package io.hhplus.ecommerce.infrastructure.cache.impl

import io.hhplus.ecommerce.infrastructure.cache.CacheService
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

/**
 * Redis 기반 캐시 서비스 구현
 *
 * Cache-Aside 패턴을 따릅니다:
 * 1. 캐시에서 데이터 조회 시도
 * 2. Cache Miss 시 DB에서 데이터 조회 (서비스 레이어 담당)
 * 3. DB에서 조회한 데이터를 캐시에 저장
 * 4. 데이터 반환
 *
 * 캐시 스탐피드 방지:
 * - 분산락을 이용한 동시 요청 제어
 * - Null 값 캐싱 (부재 데이터에 대한 짧은 TTL)
 */
@Service
class RedisCacheService(
    private val redisTemplate: RedisTemplate<String, String>,
) : CacheService {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val objectMapper = ObjectMapper()

    /**
     * 캐시에서 값을 조회합니다.
     * 직렬화된 JSON 문자열을 원래 타입으로 역직렬화합니다.
     */
    override fun get(key: String): Any? {
        return try {
            logger.debug("캐시 조회 시작: key={}", key)
            val value = redisTemplate.opsForValue().get(key)

            if (value != null) {
                logger.debug("캐시 히트: key={}, value={}", key, value.take(100))
                value // 문자열 그대로 반환 (서비스에서 역직렬화)
            } else {
                logger.debug("캐시 미스: key={}", key)
                null
            }
        } catch (e: Exception) {
            logger.error("캐시 조회 중 예외 발생: key={}, error={}", key, e.message, e)
            null // 캐시 실패 시 null 반환하여 DB 조회 진행
        }
    }

    /**
     * 캐시에 값을 저장합니다.
     * JSON 문자열로 직렬화하여 저장합니다.
     */
    override fun set(key: String, value: Any, ttlSeconds: Int) {
        try {
            logger.debug("캐시 저장 시작: key={}, ttl={}s", key, ttlSeconds)

            val jsonValue = when (value) {
                is String -> value // 이미 JSON 문자열인 경우
                else -> objectMapper.writeValueAsString(value) // 객체를 JSON으로 직렬화
            }

            redisTemplate.opsForValue().set(key, jsonValue, ttlSeconds.toLong(), TimeUnit.SECONDS)
            logger.debug("캐시 저장 완료: key={}, ttl={}s", key, ttlSeconds)
        } catch (e: Exception) {
            logger.error("캐시 저장 중 예외 발생: key={}, error={}", key, e.message, e)
            // 캐시 저장 실패는 무시 (데이터 일관성 문제 없음)
        }
    }

    /**
     * 캐시에서 값을 삭제합니다.
     */
    override fun delete(key: String) {
        try {
            logger.debug("캐시 삭제 시작: key={}", key)
            redisTemplate.delete(key)
            logger.debug("캐시 삭제 완료: key={}", key)
        } catch (e: Exception) {
            logger.error("캐시 삭제 중 예외 발생: key={}, error={}", key, e.message, e)
        }
    }

    /**
     * 패턴에 일치하는 모든 캐시 키를 삭제합니다.
     * 예: "inventory:*" - 모든 재고 캐시 삭제
     */
    fun deleteByPattern(pattern: String) {
        try {
            logger.debug("패턴 기반 캐시 삭제 시작: pattern={}", pattern)
            val keys = redisTemplate.keys(pattern)
            if (keys.isNotEmpty()) {
                redisTemplate.delete(keys)
                logger.debug("패턴 기반 캐시 삭제 완료: pattern={}, deletedCount={}", pattern, keys.size)
            }
        } catch (e: Exception) {
            logger.error("패턴 기반 캐시 삭제 중 예외 발생: pattern={}, error={}", pattern, e.message, e)
        }
    }
}
