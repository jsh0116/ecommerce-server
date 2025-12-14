package io.hhplus.ecommerce.application.services

import io.hhplus.ecommerce.exception.CouponException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 쿠폰 검증 컴포넌트
 *
 * 쿠폰 발급 프로세스의 모든 검증 로직을 담당합니다.
 * Single Responsibility: 검증 로직만 집중
 */
@Component
class CouponValidator(
    private val couponCacheManager: CouponCacheManager
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 중복 발급 검증
     *
     * @param couponId 쿠폰 ID
     * @param userId 사용자 ID
     * @throws CouponException.AlreadyIssuedCoupon 이미 발급받은 경우
     */
    fun validateNotAlreadyIssued(couponId: Long, userId: Long) {
        val alreadyIssued = couponCacheManager.hasUserIssued(couponId, userId)
        if (alreadyIssued) {
            logger.warn("중복 발급 시도: couponId={}, userId={}", couponId, userId)
            throw CouponException.AlreadyIssuedCoupon()
        }
    }

    /**
     * 수량 소진 검증
     *
     * @param couponId 쿠폰 ID
     * @throws CouponException.CouponNotFound 쿠폰이 존재하지 않는 경우
     * @throws CouponException.CouponExhausted 수량이 소진된 경우
     */
    fun validateQuotaAvailable(couponId: Long) {
        val quota = couponCacheManager.getQuota(couponId)
            ?: throw CouponException.CouponNotFound(couponId.toString())

        val currentCount = couponCacheManager.getCurrentCount(couponId)

        if (currentCount >= quota) {
            logger.warn("쿠폰 수량 소진: couponId={}, quota={}, currentCount={}", couponId, quota, currentCount)
            throw CouponException.CouponExhausted()
        }
    }

    /**
     * 쿠폰 발급 가능 여부 전체 검증 (편의 메서드)
     *
     * @param couponId 쿠폰 ID
     * @param userId 사용자 ID
     */
    fun validateIssuanceEligibility(couponId: Long, userId: Long) {
        validateNotAlreadyIssued(couponId, userId)
        validateQuotaAvailable(couponId)
    }
}
