package io.hhplus.ecommerce.config

import io.mockk.mockk
import org.redisson.api.RedissonClient
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

/**
 * 테스트 환경에서 Redisson Mock 제공
 *
 * Redis 연결이 필요 없는 단위 테스트, 통합 테스트에서
 * Mock RedissonClient를 주입받도록 설정합니다.
 * (relaxed = true로 설정하여 모든 메서드 호출을 허용)
 */
@TestConfiguration
class TestRedissonConfig {
    @Bean
    @Primary
    fun redissonClient(): RedissonClient {
        return mockk(relaxed = true)
    }
}