# 성능 최적화 가이드

## 1. Kafka 성능 최적화

### Producer 설정

```yaml
spring:
  kafka:
    producer:
      acks: 1  # 0: 응답 없음, 1: 리더만, all: 모든 복제본
      batch-size: 16384  # 배치 크기
      linger-ms: 10  # 배치 전송 대기 시간
      compression-type: snappy  # 압축 타입 (none, gzip, snappy, lz4, zstd)
      retries: 3
      buffer-memory: 33554432  # 32MB
```

### Consumer 설정

```yaml
spring:
  kafka:
    consumer:
      max-poll-records: 500  # 한 번에 가져올 레코드 수
      fetch-min-bytes: 1024  # 최소 fetch 크기
      fetch-max-wait-ms: 500  # 최대 대기 시간
      enable-auto-commit: false  # 수동 커밋 권장
```

### 파티션 수 계산

```
파티션 수 = Max(목표 처리량 / Producer 처리량, 목표 처리량 / Consumer 처리량)
```

**예시**:
- 목표: 초당 100,000 메시지 처리
- Producer 처리량: 초당 10,000 메시지/파티션
- Consumer 처리량: 초당 5,000 메시지/파티션

```
파티션 수 = Max(100,000/10,000, 100,000/5,000) = Max(10, 20) = 20
```

## 2. 데이터베이스 최적화

### 인덱스 전략

```sql
-- Outbox 테이블 인덱스
CREATE INDEX idx_outbox_published_created 
ON outbox_events(published, created_at) 
WHERE published = false;

-- Processed Events 테이블 인덱스
CREATE UNIQUE INDEX idx_processed_event_id 
ON processed_events(event_id);

-- 주문 조회 인덱스
CREATE INDEX idx_order_status_created 
ON orders(status, created_at DESC);
```

### Connection Pool 설정

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

### 쿼리 최적화

```java
// N+1 문제 방지
@Query("SELECT o FROM Order o JOIN FETCH o.items WHERE o.status = :status")
List<Order> findAllWithItems(@Param("status") OrderStatus status);

// Projection 사용
@Query("SELECT new com.example.dto.OrderSummary(o.id, o.orderNumber, o.totalAmount) FROM Order o")
List<OrderSummary> findAllSummaries();
```

## 3. Redis 캐싱 전략

### Cache-Aside Pattern

```java
@Cacheable(value = "orders", key = "#id")
public OrderResponse getOrderById(Long id) {
    return orderRepository.findById(id)
        .map(OrderResponse::fromEntity)
        .orElseThrow();
}

@CacheEvict(value = "orders", key = "#id")
public void updateOrder(Long id, OrderRequest request) {
    // 업데이트 로직
}
```

### TTL 설정

```yaml
spring:
  cache:
    redis:
      time-to-live: 3600000  # 1시간
```

## 4. 서비스 튜닝

### JVM 옵션

```bash
JAVA_OPTS="
  -Xms512m
  -Xmx1g
  -XX:+UseG1GC
  -XX:MaxGCPauseMillis=200
  -XX:ParallelGCThreads=4
  -XX:+HeapDumpOnOutOfMemoryError
  -XX:HeapDumpPath=/var/log
"
```

### Thread Pool 설정

```yaml
spring:
  task:
    execution:
      pool:
        core-size: 8
        max-size: 16
        queue-capacity: 100
```

## 5. 모니터링 메트릭

### 주요 메트릭

1. **Kafka Consumer Lag**: 처리 지연 모니터링
2. **API Response Time**: P50, P95, P99
3. **Database Connection Pool**: 활성/유휴 연결 수
4. **JVM Memory**: Heap 사용률
5. **Error Rate**: 초당 에러 발생 수

### Prometheus 쿼리 예시

```promql
# Consumer Lag
kafka_consumer_lag{topic="order-events"} > 1000

# API 지연시간 (P95)
histogram_quantile(0.95, 
  rate(http_server_requests_seconds_bucket[5m])
)

# Error Rate
rate(http_server_requests_total{status=~"5.."}[1m])
```

## 6. 부하 테스트

### k6 스크립트 예시

```javascript
import http from 'k6/http';
import { check } from 'k6';

export let options = {
  stages: [
    { duration: '1m', target: 100 },  // Ramp up
    { duration: '5m', target: 100 },  // Stay at 100 users
    { duration: '1m', target: 0 },    // Ramp down
  ],
};

export default function() {
  let payload = JSON.stringify({
    productName: 'Test Product',
    quantity: 1,
    price: 10000,
    customerName: 'Test User',
    customerEmail: 'test@example.com'
  });

  let params = {
    headers: {
      'Content-Type': 'application/json',
    },
  };

  let res = http.post('http://localhost:8080/api/orders', payload, params);
  
  check(res, {
    'status is 201': (r) => r.status === 201,
    'response time < 500ms': (r) => r.timings.duration < 500,
  });
}
```

### 실행

```bash
k6 run --vus 100 --duration 5m load-test.js
```

## 7. 확장 전략

### Horizontal Scaling

```bash
# Kubernetes에서 수동 스케일링
kubectl scale deployment order-service --replicas=5 -n microservices

# HPA로 자동 스케일링
kubectl autoscale deployment order-service \
  --cpu-percent=70 \
  --min=2 \
  --max=10 \
  -n microservices
```

### Vertical Scaling

```yaml
resources:
  requests:
    memory: "1Gi"
    cpu: "1000m"
  limits:
    memory: "2Gi"
    cpu: "2000m"
```

## 성능 목표

| 메트릭 | 목표 | 측정 방법 |
|--------|------|-----------|
| API Response Time (P95) | < 500ms | Prometheus |
| Throughput | > 1000 TPS | k6 |
| Kafka Consumer Lag | < 100 | Kafka Manager |
| Error Rate | < 0.1% | Application Logs |
| Database Query Time | < 100ms | Slow Query Log |
