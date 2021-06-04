<img src="https://user-images.githubusercontent.com/43338817/120570337-d97ad400-c452-11eb-890e-75badb9ee9c9.png" alt="" data-canonical-src="https://user-images.githubusercontent.com/43338817/120570337-d97ad400-c452-11eb-890e-75badb9ee9c9.png" width="250" height="250" /> <img src="https://user-images.githubusercontent.com/43338817/120570362-e992b380-c452-11eb-991d-7484e593d340.png" alt="" data-canonical-src="https://user-images.githubusercontent.com/43338817/120570362-e992b380-c452-11eb-991d-7484e593d340.png" width="250" height="250" />

# e-bookstore : 온라인서점

- 체크포인트 : https://workflowy.com/s/assessment-check-po/T5YrzcMewfo4J6LW

# Table of contents

- [ebookstore](#---)
  - [서비스 시나리오](#서비스-시나리오)
  - [분석/설계](#분석설계)
  - [구현](#구현)
    - [DDD 의 적용](#ddd-의-적용)
    - [폴리글랏 퍼시스턴스](#폴리글랏-퍼시스턴스)
    - [폴리글랏 프로그래밍](#폴리글랏-프로그래밍)
    - [동기식 호출 과 Fallback 처리](#동기식-호출-과-Fallback-처리)
    - [비동기식 호출 과 Eventual Consistency](#비동기식-호출--시간적-디커플링--장애격리--최종-eventual-일관성-테스트)
  - [운영](#운영)
    - [CI/CD 설정](#cicd-설정)
    - [동기식 호출 / 서킷 브레이킹 / 장애격리](#동기식-호출--서킷-브레이킹--장애격리)
    - [오토스케일 아웃](#오토스케일-아웃)
    - [무정지 재배포](#무정지-재배포)
    - [ConfigMap 사용](#configmap-사용)
  - [신규 개발 조직의 추가](#신규-개발-조직의-추가)


# 서비스 시나리오

온라인 서점 서비스 따라하기


## 기능적 요구사항

1. 고객이 책을 주문한다.
1. 고객이 결제한다. (Sync, 결제서비스)
1. 결제가 완료되면, 주문이 접수되며 (Async, 상품관리서비스) 주문 접수 시 배송이 시작된다. (Async, 배송서비스)
1. 결제, 주문, 배송이 완료되면 각 결제 & 주문 & 배송 내용을 고객에게 알림 전송한다. (Async, 알림서비스)
1. 고객은 본인의 주문 내역 및 상태를 조회한다.
1. 고객은 본인의 주문을 취소할 수 있다.
1. 고객이 주문을 취소하면 결제가 취소된다. (Sync, 결제서비스)
1. 결제가 취소되면, 주문이 취소되며 (Async, 상품관리서비스) 주문 취소 시 배송이 취소된다. (Async, 배송서비스)
1. 결제, 주문, 배송이 취소되면 각 내용을 고객에게 전달한다. (Async, 알림서비스)

## 비기능적 요구사항
1. 트랜잭션
    1. 결제가 되지 않은 주문건은 아예 거래가 성립되지 않아야 한다 - Sync 호출 
1. 장애격리
    1. 통지(알림) 기능이 수행되지 않더라도 주문은 365일 24시간 받을 수 있어야 한다 - Async (event-driven), Eventual Consistency
    1. 결제시스템이 과중되면 사용자를 잠시동안 받지 않고 결제를 잠시후에 하도록 유도한다 - Circuit breaker, fallback
1. 성능
    1. 고객이 자주 주문내역관리에서 확인할 수 있는 상태를 마이페이지(프론트엔드)에서 확인할 수 있어야 한다 - CQRS
    1. 처리상태가 바뀔때마다 email, app push 등으로 알림을 줄 수 있어야 한다 - Event driven


# 분석/설계

## Event Storming 결과
* MSAEz 로 모델링한 이벤트스토밍 결과 : http://www.msaez.io/#/storming/tUri1wqpAMbJUn0GwQNaQY4l6q03/local/13ea499c09dbecbbf095cabf65a3b574

### 이벤트 도출
  ![image](https://user-images.githubusercontent.com/43338817/120571855-f2d14f80-c455-11eb-96b0-2699b47255f2.png)
  
* 기능적 요구사항 수행을 위한 이벤트 도출 및 잘못된 도메인 이벤트에 대한 삭제 작업 진행
  
### 액터, 커맨드 부착하여 읽기 좋게
  ![image](https://user-images.githubusercontent.com/43338817/120573757-13e76f80-c459-11eb-9500-2eaa54327ec5.png)


### 어그리게잇으로 묶기
  ![image](https://user-images.githubusercontent.com/43338817/120574081-98d28900-c459-11eb-8963-c63d9510b38e.png)

  * 고객주문의 주문관리, 결제의 결제관리, 주문에 대한 상품관리, 배송의 배송관리, 알림의 알림이력은 그와 연결된 command와  event들에 의해 트랜잭션이 유지되어야 하는 단위로 묶어줌.

### 바운디드 컨텍스트로 묶기
  ![image](https://user-images.githubusercontent.com/43338817/120574012-78a2ca00-c459-11eb-9953-88033fea3e6d.png)

* 도메인 서열 분리 
  - Core Domain:   주문, 상품관리, 배송 : 없어서는 안될 핵심 서비스이며, 연결 Up-time SLA 수준을 99.999% 목표, 배포주기의 경우 1주일 1회 미만
  - Supporting Domain:   알림, 마이페이지 : 경쟁력을 내기위한 서비스이며, SLA 수준은 연간 60% 이상 uptime 목표, 배포주기는 각 팀의 자율이나 표준 스프린트 주기가 1주일 이므로 1주일 1회 이상을 기준으로 함.
  - General Domain:   결제 : 결제서비스로 3rd Party 외부 서비스를 사용하는 것이 경쟁력이 높음 (핑크색으로 이후 전환할 예정)

### 폴리시 부착 (괄호는 수행주체, 폴리시 부착을 둘째단계에서 해놔도 상관 없음. 전체 연계가 초기에 드러남)
  ![image](https://user-images.githubusercontent.com/43338817/120576219-63c83580-c45d-11eb-8dee-6ac650424936.png)

### 폴리시의 이동과 컨텍스트 매핑 (점선은 Pub/Sub, 실선은 Req/Resp)
  ![image](https://user-images.githubusercontent.com/43338817/120577175-e8678380-c45e-11eb-81e4-efd2f3f888c7.png)
  
### 완성된 1차 모형
  ![image](https://user-images.githubusercontent.com/43338817/120578819-6462cb00-c461-11eb-8ef8-29a566c9feeb.png)

### 기능적 요구사항 검증
  ![image](https://user-images.githubusercontent.com/43338817/120578142-4fd20300-c460-11eb-84e4-b8847c6cecd0.png)
  
  * 고객이 책을 주문한다. (OK)
  * 고객이 결제한다. (Sync, 결제서비스) (OK)
  * 결제가 완료되면, 주문이 접수되며 (Async, 상품관리서비스) 주문 접수 시 배송이 시작된다. (Async, 배송서비스) (OK)
  
  ![image](https://user-images.githubusercontent.com/43338817/120578582-06ce7e80-c461-11eb-974c-cb830b699f98.png)
  
  * 고객은 본인의 주문을 취소할 수 있다. (OK)
  * 고객이 주문을 취소하면 결제가 취소된다. (OK)
  * 결제가 취소되면, 주문이 취소되며 (Async, 상품관리서비스) 주문 취소 시 배송이 취소된다. (Async, 배송서비스) (OK)

  ![image](https://user-images.githubusercontent.com/43338817/120578350-9e7f9d00-c460-11eb-950e-8c57e2566b51.png)  
  
  * 결제, 주문, 배송이 완료되면 각 결제 & 주문 & 배송 내용을 고객에게 알림 전송한다. (Async, 알림서비스) (OK)
  * 결제, 주문, 배송이 취소되면 각 내용을 고객에게 전달한다. (Async, 알림서비스) (OK)

### 모델 수정
  ![image](https://user-images.githubusercontent.com/43338817/120578710-37aeb380-c461-11eb-9698-01bcd4bc6037.png)
  
  * 고객은 본인의 주문 내역 및 상태를 조회한다. (View를 추가하여 요구사항 커버)

### 비기능 요구사항 검증
  ![image](https://user-images.githubusercontent.com/43338817/120579448-7bee8380-c462-11eb-80ad-d353e2648d07.png)

* 마이크로 서비스를 넘나드는 시나리오에 대한 트랜잭션 처리
    - 고객 주문시 결제처리:  결제가 완료되지 않은 주문을 절대 받지 않는다는 요건에 따라 ACID 트랜잭션 적용, 주문 완료 시 결제 처리에 대해서는 Request-Response 방식 처리
    - 처리 상태 변경 시 알림 처리:  각 서비스 내 알림 마이크로서비스로 처리 상태 내용 전달 과정에 있어서 알림 마이크로서비스가 별도의 배포주기를 가지기 때문에 Eventual Consistency 방식으로 트랜잭션 처리
    - 나머지 모든 inter-microservice 트랜잭션: 모든 이벤트에 대해 알림 처리하는 등, 데이터 일관성의 시점이 크리티컬하지 않은 모든 경우가 대부분이라 판단, Eventual Consistency 를 기본으로 채택함.


### 모델 최종 수정
  ![image](https://user-images.githubusercontent.com/43338817/120593768-e9a6a980-c47a-11eb-84e8-31408b9ffebf.png)
  
  * 주문 취소 시 배송 상황 여부 고려하여 취소가 가능하도록 하기 위하여 모델 최종 변경


# 구현:

분석/설계 단계에서 도출된 헥사고날 아키텍처에 따라, 각 BC별로 대변되는 마이크로 서비스들을 스프링부트로 구현하였다. 배포는 아래와 같이 수행한다.

```
# eks cluster 생성
eksctl create cluster --name user14-eks --version 1.17 --nodegroup-name standard-workers --node-type t3.medium --nodes 4 --nodes-min 1 --nodes-max 4

# eks cluster 설정
aws eks --region ap-southeast-2 update-kubeconfig --name user14-eks
kubectl config current-context

# metric server 설치
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/download/v0.3.6/components.yaml

# kafka 설치
helm install --name my-kafka --namespace kafka incubator/kafka

# istio 설치
kubectl apply -f install/kubernetes/istio-demo.yaml

# kiali service type 변경
kubectl edit service/kiali -n istio-system
(ClusterIP -> LoadBalancer) (vi 편집기를 이용하여 치환)

# mybs namespace 생성
kubectl create namespace mybs

# mybnb istio injection 설정
kubectl label namespace mybs istio-injection=enabled

# mybnb image build & push
cd mybs/gateway
mvn package
docker build -t 879772956301.dkr.ecr.ap-southeast-2.amazonaws.com/user14-gateway:latest .
docker push 879772956301.dkr.ecr.ap-southeast-2.amazonaws.com/user14-gateway:latest

# mybs deploy
cd mybs/yaml
kubectl apply -f configmap.yaml
kubectl apply -f gateway.yaml
kubectl apply -f order.yaml
kubectl apply -f delivery.yaml
kubectl apply -f payment.yaml
kubectl apply -f store.yaml
kubectl apply -f mypage.yaml
kubectl apply -f alarm.yaml
kubectl apply -f siege.yaml

# mybs gateway service type 변경
$ kubectl edit service/gateway -n mybs
(ClusterIP -> LoadBalancer)
```

* 현황
```
$ kubectl get ns
```
![image](https://user-images.githubusercontent.com/43338817/120736399-25974880-c527-11eb-9a07-68691726a9ef.png)

```
$ kubectl describe ns mybs
```
![image](https://user-images.githubusercontent.com/43338817/120736460-3e076300-c527-11eb-9337-0ff3b27ee564.png)

```
$ kubectl get all -n mybs
```
![image](https://user-images.githubusercontent.com/43338817/120736508-58414100-c527-11eb-92c7-7397b23f062e.png)

## DDD 의 적용

* 각 서비스내에 도출된 핵심 Aggregate Root 객체를 Entity 로 선언하였다: (예시는 결제 마이크로서비스).
  - 가능한 현업에서 사용하는 언어 (유비쿼터스 랭귀지)를 그대로 사용할 수 있다.
  - 최종적으로는 모두 영문을 사용하였으며, 이는 잠재적인 오류 발생 가능성을 차단하고 향후 확장되는 다양한 서비스들 간에 영향도를 최소화하기 위함이다.

```
package ebookstore;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;
import java.util.List;
import java.util.Date;

@Entity
@Table(name="Payment_table")
public class Payment {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private Long orderId;
    private Integer price;
    private String status;

    @PostPersist
    public void onPostPersist(){
        PaymentApproved paymentApproved = new PaymentApproved();
        BeanUtils.copyProperties(this, paymentApproved);
        paymentApproved.publishAfterCommit();
    }

    @PostUpdate
    public void onPostUpdate(){
        PaymentCanceled paymentCanceled = new PaymentCanceled();
        BeanUtils.copyProperties(this, paymentCanceled);
        paymentCanceled.publishAfterCommit();
    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }
    public Integer getPrice() {
        return price;
    }

    public void setPrice(Integer price) {
        this.price = price;
    }
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
```
- Entity Pattern 과 Repository Pattern 을 적용하여 JPA 를 통하여 다양한 데이터소스 유형 (RDB or NoSQL) 에 대한 별도의 처리가 없도록 데이터 접근 어댑터를 자동 생성하기 위하여 Spring Data REST 의 RestRepository 를 적용하였다
```
package ebookstore;

import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@RepositoryRestResource(collectionResourceRel="payments", path="payments")
public interface PaymentRepository extends PagingAndSortingRepository<Payment, Long>{


}
```
- siege 접속
```
kubectl exec -it siege -n mybs -- /bin/bash
```

- (siege 에서) 적용 후 REST API 테스트 
```
# 주문 서비스의 등록처리
http POST http://order:8080/orders customerId=1 productId=1000 qty=1 pricd=17500 destination=Seoul
```
![image](https://user-images.githubusercontent.com/43338817/120747988-fa1f5880-c53c-11eb-9090-51987ae5e01b.png)

```
# 상점 서비스의 주문관리 상태 확인
http GET http://store:8080/orderManagements/1
```
![image](https://user-images.githubusercontent.com/43338817/120748017-03a8c080-c53d-11eb-9653-6afd27129c6e.png)

- HTML 화면을 통해서 각 서비스 기능 수행

## 폴리글랏 퍼시스턴스

  * 각 마이크로서비스의 특성에 따라 데이터 저장소를 RDB, DocumentDB/NoSQL 등 다양하게 사용할 수 있지만, 시간적/환경적 특성상 모두 H2 메모리DB를 적용하였다.

## 폴리글랏 프로그래밍
  
  * 각 마이크로서비스의 특성에 따라 다양한 프로그래밍 언어를 사용하여 구현할 수 있지만, 시간적/환경적 특성상 Java를 이용하여 구현하였다.

## 동기식 호출 과 Fallback 처리

분석단계에서의 조건 중 하나로 주문->결제 간의 호출은 동기식 일관성을 유지하는 트랜잭션으로 처리하기로 하였다. 호출 프로토콜은 이미 앞서 Rest Repository 에 의해 노출되어있는 REST 서비스를 FeignClient 를 이용하여 호출하도록 한다. 

- 결제 서비스를 호출하기 위하여 Stub과 (FeignClient) 를 이용하여 Service 대행 인터페이스 (Proxy) 를 구현 

```
@FeignClient(name="payment", url="http://payment:8080")
public interface PaymentService {

    @RequestMapping(method= RequestMethod.POST, path="/payments")
    public void pay(@RequestBody Payment payment);

}
```

- 예약을 받은 직후(@PostPersist) 결제를 요청하도록 처리
```
@Entity
@Table(name="Booking_table")
public class Booking {

   ...

    @PostPersist
    public void onPostPersist(){

        {
            ebookstore.external.Payment payment = new ebookstore.external.Payment();
            payment.setOrderId(getId());
            payment.setPrice(getPrice());
            payment.setStatus("PayApproved");

            try {
                OrderApplication.applicationContext.getBean(ebookstore.external.PaymentService.class)
                    .pay(payment);
            }catch(Exception e) {
                throw new RuntimeException("결제서비스 호출 실패입니다.");
            }
        }
        
        Ordered ordered = new Ordered();
        BeanUtils.copyProperties(this, ordered);
        ordered.publishAfterCommit();
    }

}
```

- 동기식 호출에서는 호출 시간에 따른 타임 커플링이 발생하며, 결제 시스템이 장애가 나면 주문도 못받는다는 것을 확인:
```
# 결제 서비스를 잠시 내려놓음 (ctrl+c)
$ kubectl delete -f payment.yaml
```
![image](https://user-images.githubusercontent.com/43338817/120755770-61430a00-c549-11eb-8fee-e8ee9825e7bf.png)

```
# 예약처리 (siege 에서)
http POST http://order:8080/orders customerId=1 productId=1000 qty=1 pricd=17000 destination=Seoul #Fail

```

```
# 예약처리 시 에러 내용
```
![image](https://user-images.githubusercontent.com/43338817/120755545-12957000-c549-11eb-8e98-ee282f143d4b.png)

- 또한 과도한 요청시에 서비스 장애가 도미노 처럼 벌어질 수 있다. (서킷브레이커, 폴백 처리는 운영단계에서 설명한다.)


## 비동기식 호출 / 시간적 디커플링 / 장애격리 / 최종 (Eventual) 일관성 테스트

결제가 이루어진 후에 매점 내 주문 관리 처리는 동기식이 아니라 비 동기식으로 처리하여 주문관리 시스템의 처리를 위하여 주문이 블로킹 되지 않아도록 처리한다.
 
- 이를 위하여 기록을 남긴 후에 곧바로 완료되었다는 도메인 이벤트를 카프카로 송출한다(Publish)
```
@Entity
@Table(name="Payment_table")
public class Payment {

   ...

    @PostPersist
    public void onPostPersist(){
        PaymentApproved paymentApproved = new PaymentApproved();
        BeanUtils.copyProperties(this, paymentApproved);
        paymentApproved.publishAfterCommit();
    }

}
```

- 주문관리 서비스에서는 주문접수,결제승인,주문취소 이벤트에 대해서 이를 수신하여 자신의 정책을 처리하도록 PolicyHandler 를 구현한다:
```
@Service
public class PolicyHandler{
    @Autowired OrderManagementRepository orderManagementRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverPaymentApproved_ReceptOrder(@Payload PaymentApproved paymentApproved){

        if(!paymentApproved.validate()) return;

        System.out.println("\n\n##### listener ReceptOrder : " + paymentApproved.toJson() + "\n\n");

        List<OrderManagement> orderManagementList = orderManagementRepository.findByOrderId(paymentApproved.getOrderId());

        for(OrderManagement orderManagement : orderManagementList) {
            orderManagement.setStatus("PayApproved");
            orderManagementRepository.save(orderManagement);
        }
            
    }
    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverOrderCanceled_CancelOrder(@Payload OrderCanceled orderCanceled){

        if(!orderCanceled.validate()) return;

        System.out.println("\n\n##### listener CancelOrder : " + orderCanceled.toJson() + "\n\n");

        List<OrderManagement> orderManagementList = orderManagementRepository.findByOrderId(orderCanceled.getId());

        for(OrderManagement orderManagement : orderManagementList) {
            orderManagement.setStatus("OrderCanceled");
            orderManagementRepository.save(orderManagement);
        }
    }
    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverOrdered_Creation(@Payload Ordered ordered){

        if(!ordered.validate()) return;

        System.out.println("\n\n##### listener Creation : " + ordered.toJson() + "\n\n");

        OrderManagement orderManagement = new OrderManagement();
        orderManagement.setCustomerId(ordered.getCustomerId());
        orderManagement.setOrderId(ordered.getId());
        orderManagement.setProductId(ordered.getProductId());
        orderManagement.setQty(ordered.getQty());
        orderManagement.setDestination(ordered.getDestination());
        orderManagement.setStatus("Created");

        orderManagementRepository.save(orderManagement);
    }


    @StreamListener(KafkaProcessor.INPUT)
    public void whatever(@Payload String eventString){}


}
```

 시스템은 주문/결제와 완전히 분리되어있으며, 이벤트 수신에 따라 처리되기 때문에, 주문관리 시스템이 유지보수로 인해 잠시 내려간 상태라도 주문을 받는데 문제가 없다:
```
# 상점 서비스를 잠시 내려놓음
kubectl delete -f store.yaml

# 주문처리 (siege 에서)
http POST http://order:8080/orders customerId=1 productId=1000 qty=1 pricd=17000 destination=Seoul #Success
```
![image](https://user-images.githubusercontent.com/43338817/120758962-6e61f800-c54d-11eb-9c04-39c966f4d601.png)

```
# 알림이력 확인 (siege 에서)
http GET http://store:8080/orderManagements # 주문관리조회 불가
```
![image](https://user-images.githubusercontent.com/43338817/120759031-820d5e80-c54d-11eb-8ebd-a6836426d6aa.png)


```
# 상점 서비스 기동
kubectl apply -f store.yaml

# 주문관리 확인 (siege 에서)
http GET http://store:8080/orderManagements # 주문관리조회
```
![image](https://user-images.githubusercontent.com/43338817/120759153-abc68580-c54d-11eb-8d5a-bd4cbc3aabf1.png)

## Polyglot

```
# Payment - pom.xml

		<dependency>
			<groupId>com.h2database</groupId>
			<artifactId>h2</artifactId>
			<scope>runtime</scope>
		</dependency>


# Alarm - pom.xml

		<dependency>
			<groupId>org.hsqldb</groupId>
			<artifactId>hsqldb</artifactId>
			<scope>runtime</scope>
		</dependency>

```


# 운영

## CI/CD 설정

  * 각 구현체들은 github의 각각의 source repository 에 구성
  * Image repository는 ECR 사용

## 동기식 호출 / 서킷 브레이킹 / 장애격리

### 방식1) 서킷 브레이킹 프레임워크의 선택: istio-injection + DestinationRule

* istio-injection 적용 (기 적용완료)
```
kubectl label namespace mybnb istio-injection=enabled
```
* 예약, 결제 서비스 모두 아무런 변경 없음

* 부하테스터 siege 툴을 통한 서킷 브레이커 동작 확인:
- 동시사용자 100명
- 60초 동안 실시
```
$ siege -v -c100 -t60S -r10 --content-type "application/json" 'http://order:8080/orders POST {"customerId":1, "productId":"1000", "qty":1, "price":17000, "destination":"서울"}'


HTTP/1.1 201     1.88 secs:     260 bytes ==> POST http://order:8080/orders
HTTP/1.1 201     2.08 secs:     260 bytes ==> POST http://order:8080/orders
HTTP/1.1 201     1.92 secs:     260 bytes ==> POST http://order:8080/orders
HTTP/1.1 201     1.90 secs:     260 bytes ==> POST http://order:8080/orders
HTTP/1.1 201     1.89 secs:     260 bytes ==> POST http://order:8080/orders
HTTP/1.1 201     1.90 secs:     260 bytes ==> POST http://order:8080/orders
HTTP/1.1 201     1.90 secs:     260 bytes ==> POST http://order:8080/orders
HTTP/1.1 201     1.70 secs:     260 bytes ==> POST http://order:8080/orders
HTTP/1.1 201     3.42 secs:     260 bytes ==> POST http://order:8080/orders
HTTP/1.1 201     1.71 secs:     260 bytes ==> POST http://order:8080/orders
HTTP/1.1 201     1.80 secs:     260 bytes ==> POST http://order:8080/orders
HTTP/1.1 201     1.78 secs:     260 bytes ==> POST http://order:8080/orders
HTTP/1.1 201     0.10 secs:     260 bytes ==> POST http://order:8080/orders
HTTP/1.1 201     1.79 secs:     260 bytes ==> POST http://order:8080/orders
HTTP/1.1 201     0.12 secs:     260 bytes ==> POST http://order:8080/orders
HTTP/1.1 201     0.20 secs:     260 bytes ==> POST http://order:8080/orders
HTTP/1.1 201     1.59 secs:     260 bytes ==> POST http://order:8080/orders
HTTP/1.1 201     0.20 secs:     260 bytes ==> POST http://order:8080/orders
HTTP/1.1 201     1.90 secs:     260 bytes ==> POST http://order:8080/orders
HTTP/1.1 201     1.60 secs:     260 bytes ==> POST http://order:8080/orders

```
* 서킷 브레이킹을 위한 DestinationRule 적용
```
cd  ../yaml
kubectl apply -f dr-payment.yaml

$ siege -v -c100 -t60S -r10 --content-type "application/json" 'http://order:8080/orders POST {"customerId":1, "productId":"1000", "qty":1, "price":17000, "destination":"서울"}'
HTTP/1.1 500     0.38 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     0.59 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     0.27 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     0.63 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     0.68 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     0.68 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     0.71 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     0.72 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     0.73 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     0.67 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     0.52 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     0.57 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     0.80 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     0.39 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     0.81 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     0.65 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     0.27 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     0.49 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     0.46 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     0.51 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     1.06 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     0.88 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     0.67 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     0.56 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     0.58 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     0.49 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     0.96 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     0.31 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     0.46 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     0.74 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     0.77 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     0.71 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     0.78 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     0.72 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     0.84 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     0.72 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     1.03 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     0.62 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     0.62 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     0.77 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     0.63 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     0.98 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     0.94 secs:     248 bytes ==> POST http://order:8080/orders
...

Transactions:                     69 hits
Availability:                   5.79 %
Elapsed time:                   7.49 secs
Data transferred:               0.28 MB
Response time:                 10.68 secs
Transaction rate:               9.21 trans/sec
Throughput:                     0.04 MB/sec
Concurrency:                   98.42
Successful transactions:          69
Failed transactions:            1123
Longest transaction:            1.85
Shortest transaction:           0.02
```

* 다시 부하 발생하여 DestinationRule 적용 제거하여 정상 처리 확인
```
$ kubectl delete -f dr-payment.yaml

$ siege -v -c100 -t60S -r10 --content-type "application/json" 'http://order:8080/orders POST {"customerId":1, "productId":"1000", "qty":1, "price":17000, "destination":"서울"}'

HTTP/1.1 201     0.10 secs:     264 bytes ==> POST http://order:8080/orders
HTTP/1.1 201     0.68 secs:     264 bytes ==> POST http://order:8080/orders
HTTP/1.1 201     0.68 secs:     264 bytes ==> POST http://order:8080/orders
HTTP/1.1 201     0.68 secs:     264 bytes ==> POST http://order:8080/orders
HTTP/1.1 201     0.68 secs:     264 bytes ==> POST http://order:8080/orders
HTTP/1.1 201     0.67 secs:     264 bytes ==> POST http://order:8080/orders
HTTP/1.1 201     0.69 secs:     264 bytes ==> POST http://order:8080/orders
HTTP/1.1 201     0.70 secs:     264 bytes ==> POST http://order:8080/orders
HTTP/1.1 201     1.49 secs:     264 bytes ==> POST http://order:8080/orders

Lifting the server siege...
Transactions:                   7830 hits
Availability:                 100.00 %
Elapsed time:                  59.57 secs
Data transferred:               1.96 MB
Response time:                  0.75 secs
Transaction rate:             131.44 trans/sec
Throughput:                     0.03 MB/sec
Concurrency:                   99.05
Successful transactions:        7830
Failed transactions:               0
Longest transaction:            3.55
Shortest transaction:           0.01
```

### 오토스케일 아웃
앞서 CB 는 시스템을 안정되게 운영할 수 있게 해줬지만 사용자의 요청을 100% 받아들여주지 못했기 때문에 이에 대한 보완책으로 자동화된 확장 기능을 적용하고자 한다. 

* (istio injection 적용한 경우) istio injection 적용 해제
```
kubectl label namespace mybs istio-injection=disabled --overwrite

kubectl apply -f order.yaml
kubectl apply -f payment.yaml
```

- 결제서비스 배포시 resource 설정 적용되어 있음
```
    spec:
      containers:
          ...
          resources:
            limits:
              cpu: 500m
            requests:
              cpu: 200m
```

- 결제서비스에 대한 replica 를 동적으로 늘려주도록 HPA 를 설정한다. 설정은 CPU 사용량이 3프로를 넘어서면 replica 를 3개까지 늘려준다:
```
kubectl autoscale deploy payment -n mybs --min=1 --max=3 --cpu-percent=3

# 적용 내용
NAME                            READY   STATUS             RESTARTS   AGE
pod/alarm-7fcb6c7cbf-6rkh9      2/2     Running            0          78m
pod/delivery-6978b6c54f-x6g7f   1/2     CrashLoopBackOff   18         77m
pod/gateway-64d747bbf5-7lxvw    2/2     Running            0          77m
pod/mypage-8596db5cb6-l4266     2/2     Running            0          77m
pod/order-65c9989544-jvkpr      1/1     Running            0          92s
pod/payment-75d746f8f5-rcbpd    2/2     Running            0          17m
pod/siege                       1/1     Running            0          23h
pod/store-64df585774-pj4wk      2/2     Running            0          12m

NAME               TYPE           CLUSTER-IP       EXTERNAL-IP                                                                   PORT(S)          AGE
service/alarm      ClusterIP      10.100.40.168    <none>                                                                        8080/TCP         78m
service/delivery   ClusterIP      10.100.113.127   <none>                                                                        8080/TCP         77m
service/gateway    LoadBalancer   10.100.197.65    a21e1b8df84fe450495addbd7f61900d-492082344.ap-southeast-2.elb.amazonaws.com   8080:32024/TCP   77m
service/mypage     ClusterIP      10.100.96.130    <none>                                                                        8080/TCP         77m
service/order      ClusterIP      10.100.198.254   <none>                                                                        8080/TCP         91s
service/payment    ClusterIP      10.100.187.240   <none>                                                                        8080/TCP         17m
service/store      ClusterIP      10.100.30.175    <none>                                                                        8080/TCP         12m

NAME                       READY   UP-TO-DATE   AVAILABLE   AGE
deployment.apps/alarm      1/1     1            1           78m
deployment.apps/delivery   0/1     1            0           77m
deployment.apps/gateway    1/1     1            1           77m
deployment.apps/mypage     1/1     1            1           77m
deployment.apps/order      1/1     1            1           92s
deployment.apps/payment    1/1     1            1           17m
deployment.apps/store      1/1     1            1           12m

NAME                                  DESIRED   CURRENT   READY   AGE
replicaset.apps/alarm-7fcb6c7cbf      1         1         1       78m
replicaset.apps/delivery-6978b6c54f   1         1         0       77m
replicaset.apps/gateway-64d747bbf5    1         1         1       77m
replicaset.apps/mypage-8596db5cb6     1         1         1       77m
replicaset.apps/order-65c9989544      1         1         1       92s
replicaset.apps/payment-75d746f8f5    1         1         1       17m
replicaset.apps/store-64df585774      1         1         1       12m

NAME                                          REFERENCE            TARGETS        MINPODS   MAXPODS   REPLICAS   AGE
horizontalpodautoscaler.autoscaling/payment   Deployment/payment   <unknown>/3%   1         3         0          8s
```

- CB 에서 했던 방식대로 워크로드를 3분 동안 걸어준다. (CB보다 오토스케일이 나타나는 모습을 빠르게 보여주도록 -c255로 설정하여 부하를 가중한다.)
```
$ siege -v -c255 -t180S -r10 --content-type "application/json" 'http://order:8080/orders POST {"customerId":1, "productId":"1000", "qty":1, "price":17000, "destination":"서울"}'

```
- 오토스케일이 어떻게 되고 있는지 모니터링을 걸어둔다:
```
kubectl get deploy payment -n mybs -w
```
- 어느정도 시간이 흐른 후 (약 30초) 스케일 아웃이 벌어지는 것을 확인할 수 있다:

![image](https://user-images.githubusercontent.com/43338817/120760852-8c305c80-c54f-11eb-81bf-7c8314a6a486.png)

- siege 의 로그를 보아도 전체적인 성공률이 높아진 것을 확인 할 수 있다. 
```
Lifting the server siege...
Transactions:                  29713 hits
Availability:                 100.00 %
Elapsed time:                 179.91 secs
Data transferred:               7.46 MB
Response time:                  1.54 secs
Transaction rate:             165.15 trans/sec
Throughput:                     0.04 MB/sec
Concurrency:                  253.63
Successful transactions:       29713
Failed transactions:               0
Longest transaction:           19.22
Shortest transaction:           0.06
```

## 무정지 재배포

* 먼저 무정지 재배포가 100% 되는 것인지 확인하기 위해서 Autoscaler 이나 CB 설정을 제거함
(위의 시나리오에서 제거되었음)

- seige 로 배포작업 직전에 워크로드를 모니터링 함.
```
$  siege -v -c1 -t300S -r10 --content-type "application/json" 'http://order:8080/orders'

** SIEGE 4.0.5
** Preparing 100 concurrent users for battle.
The server is now under siege...

HTTP/1.1 200     0.00 secs:     344 bytes ==> GET  /orders
HTTP/1.1 200     0.00 secs:     344 bytes ==> GET  /orders
HTTP/1.1 200     0.01 secs:     344 bytes ==> GET  /orders
HTTP/1.1 200     0.00 secs:     344 bytes ==> GET  /orders
HTTP/1.1 200     0.00 secs:     344 bytes ==> GET  /orders
HTTP/1.1 200     0.01 secs:     344 bytes ==> GET  /orders
HTTP/1.1 200     0.00 secs:     344 bytes ==> GET  /orders
HTTP/1.1 200     0.00 secs:     344 bytes ==> GET  /orders
HTTP/1.1 200     0.00 secs:     344 bytes ==> GET  /orders
HTTP/1.1 200     0.00 secs:     344 bytes ==> GET  /orders
HTTP/1.1 200     0.01 secs:     344 bytes ==> GET  /orders
HTTP/1.1 200     0.00 secs:     344 bytes ==> GET  /orders
HTTP/1.1 200     0.00 secs:     344 bytes ==> GET  /orders
:

```

- 새버전으로의 배포 시작
```
# 컨테이너 이미지 Update (readness, liveness 미설정 상태)
- kubectl apply -f order_na.yaml

```

- seige 의 화면으로 넘어가서 Availability 가 100% 미만으로 떨어졌는지 확인
```
Transactions:                   3590 hits
Availability:                  77.81 %
Elapsed time:                  28.35 secs
Data transferred:              23.60 MB
Response time:                  0.01 secs
Transaction rate:             126.63 trans/sec
Throughput:                     0.83 MB/sec
Concurrency:                    0.95
Successful transactions:        3590
Failed transactions:            1024
Longest transaction:            0.55
Shortest transaction:           0.00

```
- 배포기간중 Availability 가 평소 100%에서 70% 대로 떨어지는 것을 확인. 원인은 쿠버네티스가 성급하게 새로 올려진 서비스를 READY 상태로 인식하여 서비스 유입을 진행한 것이기 때문. 이를 막기위해 Readiness Probe 를 설정함:
```
# deployment.yaml 의 readiness probe 의 설정:
- kubectl apply -f order.yaml 실행

root@labs--1546259050:/home/project/e-bookstore/yaml# kubectl get pod -n mybs
NAME                        READY   STATUS             RESTARTS   AGE
alarm-7fcb6c7cbf-6rkh9      2/2     Running            0          86m
delivery-6978b6c54f-x6g7f   1/2     CrashLoopBackOff   20         86m
gateway-64d747bbf5-7lxvw    2/2     Running            0          86m
mypage-8596db5cb6-l4266     2/2     Running            0          86m
order-65c9989544-jm9gl      1/1     Running            0          48s
payment-75d746f8f5-rcbpd    2/2     Running            0          26m
payment-75d746f8f5-rlsdm    1/1     Running            0          7m11s
payment-75d746f8f5-zjtpb    1/1     Running            0          8m43s
siege                       1/1     Running            0          23h
store-64df585774-pj4wk      2/2     Running            0          21m
```

- 동일한 시나리오로 재배포 한 후 Availability 확인:
```
Lifting the server siege...
Transactions:                  93648 hits
Availability:                 100.00 %
Elapsed time:                 299.45 secs
Data transferred:              30.72 MB
Response time:                  0.00 secs
Transaction rate:             312.73 trans/sec
Throughput:                     0.10 MB/sec
Concurrency:                    0.97
Successful transactions:       93648
Failed transactions:               0
Longest transaction:            0.52
Shortest transaction:           0.00

```

배포기간 동안 Availability 가 변화없기 때문에 무정지 재배포가 성공한 것으로 확인됨.


## ConfigMap 사용

시스템별로 또는 운영중에 동적으로 변경 가능성이 있는 설정들을 ConfigMap을 사용하여 관리합니다.

* configmap.yaml
```
apiVersion: v1
kind: ConfigMap
metadata:
  name: mybs-config
  namespace: mybs
data:
  api.url.payment: http://payment:8080
  alarm.prefix: Hello
```
* order.yaml (configmap 사용)
```
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order
  namespace: mybs
  labels:
    app: order
spec:
  replicas: 1
  selector:
    matchLabels:
      app: order
  template:
    metadata:
      labels:
        app: order
    spec:
      containers:
        - name: order
          image: 879772956301.dkr.ecr.ap-southeast-2.amazonaws.com/user14-order:latest
          imagePullPolicy: Always
          ports:
            - containerPort: 8080
          env:
            - name: api.url.payment
              valueFrom:
                configMapKeyRef:
                  name: mybs-config
                  key: api.url.payment
          readinessProbe:
            httpGet:
              path: '/actuator/health'
              port: 8080
            initialDelaySeconds: 10
            timeoutSeconds: 2
            periodSeconds: 5
            failureThreshold: 10
          livenessProbe:
            httpGet:
              path: '/actuator/health'
              port: 8080
            initialDelaySeconds: 120
            timeoutSeconds: 2
            periodSeconds: 5
            failureThreshold: 5
```
* kubectl describe pod/order-65c9989544-jm9gl -n mybs
```
Containers:
  order:
    Container ID:   docker://a2a234e1e3db8825c93037c8aa1c70c9130d20770ea3d0d641e4118185c0584e
    Image:          879772956301.dkr.ecr.ap-southeast-2.amazonaws.com/user14-order:latest
    Image ID:       docker-pullable://879772956301.dkr.ecr.ap-southeast-2.amazonaws.com/user14-order@sha256:307387a203d7bd44fd9484d5676206356d0a70b4af899632fc8558de8c65e3e4
    Port:           8080/TCP
    Host Port:      0/TCP
    State:          Running
      Started:      Fri, 04 Jun 2021 07:17:42 +0000
    Ready:          True
    Restart Count:  0
    Liveness:       http-get http://:8080/actuator/health delay=120s timeout=2s period=5s #success=1 #failure=5
    Readiness:      http-get http://:8080/actuator/health delay=10s timeout=2s period=5s #success=1 #failure=10
    Environment:
      api.url.payment:  <set to the key 'api.url.payment' of config map 'mybs-config'>  Optional: false
    Mounts:
      /var/run/secrets/kubernetes.io/serviceaccount from default-token-ppdmr (ro)
```

