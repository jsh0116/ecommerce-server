package io.hhplus.ecommerce.config

import org.springframework.boot.test.context.TestConfiguration

/**
 * TestContainers 설정
 *
 * 통합 테스트를 위한 Redis/MySQL 설정
 *
 * 사용 방법:
 * 1. docker-compose up -d redis mysql (로컬)
 * 2. 테스트 실행
 *
 * GitHub Actions:
 * - services로 Redis, MySQL 제공
 */
@TestConfiguration
class TestContainersConfig
