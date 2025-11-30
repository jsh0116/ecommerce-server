package io.hhplus.ecommerce.config

import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

/**
 * 테스트 환경에서 실제 Redis에 연결하는 Redisson 클라이언트 제공
 *
 * DatabaseIntegrationTest와 CachingIntegrationTest에서
 * 실제 Redis 기반 분산 락과 캐싱을 테스트하기 위해 사용합니다.
 *
 * application-test.yml의 Redis 설정(localhost:6379)을 사용합니다.
 */
@TestConfiguration
class TestRedissonConfig {

    @Bean
    @Primary
    fun redissonClient(): RedissonClient {
        val redisHost = System.getenv("REDIS_HOST") ?: "localhost"
        val redisPort = System.getenv("REDIS_PORT")?.toIntOrNull() ?: 6379

        val config = Config()
        config.useSingleServer()
            .setAddress("redis://$redisHost:$redisPort")
            .setConnectionPoolSize(10)
            .setConnectionMinimumIdleSize(5)
            .setTimeout(3000)
            .setRetryAttempts(3)
            .setRetryInterval(1000)

        return Redisson.create(config)
    }
}