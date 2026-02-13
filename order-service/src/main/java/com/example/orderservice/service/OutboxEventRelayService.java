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
 * 
 * Transactional Outbox Pattern의 핵심 구성 요소
 * 
 * 왜 이 서비스가 필요한가:
 * - OrderService는 이벤트를 DB(outbox_events)에만 저장하고 즉시 Kafka로 발행하지 않음
 * - 이 서비스가 주기적으로 DB를 폴링하여 미발행 이벤트를 Kafka로 전송
 * - 이를 통해 DB 트랜잭션과 메시지 발행의 원자성 보장
 * 
 * 폴링 방식의 장단점:
 * - 장점: 구현이 단순하고 안정적, Kafka 장애에 대한 복원력
 * - 단점: 실시간성이 떨어짐 (폴링 주기만큼 지연)
 * 
 * 프로덕션 환경에서의 대안:
 * - Debezium CDC: DB 변경 로그를 실시간으로 캡처하여 Kafka로 전송
 * - Transaction Log Tailing: DB의 Write-Ahead Log를 직접 읽어 이벤트 발행
 * - 본 프로젝트는 간단한 구현을 위해 폴링 방식 사용
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
     * 
     * @Scheduled 설정:
     * - fixedDelay = 5000: 이전 작업 완료 후 5초 뒤에 다시 실행
     * - fixedRate와의 차이: fixedDelay는 작업 완료 시점 기준, fixedRate는 작업 시작 시점 기준
     * - 5초 간격은 실시간성과 DB 부하의 균형을 고려한 값 (프로덕션에서는 조정 필요)
     * 
     * 처리 흐름:
     * 1. published = false인 이벤트들을 조회
     * 2. 각 이벤트를 Kafka로 발행 시도
     * 3. 성공하면 published = true로 업데이트
     * 4. 실패하면 retry_count 증가 및 error_message 기록
     * 
     * 재시도 전략:
     * - 최대 5회까지 재시도 (MAX_RETRY_COUNT)
     * - 네트워크 일시 장애나 Kafka 다운타임에 대응
     * - 5회 초과 시 수동 처리 필요 (실제로는 DLQ나 알림 시스템 연동)
     * 
     * @Transactional 주의사항:
     * - Kafka 발행은 외부 시스템 호출이므로 트랜잭션 범위 밖에서 실행
     * - CompletableFuture.whenComplete()에서 비동기로 published 플래그 업데이트
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
