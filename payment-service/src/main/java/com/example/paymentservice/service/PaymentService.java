package com.example.paymentservice.service;

import com.example.paymentservice.dto.PaymentResponse;
import com.example.paymentservice.entity.Payment;
import com.example.paymentservice.entity.ProcessedEvent;
import com.example.paymentservice.event.OrderEvent;
import com.example.paymentservice.repository.PaymentRepository;
import com.example.paymentservice.repository.ProcessedEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 결제 서비스 핵심 비즈니스 로직
 * 
 * 분산 시스템에서 데이터 정합성을 보장하기 위한 여러 패턴을 적용:
 * 1. 멱등성(Idempotency): 네트워크 재시도로 인한 중복 처리 방지
 * 2. Saga Pattern: 분산 트랜잭션 관리 (Choreography 방식)
 * 3. 이벤트 기반 아키텍처: 서비스 간 느슨한 결합
 * 
 * @author Order-Payment MSA Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {
    
    private final PaymentRepository paymentRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    private static final String PAYMENT_EVENTS_TOPIC = "payment-events";
    // 결제 성공률 시뮬레이션을 위한 Random 객체 (운영 환경에서는 실제 PG사 결과 사용)
    private final Random random = new Random();
    
    /**
     * 멱등성을 보장하는 결제 처리
     * 
     * 멱등성이 중요한 이유:
     * - Kafka는 'at-least-once' 전달 보장 방식을 사용
     * - 네트워크 장애나 컨슈머 재시작 시 같은 메시지를 여러 번 받을 수 있음
     * - 중복 결제를 방지하기 위해 이벤트 ID 기반으로 처리 여부를 확인
     * 
     * ProcessedEvent 테이블을 사용하는 이유:
     * - DB 트랜잭션과 함께 관리되어 정합성 보장
     * - 처리 이력 추적 및 디버깅 용이
     * - 실패한 이벤트 재처리 시 참고 가능
     * 
     * @param orderEvent 주문 생성 이벤트 (Kafka에서 수신)
     */
    @Transactional
    public void processPayment(OrderEvent orderEvent) {
        String eventId = orderEvent.getOrderNumber();
        
        // 1. 멱등성 체크: 이미 처리된 이벤트인지 확인
        // orderNumber를 eventId로 사용하여 동일 주문의 중복 처리 방지
        if (processedEventRepository.existsByEventId(eventId)) {
            log.info("Event already processed, skipping: eventId={}", eventId);
            return;
        }
        
        log.info("Processing payment for order: {}", orderEvent.getOrderNumber());
        
        try {
            // 2. 처리 중인 이벤트 기록 (중복 처리 방지)
            // DB에 먼저 기록함으로써, 처리 도중 실패해도 재시도 시 멱등성 보장
            // PROCESSING 상태로 저장하여 현재 처리 중임을 표시
            ProcessedEvent processedEvent = ProcessedEvent.builder()
                .eventId(eventId)
                .eventType("ORDER_CREATED")
                .payload(objectMapper.writeValueAsString(orderEvent))
                .result("PROCESSING")
                .build();
            processedEventRepository.save(processedEvent);
            
            // 3. 결제 처리
            Payment payment = executePayment(orderEvent);
            
            // 4. 결제 결과 이벤트 발행
            publishPaymentResultEvent(payment, orderEvent);
            
            // 5. 처리 완료 기록 업데이트
            processedEvent.setResult(payment.getStatus() == Payment.PaymentStatus.COMPLETED 
                ? "SUCCESS" : "FAILED");
            processedEventRepository.save(processedEvent);
            
        } catch (Exception e) {
            log.error("Failed to process payment for order: {}", orderEvent.getOrderNumber(), e);
            recordFailedProcessing(eventId, e);
            throw new RuntimeException("Payment processing failed", e);
        }
    }
    
    /**
     * 실제 결제 처리 로직
     * 
     * 실제 운영 환경에서는:
     * - 외부 PG사(토스페이먼츠, KG이니시스 등) API 호출
     * - WebClient나 RestTemplate을 사용한 HTTP 통신
     * - 타임아웃 설정과 예외 처리 필수
     * 
     * 현재 구현:
     * - 결제 프로세스를 시뮬레이션하여 시스템 동작 테스트
     * - 90% 성공률로 설정하여 실패 케이스(보상 트랜잭션)도 테스트 가능
     * 
     * @param orderEvent 주문 정보
     * @return 처리된 결제 엔티티
     * @throws InterruptedException 결제 처리 시뮬레이션 중 인터럽트
     */
    private Payment executePayment(OrderEvent orderEvent) throws InterruptedException {
        // 결제 번호 생성
        String paymentNumber = "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        
        // 트랜잭션 ID 생성 (실제로는 PG사에서 받음)
        String transactionId = "TXN-" + System.currentTimeMillis();
        
        // 결제 방법 랜덤 선택 (데모용)
        String[] methods = {"CARD", "BANK_TRANSFER", "MOBILE"};
        String paymentMethod = methods[random.nextInt(methods.length)];
        
        // 결제 엔티티 생성
        Payment payment = Payment.builder()
            .paymentNumber(paymentNumber)
            .orderId(orderEvent.getOrderId())
            .orderNumber(orderEvent.getOrderNumber())
            .amount(orderEvent.getTotalAmount())
            .customerName(orderEvent.getCustomerName())
            .customerEmail(orderEvent.getCustomerEmail())
            .status(Payment.PaymentStatus.PROCESSING)
            .paymentMethod(paymentMethod)
            .transactionId(transactionId)
            .build();
        
        // DB 저장
        Payment savedPayment = paymentRepository.save(payment);
        log.info("Payment created: paymentNumber={}", savedPayment.getPaymentNumber());
        
        // 결제 처리 시뮬레이션 (실제로는 PG사 API 호출)
        Thread.sleep(1000); // 결제 처리 시뮬레이션
        
        // 90% 성공률
        boolean isSuccess = random.nextInt(10) < 9;
        
       
     * Saga Pattern의 핵심 부분:
     * - 결제 성공 시: PAYMENT_COMPLETED 이벤트 발행 -> Order Service가 주문 완료 처리
     * - 결제 실패 시: PAYMENT_FAILED 이벤트 발행 -> Order Service가 보상 트랜잭션(주문 취소) 실행
     * 
     * Choreography 방식을 사용하는 이유:
     * - 중앙 Orchestrator 없이 각 서비스가 자율적으로 동작
     * - 서비스 간 의존성 감소, 확장성 증가
     * - 이벤트 기반으로 느슨한 결합 유지
     * 
     * @param payment 처리된 결제 정보
     * @param orderEvent 원본 주문 이벤트
     *  if (isSuccess) {
            savedPayment.setStatus(Payment.PaymentStatus.COMPLETED);
            log.info("Payment completed: paymentNumber={}", savedPayment.getPaymentNumber());
        } else {
            savedPayment.setStatus(Payment.PaymentStatus.FAILED);
            log.warn("Payment failed: paymentNumber={}", savedPayment.getPaymentNumber());
        }
        
        return paymentRepository.save(savedPayment);
    }
    
    /**
     * 결제 결과 이벤트를 Kafka로 발행
     */
    private void publishPaymentResultEvent(Payment payment, OrderEvent orderEvent) {
        try {
            PaymentEventDTO paymentEvent = PaymentEventDTO.builder()
                .paymentId(payment.getId())
                .orderId(payment.getOrderId())
                .orderNumber(payment.getOrderNumber())
                .paymentNumber(payment.getPaymentNumber())
                .amount(payment.getAmount())
                .status(payment.getStatus().name())
                .eventType(payment.getStatus() == Payment.PaymentStatus.COMPLETED 
                    ? "PAYMENT_COMPLETED" : "PAYMENT_FAILED")
                .failureReason(payment.getStatus() == Payment.PaymentStatus.FAILED 
                    ? "PG사 승인 거절" : null)
                .eventTime(LocalDateTime.now())
                .build();
            
            String payload = objectMapper.writeValueAsString(paymentEvent);
            
            kafkaTemplate.send(PAYMENT_EVENTS_TOPIC, payment.getOrderNumber(), payload);
            
            log.info("Payment result event published: orderNumber={}, status={}", 
                payment.getOrderNumber(), payment.getStatus());
                
        } catch (JsonProcessingException e) {
            log.error("Failed to publish payment result event", e);
            throw new RuntimeException("Failed to publish payment event", e);
        }
    }
    
    /**
     * 실패한 이벤트 처리 기록
     */
    private void recordFailedProcessing(String eventId, Exception e) {
        try {
            ProcessedEvent processedEvent = processedEventRepository.findByEventId(eventId)
                .orElse(ProcessedEvent.builder()
                    .eventId(eventId)
                    .eventType("ORDER_CREATED")
                    .payload("{}")
                    .build());
            
            processedEvent.setResult("FAILED");
            processedEvent.setErrorMessage(e.getMessage());
            processedEventRepository.save(processedEvent);
            
        } catch (Exception ex) {
            log.error("Failed to record failed processing", ex);
        }
    }
    
    @Transactional(readOnly = true)
    public List<PaymentResponse> getAllPayments() {
        log.info("Fetching all payments");
        return paymentRepository.findAll().stream()
            .map(PaymentResponse::fromEntity)
            .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(Long id) {
        log.info("Fetching payment by id: {}", id);
        Payment payment = paymentRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Payment not found with id: " + id));
        return PaymentResponse.fromEntity(payment);
    }
    
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByOrderNumber(String orderNumber) {
        log.info("Fetching payment by order number: {}", orderNumber);
        Payment payment = paymentRepository.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new RuntimeException("Payment not found for order: " + orderNumber));
        return PaymentResponse.fromEntity(payment);
    }
}
