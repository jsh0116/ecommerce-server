package io.hhplus.ecommerce.config

import org.springframework.boot.test.context.TestConfiguration

/**
 * TestContainers 설정
 *
 * 통합 테스트를 위한 Docker 기반 MySQL과 Redis 컨테이너 제공
 *
 * 동작 방식:
 * - 로컬: docker run 명령으로 MySQL/Redis 컨테이너를 미리 시작한 후 테스트 실행
 * - CI (GitHub Actions): MySQL과 Redis 서비스로 제공됨
 *
 * 로컬 실행 방법:
 * 1. docker run -d --name ecommerce_mysql_test -e MYSQL_ROOT_PASSWORD=root \
 *    -e MYSQL_DATABASE=hhplus_ecommerce_test -p 3306:3306 mysql:8.0
 * 2. docker run -d --name ecommerce_redis_test -p 6379:6379 redis:7-alpine
 * 3. ./gradlew testIntegration
 *
 * 사용 방법:
 * @Import(TestContainersConfig::class)를 테스트 클래스에 추가
 */
@TestConfiguration
class TestContainersConfig

