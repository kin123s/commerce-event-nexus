package com.example.orderservice.repository;

import com.example.orderservice.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
    
    /**
     * 발행되지 않은 이벤트 조회 (오래된 순)
     */
    @Query("SELECT o FROM OutboxEvent o WHERE o.published = false AND o.retryCount < 5 ORDER BY o.createdAt ASC")
    List<OutboxEvent> findUnpublishedEvents();
    
    /**
     * 특정 시간 이전에 발행되지 않은 이벤트 조회
     */
    List<OutboxEvent> findByPublishedFalseAndCreatedAtBefore(LocalDateTime dateTime);
    
    /**
     * Aggregate ID로 조회
     */
    Optional<OutboxEvent> findByAggregateId(String aggregateId);
    
    /**
     * 발행 완료된 오래된 이벤트 조회 (정리용)
     */
    @Query("SELECT o FROM OutboxEvent o WHERE o.published = true AND o.publishedAt < :cutoffDate")
    List<OutboxEvent> findOldPublishedEvents(LocalDateTime cutoffDate);
}
