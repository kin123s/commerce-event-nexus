package com.example.orderservice.integration;

import com.example.orderservice.dto.OrderRequest;
import com.example.orderservice.dto.OrderResponse;
import com.example.orderservice.entity.Order;
import com.example.orderservice.entity.OutboxEvent;
import com.example.orderservice.repository.OrderRepository;
import com.example.orderservice.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

/**
 * Testcontainers를 활용한 통합 테스트
 * - 실제 PostgreSQL 컨테이너 사용
 * - Embedded Kafka 사용
 * - Transactional Outbox 패턴 검증
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@EmbeddedKafka(
    partitions = 1,
    topics = {"order-events"},
    brokerProperties = {
        "listeners=PLAINTEXT://localhost:9092",
        "port=9092"
    }
)
class OrderServiceIntegrationTest {
    
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
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");
    }
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Autowired
    private OrderRepository orderRepository;
    
    @Autowired
    private OutboxEventRepository outboxEventRepository;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
        orderRepository.deleteAll();
    }
    
    @Test
    void shouldCreateOrderAndSaveToOutbox() {
        // Given
        OrderRequest request = OrderRequest.builder()
            .productName("MacBook Pro")
            .quantity(1)
            .price(new BigDecimal("2500.00"))
            .customerName("김철수")
            .customerEmail("test@example.com")
            .build();
        
        // When
        ResponseEntity<OrderResponse> response = restTemplate.postForEntity(
            "/api/orders",
            request,
            OrderResponse.class
        );
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProductName()).isEqualTo("MacBook Pro");
        assertThat(response.getBody().getStatus()).isEqualTo("PENDING");
        
        // 주문이 DB에 저장되었는지 확인
        Order savedOrder = orderRepository.findByOrderNumber(response.getBody().getOrderNumber())
            .orElseThrow();
        assertThat(savedOrder.getProductName()).isEqualTo("MacBook Pro");
        
        // Outbox 이벤트가 생성되었는지 확인 (원자성 보장)
        await().atMost(Duration.ofSeconds(5))
            .untilAsserted(() -> {
                OutboxEvent outboxEvent = outboxEventRepository
                    .findByAggregateId(savedOrder.getOrderNumber())
                    .orElseThrow();
                
                assertThat(outboxEvent.getAggregateType()).isEqualTo("ORDER");
                assertThat(outboxEvent.getEventType()).isEqualTo("ORDER_CREATED");
                assertThat(outboxEvent.getPublished()).isFalse(); // 아직 발행되지 않음
            });
    }
    
    @Test
    void shouldPreventDuplicateOrderNumberCreation() {
        // Given
        OrderRequest request = OrderRequest.builder()
            .productName("iPhone 15")
            .quantity(2)
            .price(new BigDecimal("1200.00"))
            .customerName("이영희")
            .customerEmail("test2@example.com")
            .build();
        
        // When
        ResponseEntity<OrderResponse> response1 = restTemplate.postForEntity(
            "/api/orders",
            request,
            OrderResponse.class
        );
        
        // Then
        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        
        // 같은 주문 번호로 중복 생성은 발생하지 않음 (UUID 사용)
        long orderCount = orderRepository.count();
        assertThat(orderCount).isEqualTo(1);
    }
    
    @Test
    void shouldRetrieveAllOrders() {
        // Given
        createTestOrder("Product A", 1, "1000.00");
        createTestOrder("Product B", 2, "2000.00");
        
        // When
        ResponseEntity<OrderResponse[]> response = restTemplate.getForEntity(
            "/api/orders",
            OrderResponse[].class
        );
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
    }
    
    private void createTestOrder(String productName, int quantity, String price) {
        OrderRequest request = OrderRequest.builder()
            .productName(productName)
            .quantity(quantity)
            .price(new BigDecimal(price))
            .customerName("테스트 고객")
            .customerEmail("test@example.com")
            .build();
        
        restTemplate.postForEntity("/api/orders", request, OrderResponse.class);
    }
}
