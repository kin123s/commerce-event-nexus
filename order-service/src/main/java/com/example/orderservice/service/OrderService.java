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

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {
    
    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    
    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        log.info("Creating order for customer: {}", request.getCustomerName());
        
        // 총 금액 계산
        BigDecimal totalAmount = request.getPrice()
            .multiply(BigDecimal.valueOf(request.getQuantity()));
        
        // 주문 번호 생성
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
        log.info("Order saved to database: orderNumber={}", savedOrder.getOrderNumber());
        Outbox 이벤트 생성 (같은 트랜잭션 내에서)
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
        eventPublisher.publishOrderEvent(event);
        
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
