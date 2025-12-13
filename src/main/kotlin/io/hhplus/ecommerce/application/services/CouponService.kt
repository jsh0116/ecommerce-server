package io.hhplus.ecommerce.application.services

import io.hhplus.ecommerce.domain.Coupon
import io.hhplus.ecommerce.domain.UserCoupon
import io.hhplus.ecommerce.exception.CouponException
import io.hhplus.ecommerce.infrastructure.repositories.CouponRepository
import org.springframework.stereotype.Service

@Service
class CouponService(
    private val couponRepository: CouponRepository
) {
    fun getById(couponId: Long): Coupon {
        return couponRepository.findById(couponId)
            ?: throw CouponException.CouponNotFound(couponId.toString())
    }

    fun validateUserCoupon(userId: Long, couponId: Long, skipUsedCheck: Boolean = false): UserCoupon {
        val userCoupon = couponRepository.findUserCoupon(userId, couponId)
            ?: throw CouponException.CouponNotFound("User coupon not found")

        if (!skipUsedCheck && !userCoupon.isValid()) {
            throw CouponException.InvalidCoupon()
        }

        return userCoupon
    }

    fun useCoupon(userCoupon: UserCoupon): UserCoupon {
        userCoupon.use()
        couponRepository.saveUserCoupon(userCoupon)
        return userCoupon
    }

    fun findUserCoupons(userId: Long): List<UserCoupon> {
        return couponRepository.findUserCoupons(userId)
    }

    fun save(coupon: io.hhplus.ecommerce.domain.Coupon) {
        couponRepository.save(coupon)
    }

    fun saveUserCoupon(userCoupon: UserCoupon) {
        couponRepository.saveUserCoupon(userCoupon)
    }
}
