#!/bin/bash

# 시스템 상태 확인 스크립트

echo "🔍 시스템 상태 확인 중..."
echo ""

# Docker 실행 확인
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker가 실행 중이지 않습니다"
    exit 1
fi
echo "✅ Docker 실행 중"

# 컨테이너 상태 확인
echo ""
echo "📦 컨테이너 상태:"
docker-compose ps

# 서비스 Health Check
echo ""
echo "🏥 서비스 Health Check:"

check_service() {
    local name=$1
    local url=$2
    
    if curl -s -f "$url" > /dev/null 2>&1; then
        echo "✅ $name: 정상"
    else
        echo "❌ $name: 응답 없음"
    fi
}

# 기다림
sleep 2

check_service "Order Service   " "http://localhost:8080/actuator/health"
check_service "Payment Service " "http://localhost:8081/actuator/health"
check_service "Kafka UI        " "http://localhost:8090"
check_service "Debezium        " "http://localhost:8083"

# Kafka 토픽 확인
echo ""
echo "📬 Kafka 토픽:"
docker exec -it kafka kafka-topics --list --bootstrap-server localhost:9092 2>/dev/null || echo "❌ Kafka 접근 불가"

# DB 연결 확인
echo ""
echo "🗄️  DB 연결:"
docker exec postgres-order psql -U postgres -d orderdb -c "SELECT 1" > /dev/null 2>&1 && echo "✅ Order DB 연결 정상" || echo "❌ Order DB 연결 실패"
docker exec postgres-payment psql -U postgres -d paymentdb -c "SELECT 1" > /dev/null 2>&1 && echo "✅ Payment DB 연결 정상" || echo "❌ Payment DB 연결 실패"

echo ""
echo "🔗 접속 URL:"
echo "- Order Service: http://localhost:8080"
echo "- Payment Service: http://localhost:8081"
echo "- Admin Dashboard: http://localhost:3000"
echo "- Kafka UI: http://localhost:8090"
echo "- Debezium: http://localhost:8083"
