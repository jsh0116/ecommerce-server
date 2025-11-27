package io.hhplus.ecommerce.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.StringRedisSerializer

/**
 * Redis 설정
 *
 * String 기반 Key-Value 저장을 위해 RedisTemplate을 설정합니다.
 * - Key: String
 * - Value: String (JSON 형태)
 *
 * 이를 통해 캐시 데이터의 직렬화/역직렬화를 명시적으로 제어합니다.
 */
@Configuration
class RedisConfig {

    /**
     * RedisTemplate 설정
     *
     * String 기반 캐시를 위해 직렬화 방식을 StringRedisSerializer로 설정합니다.
     */
    @Bean
    fun redisTemplate(connectionFactory: RedisConnectionFactory): RedisTemplate<String, String> {
        return RedisTemplate<String, String>().apply {
            setConnectionFactory(connectionFactory)

            // Key 직렬화
            val stringSerializer = StringRedisSerializer()
            keySerializer = stringSerializer
            hashKeySerializer = stringSerializer

            // Value 직렬화 (String JSON)
            valueSerializer = stringSerializer
            hashValueSerializer = stringSerializer

            afterPropertiesSet()
        }
    }
}
