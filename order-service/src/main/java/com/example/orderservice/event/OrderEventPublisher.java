package com.example.orderservice.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventPublisher {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @Value("${kafka.topic.order-events}")
    private String orderEventsTopic;
    
    public void publishOrderEvent(OrderEvent event) {
        log.info("Publishing order event to Kafka: {}", event);
        
        CompletableFuture<SendResult<String, Object>> future = 
            kafkaTemplate.send(orderEventsTopic, event.getOrderNumber(), event);
        
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Order event sent successfully: orderNumber={}, offset={}", 
                    event.getOrderNumber(), 
                    result.getRecordMetadata().offset());
            } else {
                log.error("Failed to send order event: orderNumber={}, error={}", 
                    event.getOrderNumber(), 
                    ex.getMessage());
            }
        });
    }
}
