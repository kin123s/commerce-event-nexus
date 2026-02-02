package com.example.paymentservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 멱등성 보장을 위한 이벤트 처리 기록
 * 중복 메시지 처리 방지
 */
@Entity
@Table(name = "processed_events", indexes = {
    @Index(name = "idx_event_id", columnList = "eventId", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedEvent {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String eventId; // aggregateId (orderNumber)
    
    @Column(nullable = false)
    private String eventType; // ORDER_CREATED
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime processedAt;
    
    @Column(nullable = false)
    private String result; // SUCCESS, FAILED
    
    @Column(columnDefinition = "TEXT")
    private String errorMessage;
}
