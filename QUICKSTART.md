# 분산 트랜잭션 정합성을 보장하는 주문-결제 MSA

실행 방법과 주요 기능에 대한 빠른 가이드입니다.

## 🚀 빠른 시작 (1분 만에 실행)

### 1. 환경 설정

```bash
# .env 파일 생성 (처음 실행 시)
cp .env.example .env

# 필요시 .env 파일에서 비밀번호 변경
# DB_PASSWORD=your_secure_password_here
```

### 2. 전체 시스템 실행

```bash
# 실행 권한 부여
chmod +x start.sh test-api.sh

# 시스템 시작 (5-10분 소요)
./start.sh
```

### 3. 테스트

```bash
# API 테스트 (시스템 시작 후)
./test-api.sh
```

## 📊 접속 URL

| 서비스 | URL | 설명 |
|--------|-----|------|
| Order Service | http://localhost:8080 | 주문 API |
| Payment Service | http://localhost:8081 | 결제 API |
| Admin Dashboard | http://localhost:3000 | 관리 대시보드 |
| Kafka UI | http://localhost:8090 | Kafka 모니터링 |
| Debezium | http://localhost:8083 | CDC 커넥터 |

## 🧪 API 테스트

### 주문 생성

```bash
curl -X POST http://localhost:8080/api/orders \
  -H 'Content-Type: application/json' \
  -d '{
    "productName": "MacBook Pro",
    "quantity": 1,
    "price": 2500000,
    "customerName": "김철수",
    "customerEmail": "test@example.com"
  }'
```

### 주문 조회

```bash
curl http://localhost:8080/api/orders | jq '.'
```

### 결제 조회

```bash
curl http://localhost:8081/api/payments | jq '.'
```

## 🔍 시스템 동작 확인

### 1. Saga Pattern 확인
- 주문 생성 → 결제 처리 → 주문 완료 (성공 시)
- 주문 생성 → 결제 실패 → 주문 취소 (실패 시, 10% 확률)

### 2. Kafka 메시지 확인
1. Kafka UI 접속: http://localhost:8090
2. Topics → `order-events`, `payment-events` 확인

### 3. DB 확인

```bash
# Order DB
docker exec -it postgres-order psql -U postgres -d orderdb
# \dt          # 테이블 목록
# SELECT * FROM orders;
# SELECT * FROM outbox_events;

# Payment DB  
docker exec -it postgres-payment psql -U postgres -d paymentdb
# SELECT * FROM payments;
# SELECT * FROM processed_events;
```

## 🛠️ 유용한 명령어

```bash
# 로그 확인
docker-compose logs -f order-service
docker-compose logs -f payment-service

# 서비스 재시작
docker-compose restart order-service

# 전체 중지
docker-compose down

# 전체 삭제 (데이터 포함)
docker-compose down -v

# 개별 서비스 빌드
docker-compose build order-service
```

## 🐛 트러블슈팅

### Kafka 연결 실패
```bash
# Kafka 상태 확인
docker exec -it kafka kafka-topics --list --bootstrap-server localhost:9092

# 토픽 생성 (자동 생성되지 않는 경우)
docker exec -it kafka kafka-topics --create \
  --bootstrap-server localhost:9092 \
  --topic order-events \
  --partitions 3 \
  --replication-factor 1
```

### DB 연결 실패
```bash
# DB 컨테이너 로그 확인
docker-compose logs postgres-order

# DB 재시작
docker-compose restart postgres-order postgres-payment
```

### 서비스 빌드 실패
```bash
# 캐시 없이 재빌드
docker-compose build --no-cache order-service
```

## 📈 성능 테스트

```bash
# Apache Bench로 부하 테스트
ab -n 1000 -c 10 -T 'application/json' \
  -p test-order.json \
  http://localhost:8080/api/orders
```

## 🔒 보안 주의사항

- `.env` 파일은 절대 Git에 커밋하지 마세요
- 프로덕션에서는 반드시 `.env`의 비밀번호를 변경하세요
- DB 외부 포트(5432, 5433)는 프로덕션에서 비활성화하세요

## 📚 자세한 문서

- [전체 README](README.md)
- [아키텍처 결정 기록](docs/ADR.md)
- [성능 최적화 가이드](docs/PERFORMANCE.md)
- [Debezium 설정 가이드](docs/DEBEZIUM_SETUP.md)
