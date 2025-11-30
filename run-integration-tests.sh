#!/bin/bash

# 통합 테스트 실행 스크립트
# 로컬에서 Redis와 MySQL을 Docker로 실행하고 테스트를 수행합니다

set -e

echo "🚀 통합 테스트 실행 시작"
echo "================================"

# 1. Redis 및 MySQL 컨테이너 시작
echo "📦 Docker 컨테이너 시작 중..."
docker run -d --name hhplus-redis -p 6379:6379 redis:7-alpine 2>/dev/null || echo "Redis already running"
docker run -d --name hhplus-mysql -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=hhplus_ecommerce_test -p 3306:3306 mysql:8.0 2>/dev/null || echo "MySQL already running"

# 2. 컨테이너가 준비될 때까지 대기
echo "⏳ 서비스 대기 중... (15초)"
sleep 15

# 3. 통합 테스트 실행
echo "🧪 통합 테스트 실행 중..."
./gradlew clean testIntegration \
  -DSPRING_REDIS_HOST=localhost \
  -DSPRING_REDIS_PORT=6379 \
  -DSPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/hhplus_ecommerce_test \
  -DSPRING_DATASOURCE_USERNAME=root \
  -DSPRING_DATASOURCE_PASSWORD=root

# 4. 결과
if [ $? -eq 0 ]; then
  echo ""
  echo "✅ 모든 테스트 통과!"
else
  echo ""
  echo "❌ 테스트 실패"
  exit 1
fi

# 5. 정리 (선택사항)
read -p "Docker 컨테이너를 중지하시겠습니까? (y/n) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
  echo "🧹 정리 중..."
  docker stop hhplus-redis hhplus-mysql 2>/dev/null || true
  docker rm hhplus-redis hhplus-mysql 2>/dev/null || true
  echo "✨ 완료!"
fi
