package com.example.paymentservice.integration;

import com.example.paymentservice.entity.Payment;
import com.example.paymentservice.entity.ProcessedEvent;
import com.example.paymentservice.event.OrderEvent;
import com.example.paymentservice.repository.PaymentRepository;
import com.example.paymentservice.repository.ProcessedEventRepository;
import com.example.paymentservice.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Payment Service 통합 테스트
 * - 멱등성 검증
 * - 중복 처리 방지 확인
 */
@SpringBootTest
@Testcontainers
@EmbeddedKafka(
    partitions = 1,
    topics = {"order-events", "payment-events"}
)
class PaymentServiceIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    
    @Autowired
    private PaymentService paymentService;
    
    @Autowired
    private PaymentRepository paymentRepository;
    
    @Autowired
    private ProcessedEventRepository processedEventRepository;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        processedEventRepository.deleteAll();
        paymentRepository.deleteAll();
    }
    
    @Test
    void shouldProcessPaymentSuccessfully() {
        // Given
        OrderEvent orderEvent = createTestOrderEvent("ORD-12345");
        
        // When
        paymentService.processPayment(orderEvent);
        
        // Then
        Payment payment = paymentRepository.findByOrderNumber("ORD-12345")
            .orElseThrow();
        
        assertThat(payment.getOrderNumber()).isEqualTo("ORD-12345");
        assertThat(payment.getAmount()).isEqualByComparingTo(new BigDecimal("3000.00"));
        
        // 멱등성 키 확인
        ProcessedEvent processedEvent = processedEventRepository.findByEventId("ORD-12345")
            .orElseThrow();
        
        assertThat(processedEvent.getEventId()).isEqualTo("ORD-12345");
        assertThat(processedEvent.getResult()).isIn("SUCCESS", "FAILED");
    }
    
    @Test
    void shouldPreventDuplicatePaymentProcessing() {
        // Given
        OrderEvent orderEvent = createTestOrderEvent("ORD-67890");
        
        // When - 첫 번째 처리
        paymentService.processPayment(orderEvent);
        long paymentCountAfterFirst = paymentRepository.count();
        
        // When - 중복 처리 시도 (멱등성 테스트)
        paymentService.processPayment(orderEvent);
        long paymentCountAfterSecond = paymentRepository.count();
        
        // Then - 결제는 한 번만 처리되어야 함
        assertThat(paymentCountAfterFirst).isEqualTo(1);
        assertThat(paymentCountAfterSecond).isEqualTo(1); // 증가하지 않음
        
        // ProcessedEvent도 하나만 존재
        long processedEventCount = processedEventRepository.count();
        assertThat(processedEventCount).isEqualTo(1);
    }
    
    @Test
    void shouldHandleMultipleOrdersIndependently() {
        // Given
        OrderEvent order1 = createTestOrderEvent("ORD-AAA");
        OrderEvent order2 = createTestOrderEvent("ORD-BBB");
        OrderEvent order3 = createTestOrderEvent("ORD-CCC");
        
        // When
        paymentService.processPayment(order1);
        paymentService.processPayment(order2);
        paymentService.processPayment(order3);
        
        // Then
        assertThat(paymentRepository.count()).isEqualTo(3);
        assertThat(processedEventRepository.count()).isEqualTo(3);
    }
    
    private OrderEvent createTestOrderEvent(String orderNumber) {
        return OrderEvent.builder()
            .orderId(1L)
            .orderNumber(orderNumber)
            .productName("Test Product")
            .quantity(1)
            .price(new BigDecimal("3000.00"))
            .totalAmount(new BigDecimal("3000.00"))
            .customerName("테스트 고객")
            .customerEmail("test@example.com")
            .status("PENDING")
            .eventTime(LocalDateTime.now())
            .eventType("ORDER_CREATED")
            .build();
    }
}
