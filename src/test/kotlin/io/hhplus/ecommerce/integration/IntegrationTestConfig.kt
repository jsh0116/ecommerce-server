package io.hhplus.ecommerce.integration

import org.springframework.test.context.ActiveProfiles

/**
 * 통합 테스트 베이스 설정
 *
 * Redis 통합 테스트를 위해 로컬 Redis가 실행 중이어야 합니다.
 * - Redis 시작: `docker-compose up redis` 또는 `redis-server`
 * - Redis 확인: `redis-cli ping` (응답: PONG)
 *
 * application-test.yml에서 Redis 연결 정보를 설정합니다:
 * - Host: localhost (기본값)
 * - Port: 6379 (기본값)
 */
@ActiveProfiles("test")
abstract class IntegrationTestBase
