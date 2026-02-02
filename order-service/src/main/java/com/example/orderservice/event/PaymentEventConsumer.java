package com.example.orderservice.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.orderservice.entity.Order;
import com.example.orderservice.entity.OutboxEvent;
import com.example.orderservice.repository.OrderRepository;
import com.example.orderservice.repository.OutboxEventRepository;

import java.time.LocalDateTime;

/**
 * Saga Pattern - Choreography 방식
 * Payment 실패 이벤트를 수신하여 보상 트랜잭션(주문 취소) 처리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {
    
    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    
    /**
     * 결제 실패 이벤트 수신 - 보상 트랜잭션 실행
     */
    @KafkaListener(topics = "payment-events", groupId = "order-service-group")
    @Transactional
    public void handlePaymentEvent(String message) {
        try {
            log.info("Received payment event: {}", message);
            
            PaymentEventDTO paymentEvent = objectMapper.readValue(message, PaymentEventDTO.class);
            
            if ("PAYMENT_FAILED".equals(paymentEvent.getEventType())) {
                // 보상 트랜잭션: 주문 취소
                compensateOrder(paymentEvent.getOrderNumber(), paymentEvent.getFailureReason());
            } else if ("PAYMENT_COMPLETED".equals(paymentEvent.getEventType())) {
                // 주문 완료 처리
                completeOrder(paymentEvent.getOrderNumber());
            }
            
        } catch (Exception e) {
            log.error("Failed to process payment event", e);
            // 실제로는 DLQ로 전송하거나 재시도 로직 추가
        }
    }
    
    /**
     * 보상 트랜잭션: 주문 취소
     */
    private void compensateOrder(String orderNumber, String reason) {
        log.warn("Compensating order due to payment failure: orderNumber={}, reason={}", 
            orderNumber, reason);
        
        Order order = orderRepository.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new RuntimeException("Order not found: " + orderNumber));
        
        // 주문 상태를 CANCELLED로 변경
        order.setStatus(Order.OrderStatus.CANCELLED);
        orderRepository.save(order);
        
        // 보상 트랜잭션 이벤트를 Outbox에 저장
        try {
            OrderEvent compensationEvent = OrderEvent.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .productName(order.getProductName())
                .quantity(order.getQuantity())
                .price(order.getPrice())
                .totalAmount(order.getTotalAmount())
                .customerName(order.getCustomerName())
                .customerEmail(order.getCustomerEmail())
                .status(Order.OrderStatus.CANCELLED.name())
                .eventTime(LocalDateTime.now())
                .eventType("ORDER_CANCELLED")
                .build();
            
            OutboxEvent outboxEvent = OutboxEvent.builder()
                .aggregateId(order.getOrderNumber() + "-compensation")
                .aggregateType("ORDER")
                .eventType("ORDER_CANCELLED")
                .payload(objectMapper.writeValueAsString(compensationEvent))
                .build();
            
            outboxEventRepository.save(outboxEvent);
            
            log.info("Order compensated successfully: orderNumber={}", orderNumber);
            
        } catch (Exception e) {
            log.error("Failed to create compensation event", e);
            throw new RuntimeException("Compensation failed", e);
        }
    }
    
    /**
     * 주문 완료 처리
     */
    private void completeOrder(String orderNumber) {
        log.info("Completing order: orderNumber={}", orderNumber);
        
        Order order = orderRepository.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new RuntimeException("Order not found: " + orderNumber));
        
        order.setStatus(Order.OrderStatus.COMPLETED);
        orderRepository.save(order);
        
        log.info("Order completed successfully: orderNumber={}", orderNumber);
    }
}
