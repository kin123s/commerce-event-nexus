package com.example.orderservice.service;

import com.example.orderservice.dto.OrderRequest;
import com.example.orderservice.dto.OrderResponse;
import com.example.orderservice.entity.Order;
import com.example.orderservice.entity.OutboxEvent;
import com.example.orderservice.event.OrderEvent;
import com.example.orderservice.repository.OrderRepository;
import com.example.orderservice.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 주문 서비스 핵심 비즈니스 로직
 * 
 * Transactional Outbox Pattern 적용:
 * - 로컬 트랜잭션과 이벤트 발행의 원자성 보장
 * - 주문 저장과 이벤트 저장을 같은 트랜잭션으로 묶어 데이터 정합성 확보
 * 
 * 왜 Outbox 패턴을 사용했는가:
 * - 주문 저장은 성공했는데 Kafka 발행이 실패하는 경우 방지
 * - Kafka가 다운되어도 서비스가 정상 동작 가능
 * - 별도의 Relay Service가 Outbox 테이블을 폴링하여 이벤트 발행
 * 
 * @author Order-Payment MSA Team
 */
@Slf4f
@Service
@RequiredArgsConstructor
public class OrderService {
    
    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    
    /**
     * 주문 생성 및 이벤트 발행
     * 
     * 트랜잭션 처리 전략:
     * - @Transactional로 주문 저장과 Outbox 이벤트 저장을 하나의 트랜잭션으로 묶음
     * - 둘 다 성공하거나 둘 다 실패하도록 보장 (원자성)
     * - 이를 통해 "이벤트 발행 누락" 문제 해결
     * 
     * Outbox 패턴 동작 방식:
     * 1. 주문 정보를 orders 테이블에 저장
     * 2. 발행할 이벤트를 outbox_events 테이블에 저장 (같은 트랜잭션)
     * 3. OutboxEventRelayService가 주기적으로 outbox_events를 폴링
     * 4. 미발행 이벤트를 Kafka로 발행
     * 5. 발행 성공 시 published = true로 업데이트
     * 
     * @param request 주문 요청 DTO
     * @return 생성된 주문 정보
     */
    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        log.info("Creating order for customer: {}", request.getCustomerName());
        
        // 총 금액 계산
        BigDecimal totalAmount = request.getPrice()
            .multiply(BigDecimal.valueOf(request.getQuantity()));
        
        // 주문 번호 생성 (UUID 기반으로 고유성 보장)
        String orderNumber = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        
        // 주문 엔티티 생성
        Order order = Order.builder()
            .orderNumber(orderNumber)
            .productName(request.getProductName())
            .quantity(request.getQuantity())
            .price(request.getPrice())
            .totalAmount(totalAmount)
            .customerName(request.getCustomerName())
            .customerEmail(request.getCustomerEmail())
            .status(Order.OrderStatus.PENDING)
            .build();
        
        // DB 저장
        Order savedOrder = orderRepository.save(order);
        // 이 부분이 Outbox 패턴의 핵심:
        // - 같은 DB 트랜잭션 내에서 처리되므로 주문과 이벤트가 함께 커밋됨
        // - Kafka 장애 상황에서도 이벤트 손실 없음
        // - 별도의 Relay Service가 나중에 발행 처리
        log.info("Order saved to database: orderNumber={}", savedOrder.getOrderNumber());
        
        // Outbox 이벤트 생성 (같은 트랜잭션 내에서)
        OrderEvent event = OrderEvent.builder()
            .orderId(savedOrder.getId())
            .orderNumber(savedOrder.getOrderNumber())
            .productName(savedOrder.getProductName())
            .quantity(savedOrder.getQuantity())
            .price(savedOrder.getPrice())
            .totalAmount(savedOrder.getTotalAmount())
            .customerName(savedOrder.getCustomerName())
            .customerEmail(savedOrder.getCustomerEmail())
            .status(savedOrder.getStatus().name())
            .eventTime(LocalDateTime.now())
            .eventType("ORDER_CREATED")
            .build();
        
        // Outbox 테이블에 이벤트 저장 (원자성 보장)
        try {
            OutboxEvent outboxEvent = OutboxEvent.builder()
                .aggregateId(savedOrder.getOrderNumber())
                .aggregateType("ORDER")
                .eventType("ORDER_CREATED")
                .payload(objectMapper.writeValueAsString(event))
                .build();
            
            outboxEventRepository.save(outboxEvent);
            log.info("Outbox event saved: orderNumber={}", savedOrder.getOrderNumber());
            
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event to JSON", e);
            throw new RuntimeException("Failed to create outbox event", e);
        }
        // eventPublisher.publishOrderEvent(event); // Outbox 패턴 사용으로 직접 발행 대신 OutboxEventRelayService가 처리
        
        return OrderResponse.fromEntity(savedOrder);
    }
    
    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders() {
        log.info("Fetching all orders");
        return orderRepository.findAll().stream()
            .map(OrderResponse::fromEntity)
            .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {
        log.info("Fetching order by id: {}", id);
        Order order = orderRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Order not found with id: " + id));
        return OrderResponse.fromEntity(order);
    }
    
    @Transactional(readOnly = true)
    public OrderResponse getOrderByNumber(String orderNumber) {
        log.info("Fetching order by number: {}", orderNumber);
        Order order = orderRepository.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new RuntimeException("Order not found with number: " + orderNumber));
        return OrderResponse.fromEntity(order);
    }
}
