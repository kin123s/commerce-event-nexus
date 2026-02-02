# Debezium Connector 설정 가이드

Debezium을 사용하여 PostgreSQL의 변경사항을 실시간으로 Kafka에 전송합니다.

## 1. Debezium Connector 생성

Outbox 테이블의 변경사항을 캡처하여 Kafka로 전송하는 커넥터를 생성합니다.

```bash
curl -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  -d '{
    "name": "order-outbox-connector",
    "config": {
      "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
      "database.hostname": "postgres-order",
      "database.port": "5432",
      "database.user": "postgres",
      "database.password": "postgres",
      "database.dbname": "orderdb",
      "database.server.name": "orderdb",
      "table.include.list": "public.outbox_events",
      "plugin.name": "pgoutput",
      "publication.autocreate.mode": "filtered",
      "transforms": "outbox",
      "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
      "transforms.outbox.table.field.event.id": "aggregate_id",
      "transforms.outbox.table.field.event.key": "aggregate_id",
      "transforms.outbox.table.field.event.type": "event_type",
      "transforms.outbox.table.field.event.payload": "payload",
      "transforms.outbox.route.topic.replacement": "order-events"
    }
  }'
```

## 2. Connector 상태 확인

```bash
# 모든 커넥터 목록
curl http://localhost:8083/connectors

# 특정 커넥터 상태
curl http://localhost:8083/connectors/order-outbox-connector/status
```

## 3. Connector 삭제

```bash
curl -X DELETE http://localhost:8083/connectors/order-outbox-connector
```

## 4. PostgreSQL 설정

PostgreSQL에서 logical replication을 활성화해야 합니다.

```sql
-- Replication 슬롯 확인
SELECT * FROM pg_replication_slots;

-- Publication 확인
SELECT * FROM pg_publication;

-- Publication에 포함된 테이블 확인
SELECT * FROM pg_publication_tables;
```

## 주의사항

1. **WAL 레벨**: PostgreSQL의 `wal_level`이 `logical`로 설정되어 있어야 합니다.
2. **성능**: CDC는 데이터베이스에 약간의 오버헤드를 추가하므로, 운영 환경에서는 모니터링이 필요합니다.
3. **Replication Slot**: Connector가 중지되면 replication slot이 계속 WAL을 쌓을 수 있으므로 관리가 필요합니다.

## Polling 방식과 비교

### Polling 방식 (현재 구현)
- **장점**: 구현이 간단하고 DB에 부담이 적음
- **단점**: 실시간성이 떨어지고, 주기적인 DB 조회 필요

### CDC 방식 (Debezium - 권장)
- **장점**: 실시간 이벤트 전파, DB 부하 감소
- **단점**: 설정이 복잡하고, 인프라 의존성 증가

## 운영 환경 권장사항

운영 환경에서는 Debezium CDC 방식을 사용하는 것을 권장합니다:
- 실시간성 보장
- DB 폴링 오버헤드 제거
- 확장성 향상
