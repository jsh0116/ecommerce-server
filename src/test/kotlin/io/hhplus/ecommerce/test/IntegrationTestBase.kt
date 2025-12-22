package io.hhplus.ecommerce.test

import io.hhplus.ecommerce.config.TestContainersConfig
import io.hhplus.ecommerce.config.TestRedissonConfig
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles

/**
 * 통합 테스트 기본 클래스
 *
 * TestContainers를 사용하여 MySQL과 Redis를 Docker 컨테이너로 실행합니다.
 * 모든 통합 테스트는 이 클래스를 상속받아야 합니다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ActiveProfiles("test")
@Import(TestContainersConfig::class, TestRedissonConfig::class)
abstract class IntegrationTestBase