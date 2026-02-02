package com.example.orderservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Transactional Outbox Pattern 구현
 * DB 업데이트와 이벤트 발행의 원자성을 보장
 */
@Entity
@Table(name = "outbox_events", indexes = {
    @Index(name = "idx_published_created", columnList = "published,createdAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String aggregateId; // 주문 ID
    
    @Column(nullable = false)
    private String aggregateType; // "ORDER"
    
    @Column(nullable = false)
    private String eventType; // "ORDER_CREATED", "ORDER_CANCELLED"
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload; // JSON 형태의 이벤트 데이터
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean published = false;
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    private LocalDateTime publishedAt;
    
    @Column(columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(nullable = false)
    @Builder.Default
    private Integer retryCount = 0;
    
    public void markAsPublished() {
        this.published = true;
        this.publishedAt = LocalDateTime.now();
    }
    
    public void incrementRetryCount(String error) {
        this.retryCount++;
        this.errorMessage = error;
    }
}
