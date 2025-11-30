package io.hhplus.ecommerce.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer
import java.time.Duration

/**
 * Redis 설정
 *
 * 1. RedisTemplate: 분산 락 등을 위한 기본 Redis 작업
 * 2. RedisCacheManager: Spring Cache 어노테이션(@Cacheable, @CacheEvict)을 위한 캐시 매니저
 */
@Configuration
@EnableCaching
class RedisConfig {

    /**
     * RedisTemplate 설정
     *
     * String 기반 캐시를 위해 직렬화 방식을 StringRedisSerializer로 설정합니다.
     * 주로 분산 락과 같은 단순 Key-Value 작업에 사용됩니다.
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

    /**
     * ObjectMapper 설정
     *
     * Redis 캐시의 JSON 직렬화/역직렬화를 위한 ObjectMapper
     * - Kotlin 데이터 클래스 지원
     * - LocalDateTime 등 Java 8 Time API 지원
     */
    @Bean
    fun redisCacheObjectMapper(): ObjectMapper {
        return ObjectMapper().apply {
            registerKotlinModule()
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }

    /**
     * RedisCacheManager 설정
     *
     * Spring Cache 어노테이션(@Cacheable, @CacheEvict)을 위한 캐시 매니저
     * - 기본 TTL: 60초
     * - JPA Entity 직렬화를 위해 Java Serialization 사용
     */
    @Bean
    fun cacheManager(
        connectionFactory: RedisConnectionFactory
    ): RedisCacheManager {
        // JPA Entity는 Java Serialization이 더 안정적
        val defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofSeconds(60))  // 기본 TTL 60초
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer())
            )
            // Value는 기본 JdkSerializationRedisSerializer 사용 (명시적 설정 불필요)

        // 캐시별 개별 설정
        val cacheConfigurations = mapOf(
            "inventory" to defaultConfig.entryTtl(Duration.ofSeconds(60)),
            "products" to defaultConfig.entryTtl(Duration.ofSeconds(60)),
            "topProducts" to defaultConfig.entryTtl(Duration.ofSeconds(300))  // 인기 상품은 5분
        )

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigurations)
            .build()
    }
}
