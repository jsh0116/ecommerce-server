package io.hhplus.week2.repository

import io.hhplus.week2.domain.Coupon
import io.hhplus.week2.domain.UserCoupon

/**
 * 쿠폰 저장소 인터페이스
 */
interface CouponRepository {
    /**
     * 쿠폰 단건 조회
     */
    fun findById(id: String): Coupon?

    /**
     * 특정 사용자의 쿠폰 단건 조회
     */
    fun findUserCoupon(userId: String, couponId: String): UserCoupon?

    /**
     * 특정 사용자의 특정 쿠폰 발급 여부 조회
     */
    fun findUserCouponByCouponId(userId: String, couponId: String): UserCoupon?

    /**
     * 사용자의 모든 쿠폰 조회
     */
    fun findUserCoupons(userId: String): List<UserCoupon>

    /**
     * 쿠폰 저장
     */
    fun save(coupon: Coupon)

    /**
     * 사용자 쿠폰 저장
     */
    fun saveUserCoupon(userCoupon: UserCoupon)
}