#!/bin/bash

# 이벤트 기반 주문-결제 시스템 빠른 시작 스크립트

echo "🚀 이벤트 기반 주문-결제 시스템 시작 중..."
echo ""

# Docker가 실행 중인지 확인
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker가 실행 중이지 않습니다. Docker Desktop을 시작해주세요."
    exit 1
fi

echo "✅ Docker 확인 완료"
echo ""

# 기존 컨테이너 정리 (선택사항)
read -p "기존 컨테이너를 정리하시겠습니까? (y/N): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "🧹 기존 컨테이너 정리 중..."
    docker-compose down -v
    echo ""
fi

# Docker Compose로 모든 서비스 시작
echo "📦 모든 서비스 빌드 및 시작 중... (초기 실행 시 5-10분 소요)"
echo ""
docker-compose up --build -d

echo ""
echo "⏳ 서비스가 준비될 때까지 대기 중... (약 30초)"
sleep 30

echo ""
echo "============================================"
echo "✨ 모든 서비스가 시작되었습니다!"
echo "============================================"
echo ""
echo "📊 Admin Dashboard: http://localhost:3000"
echo "🛒 Order Service API: http://localhost:8080"
echo "💳 Payment Service API: http://localhost:8081"
echo ""
echo "📝 로그 확인: docker-compose logs -f"
echo "🛑 서비스 중지: docker-compose down"
echo ""
echo "============================================"
echo ""

# 브라우저로 대시보드 열기 (macOS)
if [[ "$OSTYPE" == "darwin"* ]]; then
    echo "🌐 브라우저에서 대시보드를 여는 중..."
    open http://localhost:3000
fi

echo "✅ 준비 완료! Happy Coding! 🚀"
