package io.hhplus.ecommerce.infrastructure.persistence.adapter

import io.hhplus.ecommerce.domain.Coupon
import io.hhplus.ecommerce.domain.UserCoupon
import io.hhplus.ecommerce.infrastructure.repositories.CouponRepository
import org.springframework.stereotype.Repository

/**
 * CouponRepository JPA 어댑터
 */
@Repository
class CouponRepositoryAdapter : CouponRepository {

    override fun findById(id: Long): Coupon? {
        // TODO: 구현 필요
        return null
    }

    override fun save(coupon: Coupon) {
        // TODO: 구현 필요
    }

    override fun findUserCoupon(userId: Long, couponId: Long): UserCoupon? {
        // TODO: 구현 필요
        return null
    }

    override fun saveUserCoupon(userCoupon: UserCoupon) {
        // TODO: 구현 필요
    }

    override fun findUserCouponByCouponId(userId: Long, couponId: Long): UserCoupon? {
        // TODO: 구현 필요
        return null
    }

    override fun findUserCoupons(userId: Long): List<UserCoupon> {
        // TODO: 구현 필요
        return emptyList()
    }
}
