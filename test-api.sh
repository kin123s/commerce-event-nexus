#!/bin/bash

# 테스트 주문 생성
echo "📝 테스트 주문 생성 중..."

ORDER_RESPONSE=$(curl -s -X POST http://localhost:8080/api/orders \
  -H 'Content-Type: application/json' \
  -d '{
    "productName": "MacBook Pro 16",
    "quantity": 1,
    "price": 2500000,
    "customerName": "김철수",
    "customerEmail": "test@example.com"
  }')

echo "✅ 주문 생성 완료:"
echo "$ORDER_RESPONSE" | jq '.'

# 주문 번호 추출
ORDER_NUMBER=$(echo "$ORDER_RESPONSE" | jq -r '.orderNumber')
echo ""
echo "📌 주문 번호: $ORDER_NUMBER"

# 5초 대기 (Kafka 메시지 처리 시간)
echo ""
echo "⏳ 5초 대기 중 (결제 처리 중)..."
sleep 5

# 주문 조회
echo ""
echo "🔍 주문 상태 조회:"
curl -s http://localhost:8080/api/orders | jq '.[] | select(.orderNumber == "'$ORDER_NUMBER'")'

# 결제 조회
echo ""
echo "💳 결제 내역 조회:"
curl -s http://localhost:8081/api/payments | jq '.[] | select(.orderNumber == "'$ORDER_NUMBER'")'

echo ""
echo "✅ 테스트 완료!"
echo ""
echo "📊 Kafka UI: http://localhost:8090"
echo "🗄️  Debezium: http://localhost:8083"
