package io.hhplus.ecommerce.application.services

import io.hhplus.ecommerce.exception.BusinessRuleViolationException
import io.hhplus.ecommerce.exception.CouponException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 쿠폰 실행 컴포넌트
 *
 * 쿠폰 발급 프로세스의 실제 실행 로직을 담당합니다.
 * Single Responsibility: 비즈니스 로직 실행만 집중
 */
@Component
class CouponExecutor(
    private val couponCacheManager: CouponCacheManager
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 쿠폰 발급 실행 (Atomic)
     *
     * INCR의 원자성을 활용하여 정확한 수량 제어를 보장합니다.
     * 1. INCR로 카운트 증가 (원자적)
     * 2. quota 초과 시 롤백 후 예외
     * 3. 성공 시 Set에 userId 추가
     *
     * @param couponId 쿠폰 ID
     * @param userId 사용자 ID
     * @return 발급 후 남은 수량
     * @throws CouponException.CouponExhausted 수량 소진 시
     * @throws CouponException.AlreadyIssuedCoupon 중복 발급 시
     */
    fun executeIssuance(couponId: Long, userId: Long): Long {
        try {
            // 1. 발급 수량 증가 (atomic - 가장 먼저 실행하여 동시성 제어)
            val newCount = couponCacheManager.incrementCount(couponId)

            // 2. quota 체크 - 초과 시 롤백
            val quota = couponCacheManager.getQuota(couponId) ?: 0L
            if (newCount > quota) {
                // 롤백: 카운트 감소
                couponCacheManager.decrementCount(couponId)
                logger.warn("쿠폰 수량 초과로 롤백: couponId={}, newCount={}, quota={}", couponId, newCount, quota)
                throw CouponException.CouponExhausted()
            }

            // 3. Set에 userId 추가 (발급 기록) - add는 원자적으로 중복 체크 및 추가
            val added = couponCacheManager.addUserToIssuedSet(couponId, userId)

            if (!added) {
                // 이미 발급받은 사용자 (Race condition으로 인한 중복 발급 시도)
                // 카운트 롤백
                couponCacheManager.decrementCount(couponId)
                logger.warn("중복 발급으로 롤백: couponId={}, userId={}", couponId, userId)
                throw CouponException.AlreadyIssuedCoupon()
            }

            val remaining = quota - newCount

            logger.info(
                "쿠폰 발급 완료 - couponId: {}, userId: {}, 발급수: {}, 남은수량: {}",
                couponId, userId, newCount, remaining
            )

            return remaining
        } catch (e: BusinessRuleViolationException) {
            logger.warn("쿠폰 발급 실패: couponId={}, userId={}, reason={}", couponId, userId, e.message)
            throw e
        } catch (e: Exception) {
            logger.error("쿠폰 발급 Redis 기록 실패: couponId={}, userId={}, error={}", couponId, userId, e.message)
            throw e
        }
    }
}
