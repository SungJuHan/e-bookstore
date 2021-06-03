package ebookstore;

import ebookstore.config.kafka.KafkaProcessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
public class PolicyHandler{
    @Autowired OrderManagementRepository orderManagementRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverPaymentApproved_ReceptOrder(@Payload PaymentApproved paymentApproved){

        if(!paymentApproved.validate()) return;

        System.out.println("\n\n##### listener ReceptOrder : " + paymentApproved.toJson() + "\n\n");

        // Sample Logic //
        OrderManagement orderManagement = new OrderManagement();
        orderManagementRepository.save(orderManagement);
            
    }
    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverPaymentCanceled_CancelOrder(@Payload PaymentCanceled paymentCanceled){

        if(!paymentCanceled.validate()) return;

        System.out.println("\n\n##### listener CancelOrder : " + paymentCanceled.toJson() + "\n\n");

        // Sample Logic //
        OrderManagement orderManagement = new OrderManagement();
        orderManagementRepository.save(orderManagement);
            
    }
    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverOrdered_Creation(@Payload Ordered ordered){

        if(!ordered.validate()) return;

        System.out.println("\n\n##### listener Creation : " + ordered.toJson() + "\n\n");

        // Sample Logic //
        OrderManagement orderManagement = new OrderManagement();
        orderManagementRepository.save(orderManagement);
            
    }


    @StreamListener(KafkaProcessor.INPUT)
    public void whatever(@Payload String eventString){}


}
