package ebookstore;

import ebookstore.config.kafka.KafkaProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Service
public class MypageViewHandler {


    @Autowired
    private MypageRepository mypageRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void whenCreated_then_CREATE_1 (@Payload Created created) {
        try {

            if (!created.validate()) return;

            // view 객체 생성
            Mypage mypage = new Mypage();
            // view 객체에 이벤트의 Value 를 set 함
            mypage.setId(created.getId());
            mypage.setOrderId(created.getOrderId());
            mypage.setProductId(created.getProductId());
            mypage.setQty(created.getQty());
            mypage.setPrice(created.getPrice());
            mypage.setDestination(created.getDestination());
            mypage.setCustomerId(created.getCustomerId());
            mypage.setStatus("주문중");
            // view 레파지 토리에 save
            mypageRepository.save(mypage);
        
        }catch (Exception e){
            e.printStackTrace();
        }
    }


    @StreamListener(KafkaProcessor.INPUT)
    public void whenPaymentApproved_then_UPDATE_1(@Payload PaymentApproved paymentApproved) {
        try {
            if (!paymentApproved.validate()) return;
                // view 객체 조회
            List<Mypage> mypageList = mypageRepository.findByOrderId(paymentApproved.getOrderId());
            for(Mypage mypage : mypageList){
                // view 객체에 이벤트의 eventDirectValue 를 set 함
                mypage.setStatus("결제완료");
                // view 레파지 토리에 save
                mypageRepository.save(mypage);
            }
            List<Mypage> mypageList = mypageRepository.findByPaymentId(paymentApproved.getId());
            for(Mypage mypage : mypageList){
                // view 객체에 이벤트의 eventDirectValue 를 set 함
                mypage.setStatus("결제완료");
                // view 레파지 토리에 save
                mypageRepository.save(mypage);
            }
            
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    @StreamListener(KafkaProcessor.INPUT)
    public void whenPaymentCanceled_then_UPDATE_2(@Payload PaymentCanceled paymentCanceled) {
        try {
            if (!paymentCanceled.validate()) return;
                // view 객체 조회
            List<Mypage> mypageList = mypageRepository.findByPaymentId(paymentCanceled.getId());
            for(Mypage mypage : mypageList){
                // view 객체에 이벤트의 eventDirectValue 를 set 함
                mypage.setStatus("결제취소");
                // view 레파지 토리에 save
                mypageRepository.save(mypage);
            }
            List<Mypage> mypageList = mypageRepository.findByOrderId(paymentCanceled.getOrderId());
            for(Mypage mypage : mypageList){
                // view 객체에 이벤트의 eventDirectValue 를 set 함
                mypage.setStatus("결제취소");
                // view 레파지 토리에 save
                mypageRepository.save(mypage);
            }
            
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    @StreamListener(KafkaProcessor.INPUT)
    public void whenDeliveryApproved_then_UPDATE_3(@Payload DeliveryApproved deliveryApproved) {
        try {
            if (!deliveryApproved.validate()) return;
                // view 객체 조회
            Optional<Mypage> mypageOptional = mypageRepository.findById(deliveryApproved.getManageId());
            if( mypageOptional.isPresent()) {
                Mypage mypage = mypageOptional.get();
                // view 객체에 이벤트의 eventDirectValue 를 set 함
                    mypage.setDeliveryId(deliveryApproved.getId());
                    mypage.setStatus("배송접수");
                // view 레파지 토리에 save
                mypageRepository.save(mypage);
            }
            
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    @StreamListener(KafkaProcessor.INPUT)
    public void whenDeliveryStarted_then_UPDATE_4(@Payload DeliveryStarted deliveryStarted) {
        try {
            if (!deliveryStarted.validate()) return;
                // view 객체 조회
            Optional<Mypage> mypageOptional = mypageRepository.findById(deliveryStarted.getManageId());
            if( mypageOptional.isPresent()) {
                Mypage mypage = mypageOptional.get();
                // view 객체에 이벤트의 eventDirectValue 를 set 함
                    mypage.setStatus("배송시작");
                // view 레파지 토리에 save
                mypageRepository.save(mypage);
            }
            List<Mypage> mypageList = mypageRepository.findByDeliveryId(deliveryStarted.getId());
            for(Mypage mypage : mypageList){
                // view 객체에 이벤트의 eventDirectValue 를 set 함
                mypage.setStatus("배송시작");
                // view 레파지 토리에 save
                mypageRepository.save(mypage);
            }
            
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    @StreamListener(KafkaProcessor.INPUT)
    public void whenDeliveryCompleted_then_UPDATE_5(@Payload DeliveryCompleted deliveryCompleted) {
        try {
            if (!deliveryCompleted.validate()) return;
                // view 객체 조회
            List<Mypage> mypageList = mypageRepository.findByDeliveryId(deliveryCompleted.getId());
            for(Mypage mypage : mypageList){
                // view 객체에 이벤트의 eventDirectValue 를 set 함
                mypage.setStatus("배송완료");
                // view 레파지 토리에 save
                mypageRepository.save(mypage);
            }
            Optional<Mypage> mypageOptional = mypageRepository.findById(deliveryCompleted.getManageId());
            if( mypageOptional.isPresent()) {
                Mypage mypage = mypageOptional.get();
                // view 객체에 이벤트의 eventDirectValue 를 set 함
                    mypage.setStatus("배송완료");
                // view 레파지 토리에 save
                mypageRepository.save(mypage);
            }
            
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    @StreamListener(KafkaProcessor.INPUT)
    public void whenDeliveryCanceled_then_UPDATE_6(@Payload DeliveryCanceled deliveryCanceled) {
        try {
            if (!deliveryCanceled.validate()) return;
                // view 객체 조회
            List<Mypage> mypageList = mypageRepository.findByDeliveryId(deliveryCanceled.getId());
            for(Mypage mypage : mypageList){
                // view 객체에 이벤트의 eventDirectValue 를 set 함
                mypage.setStatus("배송취소");
                // view 레파지 토리에 save
                mypageRepository.save(mypage);
            }
            Optional<Mypage> mypageOptional = mypageRepository.findById(deliveryCanceled.getManageId());
            if( mypageOptional.isPresent()) {
                Mypage mypage = mypageOptional.get();
                // view 객체에 이벤트의 eventDirectValue 를 set 함
                    mypage.setStatus("배송취소");
                // view 레파지 토리에 save
                mypageRepository.save(mypage);
            }
            
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    @StreamListener(KafkaProcessor.INPUT)
    public void whenOrderAppoved_then_UPDATE_7(@Payload OrderAppoved orderAppoved) {
        try {
            if (!orderAppoved.validate()) return;
                // view 객체 조회
            Optional<Mypage> mypageOptional = mypageRepository.findById(orderAppoved.getId());
            if( mypageOptional.isPresent()) {
                Mypage mypage = mypageOptional.get();
                // view 객체에 이벤트의 eventDirectValue 를 set 함
                    mypage.setStatus("주문접수");
                // view 레파지 토리에 save
                mypageRepository.save(mypage);
            }
            
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    @StreamListener(KafkaProcessor.INPUT)
    public void whenOrderDeleted_then_UPDATE_8(@Payload OrderDeleted orderDeleted) {
        try {
            if (!orderDeleted.validate()) return;
                // view 객체 조회
            Optional<Mypage> mypageOptional = mypageRepository.findById(orderDeleted.getId());
            if( mypageOptional.isPresent()) {
                Mypage mypage = mypageOptional.get();
                // view 객체에 이벤트의 eventDirectValue 를 set 함
                    mypage.setStatus("주문취소");
                // view 레파지 토리에 save
                mypageRepository.save(mypage);
            }
            
        }catch (Exception e){
            e.printStackTrace();
        }
    }

}