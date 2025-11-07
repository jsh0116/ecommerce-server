package io.hhplus.week2.infrastructure.cache

/**
 * 캐시 서비스 인터페이스
 */
interface CacheService {
    /**
     * 캐시에서 값을 조회합니다.
     */
    fun get(key: String): Any?

    /**
     * 캐시에 값을 저장합니다.
     */
    fun set(key: String, value: Any, ttlSeconds: Int)

    /**
     * 캐시에서 값을 삭제합니다.
     */
    fun delete(key: String)
}