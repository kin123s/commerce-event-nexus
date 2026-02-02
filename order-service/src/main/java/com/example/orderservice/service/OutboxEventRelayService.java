package com.example.orderservice.service;

import com.example.orderservice.entity.OutboxEvent;
import com.example.orderservice.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Outbox Event Relay Service
 * - Polling 방식으로 미발행 이벤트를 Kafka로 발행
 * - Debezium CDC 대안으로 사용 가능
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxEventRelayService {
    
    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    private static final String TOPIC_NAME = "order-events";
    private static final int MAX_RETRY_COUNT = 5;
    
    /**
     * 주기적으로 Outbox 테이블을 폴링하여 미발행 이벤트 처리
     * 실제 운영 환경에서는 Debezium CDC를 권장
     */
    @Scheduled(fixedDelay = 5000) // 5초마다 실행
    @Transactional
    public void relayUnpublishedEvents() {
        List<OutboxEvent> unpublishedEvents = outboxEventRepository.findUnpublishedEvents();
        
        if (unpublishedEvents.isEmpty()) {
            return;
        }
        
        log.info("Found {} unpublished events to relay", unpublishedEvents.size());
        
        for (OutboxEvent event : unpublishedEvents) {
            try {
                // Kafka로 발행
                kafkaTemplate.send(TOPIC_NAME, event.getAggregateId(), event.getPayload())
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            event.markAsPublished();
                            outboxEventRepository.save(event);
                            log.info("Event relayed successfully: aggregateId={}, eventType={}", 
                                event.getAggregateId(), event.getEventType());
                        } else {
                            handlePublishError(event, ex);
                        }
                    });
                    
            } catch (Exception e) {
                handlePublishError(event, e);
            }
        }
    }
    
    /**
     * 발행 실패 처리
     */
    @Transactional
    protected void handlePublishError(OutboxEvent event, Throwable error) {
        log.error("Failed to relay event: aggregateId={}, retryCount={}, error={}", 
            event.getAggregateId(), event.getRetryCount(), error.getMessage());
        
        event.incrementRetryCount(error.getMessage());
        outboxEventRepository.save(event);
        
        if (event.getRetryCount() >= MAX_RETRY_COUNT) {
            log.error("Event exceeded max retry count, manual intervention required: aggregateId={}", 
                event.getAggregateId());
            // 실제로는 Dead Letter Queue나 알림 시스템으로 전송
        }
    }
    
    /**
     * 오래된 발행 완료 이벤트 정리 (7일 이상 된 것)
     */
    @Scheduled(cron = "0 0 2 * * *") // 매일 새벽 2시
    @Transactional
    public void cleanupOldEvents() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(7);
        List<OutboxEvent> oldEvents = outboxEventRepository.findOldPublishedEvents(cutoffDate);
        
        if (!oldEvents.isEmpty()) {
            outboxEventRepository.deleteAll(oldEvents);
            log.info("Cleaned up {} old published events", oldEvents.size());
        }
    }
}
