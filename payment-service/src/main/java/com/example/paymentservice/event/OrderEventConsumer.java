package com.example.paymentservice.event;

import com.example.paymentservice.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 주문 이벤트 컨슈머
 * 
 * Order Service에서 발행한 주문 생성 이벤트를 수신하여 결제 처리
 * 
 * Kafka Consumer 동작 원리:
 * - @KafkaListener가 백그라운드 스레드에서 지속적으로 토픽을 폴링
 * - 새로운 메시지가 있으면 자동으로 이 메서드 호출
 * - Spring이 역직렬화(deserialization)를 자동으로 처리
 * 
 * 에러 처리 전략:
 * - try-catch로 예외를 잡아 서비스 전체가 중단되지 않도록 방어
 * - 실제 프로덕션에서는 DLQ(Dead Letter Queue) 활용 권장
 * - 재시도 정책, 백오프 전략 등 추가 고려 필요
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {
    
    private final PaymentService paymentService;
    
    /**
     * 주문 생성 이벤트 수신 및 결제 처리
     * 
     * topics: order-events 토픽 구독 (OutboxEventRelayService가 발행)
     * groupId: 같은 그룹의 컨슈머들은 파티션을 나눠서 처리 (수평 확장 가능)
     * 
     * 처리 흐름:
     * 1. Kafka에서 ORDER_CREATED 이벤트 수신
     * 2. PaymentService.processPayment() 호출
     * 3. 멱등성 체크 -> 결제 처리 -> 결과 이벤트 발행
     * 4. Payment Service가 PAYMENT_COMPLETED/FAILED 이벤트를 발행하면
     *    Order Service가 이를 받아서 주문 상태 업데이트 (Saga 완성)
     */
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
