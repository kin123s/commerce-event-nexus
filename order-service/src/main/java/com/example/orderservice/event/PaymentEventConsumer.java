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
 * Saga Pattern - Choreography 방식의 이벤트 컨슈머
 * 
 * 역할: Payment Service에서 발행한 결제 결과 이벤트를 수신하여 처리
 * 
 * Saga Pattern이 필요한 이유:
 * - 마이크로서비스 환경에서는 각 서비스가 독립적인 DB를 사용
 * - 전통적인 2PC(Two-Phase Commit)는 성능 이슈와 복잡도 문제
 * - 이벤트 기반으로 각 서비스가 로컬 트랜잭션을 실행하고 결과를 이벤트로 전파
 * 
 * Choreography vs Orchestration:
 * - Choreography(현재 방식): 각 서비스가 자율적으로 이벤트 구독/발행
 * - Orchestration: 중앙 조정자가 전체 흐름 관리
 * - 본 프로젝트는 서비스 간 결합도를 낮추기 위해 Choreography 선택
 * 
 * 보상 트랜잭션(Compensation):
 * - 결제 실패 시 이미 생성된 주문을 취소하는 보상 로직 실행
 * - 분산 환경에서 롤백을 구현하는 방법
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {
    
    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    
    /**
     * 결제 이벤트 수신 및 처리
     * 
     * Kafka Consumer 설정:
     * - topics: payment-events (Payment Service가 발행하는 토픽)
     * - groupId: order-service-group (같은 그룹의 컨슈머는 메시지를 분산 처리)
     * 
     * 이벤트 타입별 처리:
     * - PAYMENT_COMPLETED: 결제 성공 -> 주문 상태를 COMPLETED로 변경
     * - PAYMENT_FAILED: 결제 실패 -> 보상 트랜잭션으로 주문 취소 (Saga Pattern)
     * 
     * @Transactional이 중요한 이유:
     * - 주문 상태 변경과 보상 이벤트 발행을 하나의 트랜잭션으로 처리
     * - 중간에 실패하면 전체 롤백되어 데이터 일관성 유지
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
     * 보상 트랜잭션: 주문 취소 처리
     * 
     * 보상 트랜잭션이란:
     * - 분산 환경에서 이미 커밋된 트랜잭션을 "논리적으로 취소"하는 작업
     * - 실제 DB 롤백이 아닌, 반대 작업을 수행하여 효과를 상쇄
     * - 예: 주문 생성의 보상 = 주문 취소, 재고 차감의 보상 = 재고 복구
     * 
     * 본 메서드의 처리 흐름:
     * 1. 주문 상태를 CANCELLED로 변경
     * 2. ORDER_CANCELLED 이벤트를 Outbox에 저장
     * 3. 다른 서비스들이 이 이벤트를 받아 추가 보상 작업 수행 가능
     *    (예: 재고 서비스가 차감한 재고를 복구)
     * 
     * @param orderNumber 취소할 주문 번호
     * @param reason 취소 사유 (결제 실패 원인)
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
