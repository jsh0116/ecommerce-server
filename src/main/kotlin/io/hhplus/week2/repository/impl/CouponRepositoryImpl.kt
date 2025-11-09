package io.hhplus.week2.repository.impl

import io.hhplus.week2.domain.Coupon
import io.hhplus.week2.domain.UserCoupon
import io.hhplus.week2.repository.CouponRepository
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

/**
 * CouponRepository의 실제 구현체 (DB 연동 필요)
 */
@Repository
@Primary
class CouponRepositoryImpl : CouponRepository {

    private val coupons = ConcurrentHashMap<String, Coupon>()
    private val userCoupons = ConcurrentHashMap<String, UserCoupon>()

    override fun findById(id: String): Coupon? {
        return coupons[id]
    }

    override fun findUserCoupon(userId: String, couponId: String): UserCoupon? {
        return userCoupons.values.find { it.userId == userId && it.couponId == couponId }
    }

    override fun findUserCouponByCouponId(userId: String, couponId: String): UserCoupon? {
        return userCoupons.values.find { it.userId == userId && it.couponId == couponId }
    }

    override fun findUserCoupons(userId: String): List<UserCoupon> {
        return userCoupons.values.filter { it.userId == userId }
    }

    override fun save(coupon: Coupon) {
        coupons[coupon.id] = coupon
    }

    override fun saveUserCoupon(userCoupon: UserCoupon) {
        userCoupons["${userCoupon.userId}:${userCoupon.couponId}"] = userCoupon
    }
}
