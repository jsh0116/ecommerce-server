package io.hhplus.ecommerce.infrastructure.cache.impl

import io.hhplus.ecommerce.infrastructure.cache.CacheService
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * 캐시 서비스 Mock 구현
 */
@Service
class MockCacheService : CacheService {

    private val cache = ConcurrentHashMap<String, Any>()

    override fun get(key: String): Any? {
        return cache[key]
    }

    override fun set(key: String, value: Any, ttlSeconds: Int) {
        cache[key] = value
    }

    override fun delete(key: String) {
        cache.remove(key)
    }
}