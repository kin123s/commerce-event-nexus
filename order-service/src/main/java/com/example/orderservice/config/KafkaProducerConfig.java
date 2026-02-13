package com.example.orderservice.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Producer 설정
 * 
 * 두 가지 타입의 KafkaTemplate을 제공:
 * 1. KafkaTemplate<String, Object>: OrderEvent 등 객체를 JSON으로 직접 발행 (현재 미사용)
 * 2. KafkaTemplate<String, String>: OutboxEventRelayService에서 사용, JSON 문자열을 발행
 * 
 * 왜 String KafkaTemplate을 사용하는가:
 * - Outbox 테이블에 이미 JSON 문자열로 저장되어 있음
 * - 직렬화 오버헤드 감소 및 성능 향상
 * - 타입 정보 없이 전송하여 Consumer 측의 의존성 감소
 * 
 * JsonSerializer vs StringSerializer:
 * - JsonSerializer: 객체를 자동으로 JSON으로 변환
 * - StringSerializer: 이미 JSON 문자열인 데이터 전송
 * - ADD_TYPE_INFO_HEADERS=false: 타입 정보를 헤더에 포함하지 않음
 *   (다른 언어로 작성된 Consumer와의 호환성 향상)
 */
@Configuration
public class KafkaProducerConfig {
    
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;
    
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        config.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaProducerFactory<>(config);
    }
    
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
    
    @Bean
    public ProducerFactory<String, String> stringProducerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new DefaultKafkaProducerFactory<>(config);
    }
    
    @Bean
    public KafkaTemplate<String, String> stringKafkaTemplate() {
        return new KafkaTemplate<>(stringProducerFactory());
    }
}
