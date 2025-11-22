package io.hhplus.ecommerce.config

import io.hhplus.ecommerce.application.services.CouponLockService
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

/**
 * 테스트 환경에서 사용할 메모리 기반 쿠폰 락 서비스
 *
 * Redis 없이도 동작하며, 테스트의 동시성을 검증하기 위해
 * JVM 내부의 ReentrantLock을 사용합니다. (테스트 전용)
 */
@TestConfiguration
class TestCouponLockServiceConfig {
    @Bean
    @Primary
    fun couponLockService(): CouponLockService {
        return InMemoryCouponLockService()
    }

    /**
     * 메모리 기반 쿠폰 락 구현 (테스트용)
     */
    private class InMemoryCouponLockService : CouponLockService {
        private val locks = mutableMapOf<Long, ReentrantLock>()

        private fun getLock(couponId: Long): ReentrantLock {
            return locks.computeIfAbsent(couponId) { ReentrantLock() }
        }

        override fun tryLock(couponId: Long, waitTime: Long, holdTime: Long, unit: TimeUnit): Boolean {
            val lock = getLock(couponId)
            return try {
                lock.tryLock(waitTime, unit)
            } catch (e: InterruptedException) {
                false
            }
        }

        override fun unlock(couponId: Long) {
            val lock = getLock(couponId)
            if (lock.isHeldByCurrentThread) {
                lock.unlock()
            }
        }

        override fun isHeldByCurrentThread(couponId: Long): Boolean {
            val lock = getLock(couponId)
            return lock.isHeldByCurrentThread
        }
    }
}