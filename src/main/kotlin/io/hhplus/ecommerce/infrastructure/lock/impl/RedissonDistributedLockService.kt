package io.hhplus.ecommerce.infrastructure.lock.impl

import io.hhplus.ecommerce.infrastructure.lock.DistributedLockService
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory

/**
 * Redis/Redisson 기반 분산 락 구현
 *
 * 여러 서버 인스턴스에 걸쳐 안전한 동시성 제어를 제공합니다.
 * - Lock 획득 시 Redis의 원자성을 보장
 * - 락 타임아웃으로 데드락 방지
 * - 현재 스레드가 락을 보유한 경우만 해제 가능
 *
 * 특징:
 * - Fair Lock: 다중 스레드 환경에서 요청 순서 보장
 * - Atomic Operations: Redis SET NX + TTL로 원자성 보장
 * - Auto-Release: TTL 기반 자동 해제
 */
@Component
class RedissonDistributedLockService(
    private val redissonClient: RedissonClient
) : DistributedLockService {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun tryLock(key: String, waitTime: Long, holdTime: Long, unit: TimeUnit): Boolean {
        return try {
            val lock = redissonClient.getLock(key)
            val acquired = lock.tryLock(waitTime, holdTime, unit)
            logger.debug("Lock acquisition: key={}, acquired={}, thread={}", key, acquired, Thread.currentThread().name)
            acquired
        } catch (e: Exception) {
            logger.warn("Lock acquisition failed: key={}, error={}", key, e.message)
            false
        }
    }

    override fun unlock(key: String) {
        return try {
            val lock = redissonClient.getLock(key)
            if (lock.isHeldByCurrentThread) {
                lock.unlock()
                logger.debug("Lock released: key={}, thread={}", key, Thread.currentThread().name)
            } else {
                logger.warn("Lock not held by current thread: key={}, thread={}", key, Thread.currentThread().name)
            }
        } catch (e: Exception) {
            logger.warn("Lock release failed: key={}, error={}", key, e.message)
        }
    }

    override fun isHeldByCurrentThread(key: String): Boolean {
        return try {
            val lock = redissonClient.getLock(key)
            lock.isHeldByCurrentThread
        } catch (e: Exception) {
            logger.warn("Failed to check lock status: key={}, error={}", key, e.message)
            false
        }
    }
}
