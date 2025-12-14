package io.hhplus.ecommerce.infrastructure.repositories

import io.hhplus.ecommerce.domain.Coupon
import io.hhplus.ecommerce.domain.UserCoupon

/**
 * 쿠폰 저장소 인터페이스
 */
interface CouponRepository {
    /**
     * 쿠폰 단건 조회
     */
    fun findById(id: Long): Coupon?

    /**
     * 쿠폰 코드로 조회
     */
    fun findByCode(code: String): Coupon?

    /**
     * 특정 사용자의 쿠폰 단건 조회
     */
    fun findUserCoupon(userId: Long, couponId: Long): UserCoupon?

    /**
     * 특정 사용자의 특정 쿠폰 발급 여부 조회
     */
    fun findUserCouponByCouponId(userId: Long, couponId: Long): UserCoupon?

    /**
     * 사용자의 모든 쿠폰 조회
     */
    fun findUserCoupons(userId: Long): List<UserCoupon>

    /**
     * 쿠폰 저장
     */
    fun save(coupon: Coupon)

    /**
     * 사용자 쿠폰 저장
     */
    fun saveUserCoupon(userCoupon: UserCoupon)
}