package com.example.paymentservice.repository;

import com.example.paymentservice.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, Long> {
    
    /**
     * 이벤트 ID로 이미 처리된 이벤트 조회
     */
    Optional<ProcessedEvent> findByEventId(String eventId);
    
    /**
     * 이벤트가 이미 처리되었는지 확인
     */
    boolean existsByEventId(String eventId);
}
