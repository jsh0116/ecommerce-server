package io.hhplus.ecommerce.integration

import org.springframework.test.context.ActiveProfiles

/**
 * 통합 테스트 베이스 설정
 *
 * Docker/TestContainer 필요 시:
 * 1. Docker Desktop 실행 확인
 * 2. @Testcontainers 애너테이션 추가
 * 3. @Container companion object 설정
 *
 * 현재: H2 메모리 DB 또는 실제 MySQL 사용
 * (application-test.yml에서 datasource 설정)
 *
 * 주의: @SpringBootTest와 @DirtiesContext는 각 구현 클래스에서 추가해야 합니다
 * (여러 @SpringBootTest가 있을 때 Bean 충돌을 방지하기 위함)
 */
@ActiveProfiles("test")
abstract class IntegrationTestBase
