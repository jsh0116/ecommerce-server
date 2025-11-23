package io.hhplus.ecommerce.application.usecases

import io.hhplus.ecommerce.domain.CouponValidationResult
import io.hhplus.ecommerce.domain.UserCoupon
import io.hhplus.ecommerce.exception.*
import io.hhplus.ecommerce.infrastructure.repositories.CouponRepository
import io.hhplus.ecommerce.infrastructure.repositories.UserRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

@Service
class CouponUseCase(
    private val couponRepository: CouponRepository,
    private val userRepository: UserRepository
) {
    private val couponLocks = ConcurrentHashMap<Long, Any>()

    fun issueCoupon(couponId: Long, userId: Long): CouponIssueResult {
        val lockObject = couponLocks.computeIfAbsent(couponId) { Any() }

        synchronized(lockObject) {
            val user = userRepository.findById(userId)
                ?: throw UserException.UserNotFound(userId.toString())

            val existing = couponRepository.findUserCouponByCouponId(userId, couponId)
            if (existing != null) throw CouponException.AlreadyIssuedCoupon()

            val coupon = couponRepository.findById(couponId)
                ?: throw CouponException.CouponNotFound(couponId.toString())

            if (!coupon.canIssue()) throw CouponException.CouponExhausted()

            val remainingQuantity = coupon.issue()
            couponRepository.save(coupon)

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

            couponRepository.saveUserCoupon(userCoupon)

            return CouponIssueResult(
                userCouponId = couponId,
                couponName = userCoupon.couponName,
                discountRate = userCoupon.discountRate,
                expiresAt = userCoupon.expiresAt,
                remainingQuantity = remainingQuantity
            )
        }
    }

    fun getUserCoupons(userId: Long): List<UserCoupon> {
        val coupons = couponRepository.findUserCoupons(userId)
        val now = LocalDateTime.now()

        for (coupon in coupons) {
            if ("AVAILABLE" == coupon.status &&
                coupon.expiresAt != null &&
                now.isAfter(coupon.expiresAt)
            ) {
                coupon.expire()
                couponRepository.saveUserCoupon(coupon)
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
