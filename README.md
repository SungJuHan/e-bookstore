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
1. 결제가 완료되면, 주문이 접수되며 (Sync, 상품관리서비스) 주문 접수 시 배송이 시작된다. (Sync, 배송서비스)
1. 결제, 주문, 배송이 완료되면 각 결제 & 주문 & 배송 내용을 고객에게 알림 전송한다. (Async, 알림서비스)
1. 고객은 본인의 주문 내역 및 상태를 조회한다.
1. 고객은 본인의 주문을 취소할 수 있다.
1. 고객이 주문을 취소하면, 결제 & 주문 & 배송을 취소한다. (Sync, 결제/상품관리/배송서비스)
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
  ![image](https://user-images.githubusercontent.com/61722732/89151387-d21d3c80-d59b-11ea-9a34-4e2e9afe87b5.JPG)

  * 숙소의 숙소관리, 예약의 예약관리, 결제의 결제이력, 알림의 알림이력, 마이페이지는 그와 연결된 command 와 event 들에 의하여 트랜잭션이 유지되어야 하는 단위로 그들 끼리 묶어줌

### 바운디드 컨텍스트로 묶기
  ![image](https://user-images.githubusercontent.com/43338817/120574012-78a2ca00-c459-11eb-9953-88033fea3e6d.png)

* 도메인 서열 분리 
  - Core Domain:   숙소, 예약 : 없어서는 안될 핵심 서비스이며, 연견 Up-time SLA 수준을 99.999% 목표, 배포주기는 app 의 경우 1주일 1회 미만, store 의 경우 1개월 1회 미만
  - Supporting Domain:   알림, 마이페이지 : 경쟁력을 내기위한 서비스이며, SLA 수준은 연간 60% 이상 uptime 목표, 배포주기는 각 팀의 자율이나 표준 스프린트 주기가 1주일 이므로 1주일 1회 이상을 기준으로 함.
  - General Domain:   결제 : 결제서비스로 3rd Party 외부 서비스를 사용하는 것이 경쟁력이 높음 (핑크색으로 이후 전환할 예정)

### 폴리시 부착 (괄호는 수행주체, 폴리시 부착을 둘째단계에서 해놔도 상관 없음. 전체 연계가 초기에 드러남)
  ![image](https://user-images.githubusercontent.com/61722732/89151438-f547ec00-d59b-11ea-9b0a-7e54da94a0f0.JPG)

### 폴리시의 이동과 컨텍스트 매핑 (점선은 Pub/Sub, 실선은 Req/Resp)
  ![image](https://user-images.githubusercontent.com/61722732/89151466-02fd7180-d59c-11ea-8f08-d43a424b893f.JPG)

### 기능적 요구사항 검증
  ![슬라이드13](https://user-images.githubusercontent.com/61722732/89151515-1f011300-d59c-11ea-9029-565bfd0dd26e.JPG)
  ![슬라이드14](https://user-images.githubusercontent.com/61722732/89151518-21fc0380-d59c-11ea-9f22-727056c291dc.JPG)
  ![슬라이드15](https://user-images.githubusercontent.com/61722732/89151524-245e5d80-d59c-11ea-87d3-842e297ff490.JPG)
  ![슬라이드16](https://user-images.githubusercontent.com/61722732/89151531-26282100-d59c-11ea-8fdb-4f2abc1be094.JPG)

  * 호스트가 속소를 등록한다. (ok)
  * 호스트가 숙소를 삭제한다. (ok)
  * 게스트가 숙소를 선택하여 사용 예약한다. (ok)
  * 게스트가 결제한다. (ok)
  * 결제가 완료되면, 결제 & 예약 내용을 게스트에게 전달한다. (ok)
  * 예약 내역을 호스트에게 전달한다. (ok)
  * 게스트는 본인의 예약 내용 및 상태를 조회한다. (ok)
  * 게스트는 본인의 예약을 취소할 수 있다. (ok)
  * 예약이 취소되면, 결제를 취소한다. (ok)
  * 결제가 취소되면, 결제 취소 내용을 게스트에게 전달한다. (ok)

### 비기능 요구사항 검증
  ![슬라이드17](https://user-images.githubusercontent.com/61722732/89151536-29231180-d59c-11ea-9294-b8f457143d57.JPG)

* 마이크로 서비스를 넘나드는 시나리오에 대한 트랜잭션 처리
    - 숙소 예약시 결제처리:  결제가 완료되지 않은 예약은 절대 받지 않는다에 따라, ACID 트랜잭션 적용. 예약 완료시 결제처리에 대해서는 Request-Response 방식 처리
    - 예약 완료시 알림 처리:  예약에서 알림 마이크로서비스로 예약 완료 내용을 전달되는 과정에 있어서 알림 마이크로서비스가 별도의 배포주기를 가지기 때문에 Eventual Consistency 방식으로 트랜잭션 처리함.
    - 나머지 모든 inter-microservice 트랜잭션: 예약상태, 예약취소 등 모든 이벤트에 대해 알림 처리하는 등, 데이터 일관성의 시점이 크리티컬하지 않은 모든 경우가 대부분이라 판단, Eventual Consistency 를 기본으로 채택함.

## 헥사고날 아키텍처 다이어그램 도출
  ![슬라이드18](https://user-images.githubusercontent.com/61722732/89151539-2a543e80-d59c-11ea-9400-0aad70e06be4.JPG)

  * Chris Richardson, MSA Patterns 참고하여 Inbound adaptor와 Outbound adaptor를 구분함
  * 호출관계에서 PubSub 과 Req/Resp 를 구분함
  * 서브 도메인과 바운디드 컨텍스트의 분리:  각 팀의 KPI 별로 아래와 같이 관심 구현 스토리를 나눠가짐


# 구현:

분석/설계 단계에서 도출된 헥사고날 아키텍처에 따라, 각 BC별로 대변되는 마이크로 서비스들을 스프링부트로 구현하였다. 배포는 아래와 같이 수행한다.

```
# eks cluster 생성
eksctl create cluster --name team4-cluseter --version 1.15 --nodegroup-name standard-workers --node-type t3.medium --nodes 3 --nodes-min 1 --nodes-max 4

# eks cluster 설정
aws eks --region ap-northeast-2 update-kubeconfig --name team4-cluseter
kubectl config current-context

# metric server 설치
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/download/v0.3.6/components.yaml

# kafka 설치
helm install --name my-kafka --namespace kafka incubator/kafka

# istio 설치
kubectl apply -f install/kubernetes/istio-demo.yaml

# kiali service type 변경
kubectl edit service/kiali -n istio-system
(ClusterIP -> LoadBalancer)

# mybnb namespace 생성
kubectl create namespace mybnb

# mybnb istio injection 설정
kubectl label namespace mybnb istio-injection=enabled

# mybnb image build & push
cd mybnb/gateway
mvn package
docker build -t 496278789073.dkr.ecr.ap-northeast-2.amazonaws.com/mybnb-gateway:latest .
docker push 496278789073.dkr.ecr.ap-northeast-2.amazonaws.com/mybnb-gateway:latest

# mybnb deploy
cd mybnb/yaml
kubectl apply -f configmap.yaml
kubectl apply -f gateway.yaml
kubectl apply -f html.yaml
kubectl apply -f room.yaml
kubectl apply -f booking.yaml
kubectl apply -f pay.yaml
kubectl apply -f mypage.yaml
kubectl apply -f alarm.yaml
kubectl apply -f siege.yaml

# mybnb gateway service type 변경
$ kubectl edit service/gateway -n mybnb
(ClusterIP -> LoadBalancer)
```

* 현황
```
$ kubectl get ns
NAME              STATUS   AGE
default           Active   41h
istio-system      Active   41h
kafka             Active   41h
kube-node-lease   Active   41h
kube-public       Active   41h
kube-system       Active   41h
mybnb             Active   3m


$ kubectl describe ns mybnb
Name:         mybnb
Labels:       istio-injection=enabled
Annotations:  <none>
Status:       Active

No resource quota.

No LimitRange resource.


$ kubectl get all -n mybnb
NAME                           READY   STATUS    RESTARTS   AGE
pod/alarm-bc469c66b-nn7r9      2/2     Running   0          3m53s
pod/booking-6f85b67876-rhwl2   2/2     Running   0          4m6s
pod/gateway-7bd59945-g9hdq     2/2     Running   0          4m18s
pod/html-78f648d5b-zhv2b       2/2     Running   0          4m14s
pod/mypage-7587b7598b-l86jl    2/2     Running   0          3m57s
pod/pay-755d679cbf-vmp2z       2/2     Running   0          4m
pod/room-6c8cff5b96-78chb      2/2     Running   0          4m10s
pod/siege                      2/2     Running   0          3m49s

NAME              TYPE           CLUSTER-IP       EXTERNAL-IP                                                                   PORT(S)          AGE
service/alarm     ClusterIP      10.100.36.234    <none>                                                                        8080/TCP         3m53s
service/booking   ClusterIP      10.100.19.222    <none>                                                                        8080/TCP         4m6s
service/gateway   LoadBalancer   10.100.195.171   a59f2304940914b7ca3875b12e62e321-738700923.ap-northeast-2.elb.amazonaws.com   8080:31754/TCP   4m18s
service/html      ClusterIP      10.100.19.81     <none>                                                                        8080/TCP         4m14s
service/mypage    ClusterIP      10.100.134.37    <none>                                                                        8080/TCP         3m57s
service/pay       ClusterIP      10.100.210.94    <none>                                                                        8080/TCP         4m
service/room      ClusterIP      10.100.78.233    <none>                                                                        8080/TCP         4m10s

NAME                      READY   UP-TO-DATE   AVAILABLE   AGE
deployment.apps/alarm     1/1     1            1           3m53s
deployment.apps/booking   1/1     1            1           4m6s
deployment.apps/gateway   1/1     1            1           4m18s
deployment.apps/html      1/1     1            1           4m14s
deployment.apps/mypage    1/1     1            1           3m57s
deployment.apps/pay       1/1     1            1           4m
deployment.apps/room      1/1     1            1           4m10s

NAME                                 DESIRED   CURRENT   READY   AGE
replicaset.apps/alarm-bc469c66b      1         1         1       3m53s
replicaset.apps/booking-6f85b67876   1         1         1       4m6s
replicaset.apps/gateway-7bd59945     1         1         1       4m18s
replicaset.apps/html-78f648d5b       1         1         1       4m14s
replicaset.apps/mypage-7587b7598b    1         1         1       3m57s
replicaset.apps/pay-755d679cbf       1         1         1       4m
replicaset.apps/room-6c8cff5b96      1         1         1       4m10s
```

## DDD 의 적용

* 각 서비스내에 도출된 핵심 Aggregate Root 객체를 Entity 로 선언하였다: (예시는 결제 마이크로서비스).
  - 가능한 현업에서 사용하는 언어 (유비쿼터스 랭귀지)를 그대로 사용할 수 있지만, 일부 구현에 있어서 영문이 아닌 경우는 실행이 불가능한 경우가 있다 Maven pom.xml, Kafka의 topic id, FeignClient 의 서비스 id 등은 한글로 식별자를 사용하는 경우 오류가 발생하는 것을 확인하였다)
  - 최종적으로는 모두 영문을 사용하였으며, 이는 잠재적인 오류 발생 가능성을 차단하고 향후 확장되는 다양한 서비스들 간에 영향도를 최소화하기 위함이다.

```
package mybnb;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;
import java.util.List;

@Entity
@Table(name="Payment_table")
public class Payment {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private Long bookId;
    private Long roomId;
    private String name;
    private Long price;
    private String address;
    private String host;
    private String guest;
    private String usedate;
    private String status;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getBookId() {
        return bookId;
    }

    public void setBookId(Long bookId) {
        this.bookId = bookId;
    }

    public Long getRoomId() {
        return roomId;
    }

    public void setRoomId(Long roomId) {
        this.roomId = roomId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getPrice() {
        return price;
    }

    public void setPrice(Long price) {
        this.price = price;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getGuest() {
        return guest;
    }

    public void setGuest(String guest) {
        this.guest = guest;
    }

    public String getUsedate() {
        return usedate;
    }

    public void setUsedate(String usedate) {
        this.usedate = usedate;
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
package mybnb;

import org.springframework.data.repository.PagingAndSortingRepository;

public interface PaymentRepository extends PagingAndSortingRepository<Payment, Long>{

}
```
- siege 접속
```
kubectl exec -it siege -n mybnb -- /bin/bash
```

- kiali 화면 접속
http://a808fbb3bb7514eb7b08f595489d54e6-1558117695.ap-northeast-2.elb.amazonaws.com:20001/kiali/console
![슬라이드1](https://user-images.githubusercontent.com/61722732/89362526-14b05780-d709-11ea-91c5-91bcb5c6e1b1.JPG)

- (웹화면에서) 적용 후 REST API 테스트 
http://a59f2304940914b7ca3875b12e62e321-738700923.ap-northeast-2.elb.amazonaws.com:8080/html/index.html

* 숙소 등록
![슬라이드2](https://user-images.githubusercontent.com/61722732/89362530-15e18480-d709-11ea-8deb-d15ca8bafa39.JPG)

* 예약
![슬라이드3](https://user-images.githubusercontent.com/61722732/89362531-167a1b00-d709-11ea-9587-ef56421befd3.JPG)

- (siege 에서) 적용 후 REST API 테스트 
```
# 숙소 서비스의 등록처리
http POST http://room:8080/rooms name=호텔 price=1000 address=서울 host=Superman

# 예약 서비스의 예약처리
http POST http://booking:8080/bookings roomId=1 name=호텔 price=1000 address=서울 host=Superman guest=배트맨 usedate=20201010

# 예약 상태 확인
http http://booking:8080/bookings/1
```
- HTML 화면을 통해서 각 서비스 기능 수행

## 폴리글랏 퍼시스턴스

  * 각 마이크로서비스의 특성에 따라 데이터 저장소를 RDB, DocumentDB/NoSQL 등 다양하게 사용할 수 있지만, 시간적/환경적 특성상 모두 H2 메모리DB를 적용하였다.

## 폴리글랏 프로그래밍
  
  * 각 마이크로서비스의 특성에 따라 다양한 프로그래밍 언어를 사용하여 구현할 수 있지만, 시간적/환경적 특성상 Java를 이용하여 구현하였다.

## 동기식 호출 과 Fallback 처리

분석단계에서의 조건 중 하나로 예약->결제 간의 호출은 동기식 일관성을 유지하는 트랜잭션으로 처리하기로 하였다. 호출 프로토콜은 이미 앞서 Rest Repository 에 의해 노출되어있는 REST 서비스를 FeignClient 를 이용하여 호출하도록 한다. 

- 결제 서비스를 호출하기 위하여 Stub과 (FeignClient) 를 이용하여 Service 대행 인터페이스 (Proxy) 를 구현 

```
@FeignClient(name="pay", url="${api.url.payment}")
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
        // 예약시 결제까지 트랜잭션을 통합을 위해 결제 서비스 직접 호출
        {
            mybnb.external.Payment payment = new mybnb.external.Payment();
            payment.setBookId(getId());
            payment.setRoomId(getRoomId());
            payment.setGuest(getGuest());
            payment.setPrice(getPrice());
            payment.setName(getName());
            payment.setHost(getHost());
            payment.setAddress(getAddress());
            payment.setUsedate(getUsedate());
            payment.setStatus("PayApproved");

            // mappings goes here
            try {
                BookingApplication.applicationContext.getBean(mybnb.external.PaymentService.class)
                        .pay(payment);
            }catch(Exception e) {
                throw new RuntimeException("결제서비스 호출 실패입니다.");
            }
        }
    }

}
```

- 동기식 호출에서는 호출 시간에 따른 타임 커플링이 발생하며, 결제 시스템이 장애가 나면 주문도 못받는다는 것을 확인:
```
# 결제 서비스를 잠시 내려놓음 (ctrl+c)
$ kubectl delete -f pay.yaml

NAME                           READY   STATUS    RESTARTS   AGE
pod/alarm-bc469c66b-nn7r9      2/2     Running   0          14m
pod/booking-6f85b67876-rhwl2   2/2     Running   0          14m
pod/gateway-7bd59945-g9hdq     2/2     Running   0          14m
pod/html-78f648d5b-zhv2b       2/2     Running   0          14m
pod/mypage-7587b7598b-l86jl    2/2     Running   0          14m
pod/room-6c8cff5b96-78chb      2/2     Running   0          14m
pod/siege                      2/2     Running   0          14m

# 예약처리 (siege 에서)
http POST http://booking:8080/bookings roomId=1 name=호텔 price=1000 address=서울 host=Superman guest=배트맨 usedate=20201010 #Fail
http POST http://booking:8080/bookings roomId=2 name=펜션 price=1000 address=양평 host=Superman guest=홍길동 usedate=20201011 #Fail

# 예약처리 시 에러 내용
HTTP/1.1 500 Internal Server Error
content-type: application/json;charset=UTF-8
date: Wed, 05 Aug 2020 00:58:04 GMT
server: envoy
transfer-encoding: chunked
x-envoy-upstream-service-time: 188

{
    "error": "Internal Server Error",
    "message": "Could not commit JPA transaction; nested exception is javax.persistence.RollbackException: Error while committing the transaction",
    "path": "/bookings",
    "status": 500,
    "timestamp": "2020-08-05T00:58:05.047+0000"
}

# 결제서비스 재기동전에 아래의 비동기식 호출 기능 점검 테스트 수행 (siege 에서)
http DELETE http://booking:8080/bookings/1 #Success
# 결과
root@siege:/# http DELETE http://booking:8080/bookings/1
HTTP/1.1 204 No Content
date: Wed, 05 Aug 2020 00:59:03 GMT
server: envoy
x-envoy-upstream-service-time: 35

# 결제서비스 재기동
$ kubectl apply -f pay.yaml

NAME                           READY   STATUS    RESTARTS   AGE
pod/alarm-bc469c66b-nn7r9      2/2     Running   0          18m
pod/booking-6f85b67876-rhwl2   2/2     Running   0          18m
pod/gateway-7bd59945-g9hdq     2/2     Running   0          18m
pod/html-78f648d5b-zhv2b       2/2     Running   0          18m
pod/mypage-7587b7598b-l86jl    2/2     Running   0          18m
pod/pay-755d679cbf-7l7dq       2/2     Running   0          84s
pod/room-6c8cff5b96-78chb      2/2     Running   0          18m
pod/siege                      2/2     Running   0          17m


# 예약처리 (siege 에서)
http POST http://booking:8080/bookings roomId=1 name=호텔 price=1000 address=서울 host=Superman guest=배트맨 usedate=20201010 #Success
http POST http://booking:8080/bookings roomId=2 name=펜션 price=1000 address=양평 host=Superman guest=홍길동 usedate=20201011 #Success

# 처리결과
HTTP/1.1 201 Created
content-type: application/json;charset=UTF-8
date: Wed, 05 Aug 2020 01:01:54 GMT
location: http://booking:8080/bookings/3
server: envoy
transfer-encoding: chunked
x-envoy-upstream-service-time: 326

{
    "_links": {
        "booking": {
            "href": "http://booking:8080/bookings/3"
        },
        "self": {
            "href": "http://booking:8080/bookings/3"
        }
    },
    "address": "서울",
    "guest": "배트맨",
    "host": "Superman",
    "name": "호텔",
    "price": 1000,
    "roomId": 1,
    "usedate": "20201010"
}

```

- 또한 과도한 요청시에 서비스 장애가 도미노 처럼 벌어질 수 있다. (서킷브레이커, 폴백 처리는 운영단계에서 설명한다.)


## 비동기식 호출 / 시간적 디커플링 / 장애격리 / 최종 (Eventual) 일관성 테스트

결제가 이루어진 후에 알림 처리는 동기식이 아니라 비 동기식으로 처리하여 알림 시스템의 처리를 위하여 예약이 블로킹 되지 않아도록 처리한다.
 
- 이를 위하여 예약관리, 결제관리에 기록을 남긴 후에 곧바로 완료되었다는 도메인 이벤트를 카프카로 송출한다(Publish)
```
@Entity
@Table(name="Payment_table")
public class Payment {

   ...

    @PostPersist
    public void onPostPersist(){
        PayApproved payApproved = new PayApproved();
        BeanUtils.copyProperties(this, payApproved);
        payApproved.setStatus(getStatus());
        payApproved.publishAfterCommit();
    }

}
```

- 알림 서비스에서는 예약완료, 결제승인 이벤트에 대해서 이를 수신하여 자신의 정책을 처리하도록 PolicyHandler 를 구현한다:
```
@Service
public class PolicyHandler{

    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverPayApproved_Notify(@Payload PayApproved payApproved){
        if(payApproved.isMe()){
            System.out.println("##### listener Notify : " + payApproved.toJson());
        }
    }
    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverPayCanceled_Notify(@Payload PayCanceled payCanceled){
        if(payCanceled.isMe()){
            System.out.println("##### listener Notify : " + payCanceled.toJson());
        }
    }
```

- 실제 구현을 하자면, 카톡 등으로 알림을 처리합니다.:
```
@Service
public class PolicyHandler{

    @Autowired
    private AlarmRepository alarmRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverPayApproved_Notify(@Payload PayApproved payApproved){
        if(payApproved.isMe()){
            addNotificationHistory(payApproved.getGuest(), "PayApproved");
            addNotificationHistory(payApproved.getHost(), "PayApproved");
        }
    }
 ```

알림 시스템은 예약/결제와 완전히 분리되어있으며, 이벤트 수신에 따라 처리되기 때문에, 알림 시스템이 유지보수로 인해 잠시 내려간 상태라도 예약을 받는데 문제가 없다:
```
# 알림 서비스를 잠시 내려놓음
kubectl delete -f alarm.yaml

# 예약처리 (siege 에서)
http POST http://booking:8080/bookings roomId=1 name=호텔 price=1000 address=서울 host=Superman guest=배트맨 usedate=20201010 #Success
http POST http://booking:8080/bookings roomId=2 name=펜션 price=1000 address=양평 host=Superman guest=홍길동 usedate=20201011 #Success

# 알림이력 확인 (siege 에서)
http http://alarm:8080/alarms # 알림이력조회 불가

# 알림 서비스 기동
kubectl apply -f alarm.yaml

# 알림이력 확인 (siege 에서)
http http://alarm:8080/alarms # 알림이력조회
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
$ siege -v -c100 -t60S -r10 --content-type "application/json" 'http://booking:8080/bookings POST {"roomId":1, "name":"호텔", "price":1000, "address":"서울", "host":"Superman", "guest":"배트맨", "usedate":"20201230"}'

HTTP/1.1 201     2.19 secs:     321 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 201     3.91 secs:     321 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 201     2.22 secs:     321 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 201     2.30 secs:     321 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 201     2.23 secs:     321 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 201     2.06 secs:     321 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 201     0.11 secs:     321 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 201     2.02 secs:     321 bytes ==> POST http://booking:8080/bookings

```
* 서킷 브레이킹을 위한 DestinationRule 적용
```
cd mybnb/yaml
kubectl apply -f dr-pay.yaml

HTTP/1.1 500     0.28 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     1.35 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     0.28 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     2.29 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     2.41 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     2.15 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     0.24 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     0.41 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     0.21 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     0.33 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     0.43 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     0.34 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     0.32 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     0.33 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     0.36 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     0.33 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     0.34 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     0.43 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     0.46 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     0.38 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     2.33 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     0.39 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     0.49 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     2.21 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     2.32 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     0.42 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     0.38 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     0.28 secs:     250 bytes ==> POST http://booking:8080/bookings

Transactions:                   1986 hits
Availability:                  63.88 %
Elapsed time:                  47.75 secs
Data transferred:               0.88 MB
Response time:                  2.39 secs
Transaction rate:              41.59 trans/sec
Throughput:                     0.02 MB/sec
Concurrency:                   99.57
Successful transactions:        1986
Failed transactions:            1123
Longest transaction:            7.53
Shortest transaction:           0.05
```

* DestinationRule 적용되어 서킷 브레이킹 동작 확인 (kiali 화면)
![슬라이드4](https://user-images.githubusercontent.com/61722732/89362532-167a1b00-d709-11ea-8981-07bf788080b5.JPG)


* 다시 부하 발생하여 DestinationRule 적용 제거하여 정상 처리 확인
```
cd mybnb/yaml
kubectl delete -f dr-pay.yaml
```

### 방식2) 서킷 브레이킹 프레임워크의 선택: Spring FeignClient + Hystrix 옵션을 사용하여 구현함

시나리오는 예약-->결제 시의 연결을 RESTful Request/Response 로 연동하여 구현이 되어있고, 결제 요청이 과도할 경우 CB 를 통하여 장애격리.

* Hystrix 를 설정:  요청처리 쓰레드에서 처리시간이 610 밀리가 넘어서기 시작하여 어느정도 유지되면 CB 회로가 닫히도록 (요청을 빠르게 실패처리, 차단) 설정
- kubectl apply -f booking_cb.yaml 실행
```
# application.yml

feign:
  hystrix:
    enabled: true

hystrix:
  command:
    default:
      execution.isolation.thread.timeoutInMilliseconds: 610

```

* 피호출 서비스(결제) 의 임의 부하 처리 - 400 밀리에서 증감 220 밀리 정도 왔다갔다 하게
- kubectl apply -f pay_cb.yaml 
```
# Payment.java (Entity)

    @PrePersist
    public void onPrePersist(){  //결제이력을 저장한 후 적당한 시간 끌기

        ...
        
        try {
            Thread.currentThread().sleep((long) (400 + Math.random() * 220));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
```

* 부하테스터 siege 툴을 통한 서킷 브레이커 동작 확인:
- 동시사용자 100명
- 60초 동안 실시

```
$ siege -v -c100 -t60S -r10 --content-type "application/json" 'http://booking:8080/bookings POST {"roomId":1, "name":"호텔", "price":1000, "address":"서울", "host":"Superman", "guest":"배트맨", "usedate":"20201230"}'

** SIEGE 4.0.4
** Preparing 100 concurrent users for battle.
The server is now under siege...

HTTP/1.1 201     4.75 secs:     321 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 201     4.65 secs:     321 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 201     4.80 secs:     321 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 201     0.49 secs:     321 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 201     4.40 secs:     321 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 201     0.48 secs:     321 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 201     4.51 secs:     321 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 201     4.29 secs:     321 bytes ==> POST http://booking:8080/bookings

HTTP/1.1 500     4.46 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     3.84 secs:     250 bytes ==> POST http://booking:8080/bookings

HTTP/1.1 201     4.15 secs:     321 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 201     4.32 secs:     321 bytes ==> POST http://booking:8080/bookings

HTTP/1.1 500     4.43 secs:     250 bytes ==> POST http://booking:8080/bookings

HTTP/1.1 201     4.31 secs:     321 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 201     4.32 secs:     321 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 201     4.29 secs:     321 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 201     4.38 secs:     321 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 201     4.22 secs:     321 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 201     4.43 secs:     321 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 201     4.31 secs:     321 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 201     4.17 secs:     321 bytes ==> POST http://booking:8080/bookings

HTTP/1.1 500     4.45 secs:     250 bytes ==> POST http://booking:8080/bookings

HTTP/1.1 201     4.18 secs:     321 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 201     4.18 secs:     321 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 201     4.10 secs:     321 bytes ==> POST http://booking:8080/bookings

HTTP/1.1 500     3.54 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     3.59 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     3.48 secs:     250 bytes ==> POST http://booking:8080/bookings

HTTP/1.1 201     4.14 secs:     321 bytes ==> POST http://booking:8080/bookings

HTTP/1.1 500     3.48 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     3.74 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     3.47 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     4.27 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     8.48 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 201     3.98 secs:     321 bytes ==> POST http://booking:8080/bookings

HTTP/1.1 500     3.13 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     3.21 secs:     250 bytes ==> POST http://booking:8080/bookings

HTTP/1.1 201     0.56 secs:     321 bytes ==> POST http://booking:8080/bookings

HTTP/1.1 500     3.12 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     3.14 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     3.04 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     3.16 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     2.89 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     3.09 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     3.19 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     2.77 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     4.15 secs:     250 bytes ==> POST http://booking:8080/bookings

HTTP/1.1 201     3.66 secs:     321 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 201     0.65 secs:     321 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 201     4.33 secs:     321 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 201     1.74 secs:     323 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 201     0.53 secs:     323 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 201     1.87 secs:     323 bytes ==> POST http://booking:8080/bookings

HTTP/1.1 500     1.16 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     1.16 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     2.85 secs:     250 bytes ==> POST http://booking:8080/bookings

HTTP/1.1 201     1.28 secs:     323 bytes ==> POST http://booking:8080/bookings

HTTP/1.1 500     1.23 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     1.22 secs:     250 bytes ==> POST http://booking:8080/bookings

HTTP/1.1 201     1.86 secs:     323 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 201     1.87 secs:     323 bytes ==> POST http://booking:8080/bookings

HTTP/1.1 500     1.21 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     1.22 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     1.24 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     1.17 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     1.23 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     1.12 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     1.08 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     1.16 secs:     250 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 500     1.16 secs:     250 bytes ==> POST http://booking:8080/bookings

Lifting the server siege...siege aborted due to excessive socket failure; you
can change the failure threshold in $HOME/.siegerc

Transactions:                    796 hits
Availability:                  42.98 %
Elapsed time:                  59.06 secs
Data transferred:               0.50 MB
Response time:                  7.32 secs
Transaction rate:              13.48 trans/sec
Throughput:                     0.01 MB/sec
Concurrency:                   98.61
Successful transactions:         796
Failed transactions:            1056
Longest transaction:           10.77
Shortest transaction:           0.08
```
- 운영시스템은 죽지 않고 지속적으로 CB 에 의하여 적절히 회로가 열림과 닫힘이 벌어지면서 자원을 보호하고 있음을 보여줌. 하지만, 42.985% 가 성공하였고, 67%가 실패했다는 것은 고객 사용성에 있어 좋지 않기 때문에 동적 Scale out (replica의 자동적 추가,HPA) 을 통하여 시스템을 확장 해주는 후속처리가 필요.

- Availability 가 높아진 것을 확인 (siege)

### 오토스케일 아웃
앞서 CB 는 시스템을 안정되게 운영할 수 있게 해줬지만 사용자의 요청을 100% 받아들여주지 못했기 때문에 이에 대한 보완책으로 자동화된 확장 기능을 적용하고자 한다. 

* (istio injection 적용한 경우) istio injection 적용 해제
```
kubectl label namespace mybnb istio-injection=disabled --overwrite

kubectl apply -f booking.yaml
kubectl apply -f pay.yaml
```

* (Spring FeignClient + Hystrix 적용한 경우) 위에서 설정된 CB는 제거해야함.
```
kubectl apply -f booking.yaml
kubectl apply -f pay.yaml
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

- 결제서비스에 대한 replica 를 동적으로 늘려주도록 HPA 를 설정한다. 설정은 CPU 사용량이 15프로를 넘어서면 replica 를 3개까지 늘려준다:
```
kubectl autoscale deploy pay -n mybnb --min=1 --max=3 --cpu-percent=15

# 적용 내용
NAME                           READY   STATUS    RESTARTS   AGE
pod/alarm-bc469c66b-nn7r9      2/2     Running   0          25m
pod/booking-6f85b67876-rhwl2   2/2     Running   0          25m
pod/gateway-7bd59945-g9hdq     2/2     Running   0          25m
pod/html-78f648d5b-zhv2b       2/2     Running   0          25m
pod/mypage-7587b7598b-l86jl    2/2     Running   0          25m
pod/pay-755d679cbf-7l7dq       2/2     Running   0          8m58s
pod/room-6c8cff5b96-78chb      2/2     Running   0          25m
pod/siege                      2/2     Running   0          25m

NAME              TYPE           CLUSTER-IP       EXTERNAL-IP                                                                   PORT(S)          AGE
service/alarm     ClusterIP      10.100.36.234    <none>                                                                        8080/TCP         25m
service/booking   ClusterIP      10.100.19.222    <none>                                                                        8080/TCP         25m
service/gateway   LoadBalancer   10.100.195.171   a59f2304940914b7ca3875b12e62e321-738700923.ap-northeast-2.elb.amazonaws.com   8080:31754/TCP   25m
service/html      ClusterIP      10.100.19.81     <none>                                                                        8080/TCP         25m
service/mypage    ClusterIP      10.100.134.37    <none>                                                                        8080/TCP         25m
service/pay       ClusterIP      10.100.97.43     <none>                                                                        8080/TCP         8m58s
service/room      ClusterIP      10.100.78.233    <none>                                                                        8080/TCP         25m

NAME                      READY   UP-TO-DATE   AVAILABLE   AGE
deployment.apps/alarm     1/1     1            1           25m
deployment.apps/booking   1/1     1            1           25m
deployment.apps/gateway   1/1     1            1           25m
deployment.apps/html      1/1     1            1           25m
deployment.apps/mypage    1/1     1            1           25m
deployment.apps/pay       1/1     1            1           8m58s
deployment.apps/room      1/1     1            1           25m

NAME                                 DESIRED   CURRENT   READY   AGE
replicaset.apps/alarm-bc469c66b      1         1         1       25m
replicaset.apps/booking-6f85b67876   1         1         1       25m
replicaset.apps/gateway-7bd59945     1         1         1       25m
replicaset.apps/html-78f648d5b       1         1         1       25m
replicaset.apps/mypage-7587b7598b    1         1         1       25m
replicaset.apps/pay-755d679cbf       1         1         1       8m58s
replicaset.apps/room-6c8cff5b96      1         1         1       25m

NAME                                      REFERENCE        TARGETS         MINPODS   MAXPODS   REPLICAS   AGE
horizontalpodautoscaler.autoscaling/pay   Deployment/pay   <unknown>/15%   1         3         0          7s
```

- CB 에서 했던 방식대로 워크로드를 3분 동안 걸어준다.
```
$ siege -v -c100 -t180S -r10 --content-type "application/json" 'http://booking:8080/bookings POST {"roomId":1, "name":"호텔", "price":1000, "address":"서울", "host":"Superman", "guest":"배트맨", "usedate":"20201230"}'

```
- 오토스케일이 어떻게 되고 있는지 모니터링을 걸어둔다:
```
kubectl get deploy pay -n mybnb -w 
```
- 어느정도 시간이 흐른 후 (약 30초) 스케일 아웃이 벌어지는 것을 확인할 수 있다:
```
NAME   READY   UP-TO-DATE   AVAILABLE   AGE
pay    1/1     1            1           4m21s
pay    1/2     1            1           4m28s
pay    1/2     1            1           4m28s
pay    1/2     1            1           4m28s
pay    1/2     2            1           4m28s
pay    1/3     2            1           4m43s
pay    1/3     2            1           4m43s
pay    1/3     2            1           4m43s
pay    1/3     3            1           4m43s
pay    2/3     3            2           5m53s
pay    3/3     3            3           5m59s
:
```
- siege 의 로그를 보아도 전체적인 성공률이 높아진 것을 확인 할 수 있다. 
```
Lifting the server siege...
Transactions:                  26446 hits
Availability:                 100.00 %
Elapsed time:                 179.76 secs
Data transferred:               8.73 MB
Response time:                  0.68 secs
Transaction rate:             147.12 trans/sec
Throughput:                     0.05 MB/sec
Concurrency:                   99.60
Successful transactions:       26446
Failed transactions:               0
Longest transaction:            5.85
Shortest transaction:           0.00
```

## 무정지 재배포

* 먼저 무정지 재배포가 100% 되는 것인지 확인하기 위해서 Autoscaler 이나 CB 설정을 제거함
(위의 시나리오에서 제거되었음)

- seige 로 배포작업 직전에 워크로드를 모니터링 함.
```
$ siege -v -c1 -t300S -r10 --content-type "application/json" 'http://booking:8080/bookings'

** SIEGE 4.0.5
** Preparing 100 concurrent users for battle.
The server is now under siege...

HTTP/1.1 201     0.68 secs:     207 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 201     0.68 secs:     207 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 201     0.70 secs:     207 bytes ==> POST http://booking:8080/bookings
HTTP/1.1 201     0.70 secs:     207 bytes ==> POST http://booking:8080/bookings
:

```

- 새버전으로의 배포 시작
```
# 컨테이너 이미지 Update (readness, liveness 미설정 상태)
- kubectl apply -f booking_na.yaml 실행

```

- seige 의 화면으로 넘어가서 Availability 가 100% 미만으로 떨어졌는지 확인
```
Transactions:                  18182 hits
Availability:                  94.67 %
Elapsed time:                  56.86 secs
Data transferred:               6.14 MB
Response time:                  0.00 secs
Transaction rate:             319.77 trans/sec
Throughput:                     0.11 MB/sec
Concurrency:                    0.93
Successful transactions:       18182
Failed transactions:            1024
Longest transaction:            0.73
Shortest transaction:           0.00

```
- 배포기간중 Availability 가 평소 100%에서 70% 대로 떨어지는 것을 확인. 원인은 쿠버네티스가 성급하게 새로 올려진 서비스를 READY 상태로 인식하여 서비스 유입을 진행한 것이기 때문. 이를 막기위해 Readiness Probe 를 설정함:
```
# deployment.yaml 의 readiness probe 의 설정:
- kubectl apply -f booking.yaml 실행

NAME                           READY   STATUS        RESTARTS   AGE
pod/alarm-bc469c66b-nn7r9      2/2     Running       1          58m
pod/booking-67d766dc78-xzrzr   1/1     Terminating   0          73s
pod/booking-6f85b67876-94nxl   1/1     Running       0          34s
pod/gateway-7bd59945-g9hdq     2/2     Running       0          58m
pod/html-78f648d5b-zhv2b       2/2     Running       0          58m
pod/pay-755d679cbf-f56nd       1/1     Running       0          3m33s
pod/pay-755d679cbf-lmtvh       1/1     Running       0          8m16s
pod/pay-755d679cbf-qjbw6       1/1     Running       0          3m48s
pod/siege                      1/1     Running       0          13m
```

- 동일한 시나리오로 재배포 한 후 Availability 확인:
```
Transactions:                  13547 hits
Availability:                 100.00 %
Elapsed time:                  41.53 secs
Data transferred:               4.57 MB
Response time:                  0.00 secs
Transaction rate:             326.20 trans/sec
Throughput:                     0.11 MB/sec
Concurrency:                    0.94
Successful transactions:       13547
Failed transactions:               0
Longest transaction:            0.70
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
  name: mybnb-config
  namespace: mybnb
data:
  api.url.payment: http://pay:8080
  alarm.prefix: Hello
```
* booking.yaml (configmap 사용)
```
apiVersion: apps/v1
kind: Deployment
metadata:
  name: booking
  namespace: mybnb
  labels:
    app: booking
spec:
  replicas: 1
  selector:
    matchLabels:
      app: booking
  template:
    metadata:
      labels:
        app: booking
    spec:
      containers:
        - name: booking
          image: 496278789073.dkr.ecr.ap-northeast-2.amazonaws.com/mybnb-booking:latest
          ports:
            - containerPort: 8080
          env:
            - name: api.url.payment
              valueFrom:
                configMapKeyRef:
                  name: mybnb-config
                  key: api.url.payment
          resources:
```
* kubectl describe pod/booking-588cb89c6b-gmw8h -n mybnb
```
Containers:
  booking:
    Container ID:   docker://0b90fe0d06629fc367fa83273abecba2724958a0b838c058553d193a86c3e0fe
    Image:          496278789073.dkr.ecr.ap-northeast-2.amazonaws.com/mybnb-booking:latest
    Image ID:       docker-pullable://496278789073.dkr.ecr.ap-northeast-2.amazonaws.com/mybnb-booking@sha256:59abe6ec02e165fda1c8e3dbf3e8bcedf7fb5edc53fcffca5f708a70969452f3
    Port:           8080/TCP
    Host Port:      0/TCP
    State:          Running
      Started:      Mon, 03 Aug 2020 16:48:56 +0900
    Ready:          True
    Restart Count:  0
    Limits:
      cpu:  500m
    Requests:
      cpu:      200m
    Liveness:   http-get http://:8080/actuator/health delay=120s timeout=2s period=5s #success=1 #failure=5
    Readiness:  http-get http://:8080/actuator/health delay=10s timeout=2s period=5s #success=1 #failure=10
    Environment:
      api.url.payment:  <set to the key 'api.url.payment' of config map 'mybnb-config'>  Optional: false
    Mounts:
      /var/run/secrets/kubernetes.io/serviceaccount from default-token-mrczz (ro)
```

# 신규 개발 조직의 추가

  ![image](https://user-images.githubusercontent.com/487999/79684133-1d6c4300-826a-11ea-94a2-602e61814ebf.png)


## 마케팅팀의 추가
    - KPI: 신규 고객의 유입률 증대와 기존 고객의 충성도 향상
    - 구현계획 마이크로 서비스: 기존 customer 마이크로 서비스를 인수하며, 고객에 음식 및 맛집 추천 서비스 등을 제공할 예정

## 이벤트 스토밍 
    ![image](https://user-images.githubusercontent.com/487999/79685356-2b729180-8273-11ea-9361-a434065f2249.png)


## 헥사고날 아키텍처 변화 

![image](https://user-images.githubusercontent.com/487999/79685243-1d704100-8272-11ea-8ef6-f4869c509996.png)

## 구현  

기존의 마이크로 서비스에 수정을 발생시키지 않도록 Inbund 요청을 REST 가 아닌 Event 를 Subscribe 하는 방식으로 구현. 기존 마이크로 서비스에 대하여 아키텍처나 기존 마이크로 서비스들의 데이터베이스 구조와 관계없이 추가됨. 

## 운영과 Retirement

Request/Response 방식으로 구현하지 않았기 때문에 서비스가 더이상 불필요해져도 Deployment 에서 제거되면 기존 마이크로 서비스에 어떤 영향도 주지 않음.

* [비교] 결제 (pay) 마이크로서비스의 경우 API 변화나 Retire 시에 app(주문) 마이크로 서비스의 변경을 초래함:

예) API 변화시
```
# Order.java (Entity)

    @PostPersist
    public void onPostPersist(){

        fooddelivery.external.결제이력 pay = new fooddelivery.external.결제이력();
        pay.setOrderId(getOrderId());
        
        Application.applicationContext.getBean(fooddelivery.external.결제이력Service.class)
                .결제(pay);

                --> 

        Application.applicationContext.getBean(fooddelivery.external.결제이력Service.class)
                .결제2(pay);

    }
```

예) Retire 시
```
# Order.java (Entity)

    @PostPersist
    public void onPostPersist(){

        /**
        fooddelivery.external.결제이력 pay = new fooddelivery.external.결제이력();
        pay.setOrderId(getOrderId());
        
        Application.applicationContext.getBean(fooddelivery.external.결제이력Service.class)
                .결제(pay);

        **/
    }
```

