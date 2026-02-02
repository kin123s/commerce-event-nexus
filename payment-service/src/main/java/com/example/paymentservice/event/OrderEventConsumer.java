package com.example.paymentservice.event;

import com.example.paymentservice.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {
    
    private final PaymentService paymentService;
    
    @KafkaListener(
        topics = "${kafka.topic.order-events}",
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumeOrderEvent(OrderEvent event) {
        log.info("Received order event from Kafka: {}", event);
        
        try {
            if ("ORDER_CREATED".equals(event.getEventType())) {
                paymentService.processPayment(event);
                log.info("Payment processed successfully for order: {}", event.getOrderNumber());
            }
        } catch (Exception e) {
            log.error("Failed to process payment for order: {}, error: {}", 
                event.getOrderNumber(), e.getMessage(), e);
        }
    }
}
