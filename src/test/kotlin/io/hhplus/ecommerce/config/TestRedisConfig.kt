package io.hhplus.ecommerce.config

import org.redisson.api.RedissonClient
import org.redisson.spring.data.connection.RedissonConnectionFactory
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.connection.RedisConnectionFactory

/**
 * 테스트 환경용 Redis 연결 설정
 *
 * 실제 Redis를 사용하여 캐싱 동작을 검증합니다.
 * 기존 Redisson 의존성을 재사용하여 RedisConnectionFactory를 제공합니다.
 */
@TestConfiguration
class TestRedisConfig {

    @Bean
    @Primary
    fun testRedisConnectionFactory(redissonClient: RedissonClient): RedisConnectionFactory {
        // Redisson 기반 ConnectionFactory 사용 (Lettuce 의존성 불필요)
        return RedissonConnectionFactory(redissonClient)
    }
}
