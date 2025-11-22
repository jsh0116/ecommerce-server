package io.hhplus.ecommerce.application.services.impl

import io.hhplus.ecommerce.application.services.CouponLockService
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

/**
 * Redisson 기반 쿠폰 분산 락 구현
 *
 * Redis를 이용하여 멀티 서버 환경에서 안전한 동시성 제어를 제공합니다.
 */
@Service
class RedissonCouponLockService(
    private val redissonClient: RedissonClient
) : CouponLockService {
    companion object {
        private const val LOCK_KEY_PREFIX = "coupon:lock:"
    }

    override fun tryLock(couponId: Long, waitTime: Long, holdTime: Long, unit: TimeUnit): Boolean {
        val lockKey = "$LOCK_KEY_PREFIX$couponId"
        val lock = redissonClient.getLock(lockKey)
        return lock.tryLock(waitTime, holdTime, unit)
    }

    override fun unlock(couponId: Long) {
        val lockKey = "$LOCK_KEY_PREFIX$couponId"
        val lock = redissonClient.getLock(lockKey)
        if (lock.isHeldByCurrentThread) {
            lock.unlock()
        }
    }

    override fun isHeldByCurrentThread(couponId: Long): Boolean {
        val lockKey = "$LOCK_KEY_PREFIX$couponId"
        val lock = redissonClient.getLock(lockKey)
        return lock.isHeldByCurrentThread
    }
}