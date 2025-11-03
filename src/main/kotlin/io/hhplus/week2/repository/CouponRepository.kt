package io.hhplus.week2.repository

import io.hhplus.week2.domain.Coupon

/**
 * 쿠폰 관련 저장소 인터페이스
 */
interface CouponRepository {
    /**
     * 쿠폰 코드로 쿠폰을 조회합니다.
     *
     * @param code 쿠폰 코드
     * @return 쿠폰 또는 null
     */
    fun findByCode(code: String): Coupon?

    /**
     * 쿠폰을 저장합니다.
     *
     * @param coupon 쿠폰
     */
    fun save(coupon: Coupon)

    /**
     * 쿠폰을 업데이트합니다.
     *
     * @param coupon 업데이트될 쿠폰
     */
    fun update(coupon: Coupon)
}