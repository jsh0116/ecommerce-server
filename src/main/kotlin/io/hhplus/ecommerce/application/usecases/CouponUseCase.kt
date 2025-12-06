package io.hhplus.ecommerce.application.usecases

import io.hhplus.ecommerce.application.services.CouponIssuanceService
import io.hhplus.ecommerce.application.services.CouponService
import io.hhplus.ecommerce.application.services.UserService
import io.hhplus.ecommerce.domain.CouponValidationResult
import io.hhplus.ecommerce.domain.UserCoupon
import io.hhplus.ecommerce.exception.*
import io.hhplus.ecommerce.infrastructure.lock.DistributedLockService
import org.springframework.stereotype.Service
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

@Service
class CouponUseCase(
    private val couponService: CouponService,
    private val userService: UserService,
    private val distributedLockService: DistributedLockService,
    private val couponIssuanceService: CouponIssuanceService
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    companion object {
        private const val COUPON_LOCK_PREFIX = "coupon:lock:"
    }

    fun issueCoupon(couponId: Long, userId: Long): CouponIssueResult {
        // Redis 사전 체크 (Lock 없이 빠르게 실패)
        try {
            couponIssuanceService.checkIssuanceEligibility(couponId, userId)
        } catch (e: BusinessRuleViolationException) {
            logger.info("쿠폰 발급 사전 체크 실패: couponId={}, userId={}, reason={}", couponId, userId, e.message)
            throw e
        } catch (e: ResourceNotFoundException) {
            logger.info("쿠폰 발급 사전 체크 실패: couponId={}, userId={}, reason={}", couponId, userId, e.message)
            throw e
        }

        val lockKey = "$COUPON_LOCK_PREFIX$couponId"
        val lockAcquired = distributedLockService.tryLock(
            key = lockKey,
            waitTime = 3L,
            holdTime = 10L,
            unit = TimeUnit.SECONDS
        )

        if (!lockAcquired) {
            throw CouponException.CouponLockTimeout("쿠폰 발급 대기 시간 초과")
        }

        try {
            couponIssuanceService.checkIssuanceEligibility(couponId, userId)

            val user = userService.getById(userId)
            val coupon = couponService.getById(couponId)

            if (!coupon.canIssue()) throw CouponException.CouponExhausted()

            val remainingQuantity = coupon.issue()
            couponService.save(coupon)

            val userCoupon = UserCoupon(
                userId = userId,
                couponId = coupon.id,
                couponName = coupon.name,
                discountRate = coupon.discountRate,
                status = "AVAILABLE",
                issuedAt = LocalDateTime.now(),
                usedAt = null,
                expiresAt = LocalDateTime.now().plusDays(7)
            )

            couponService.saveUserCoupon(userCoupon)

            // STEP 14: Redis에 발급 기록 (DB 저장 성공 후)
            val remaining = couponIssuanceService.recordIssuance(couponId, userId)

            logger.info("쿠폰 발급 성공: couponId={}, userId={}, remaining={}", couponId, userId, remaining)

            return CouponIssueResult(
                userCouponId = couponId,
                couponName = userCoupon.couponName,
                discountRate = userCoupon.discountRate,
                expiresAt = userCoupon.expiresAt,
                remainingQuantity = remaining.toInt()
            )
        } finally {
            // 명시적 락 해제
            distributedLockService.unlock(lockKey)
        }
    }

    fun getUserCoupons(userId: Long): List<UserCoupon> {
        val coupons = couponService.findUserCoupons(userId)
        val now = LocalDateTime.now()

        for (coupon in coupons) {
            if ("AVAILABLE" == coupon.status &&
                coupon.expiresAt != null &&
                now.isAfter(coupon.expiresAt)
            ) {
                coupon.expire()
                couponService.saveUserCoupon(coupon)
            }
        }

        return coupons
    }

    fun validateCoupon(couponCode: String, orderAmount: Long): CouponValidationResult {
        return CouponValidationResult(
            valid = false,
            coupon = null,
            discount = 0L,
            message = "쿠폰 검증 기능이 아직 구현되지 않았습니다"
        )
    }

    data class CouponIssueResult(
        val userCouponId: Long,
        val couponName: String,
        val discountRate: Int,
        val expiresAt: LocalDateTime,
        val remainingQuantity: Int
    )
}
