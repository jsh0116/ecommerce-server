package io.hhplus.week2.service

import io.hhplus.week2.domain.Coupon
import io.hhplus.week2.domain.CouponValidationResult

/**
 * 쿠폰 관련 도메인 서비스
 */
interface CouponService {

    /**
     * 쿠폰 코드로 쿠폰 정보를 조회합니다.
     *
     * @param code 쿠폰 코드
     * @return 쿠폰 정보 또는 null
     */
    fun getCouponByCode(code: String): Coupon?

    /**
     * 주어진 주문 금액에 대해 쿠폰을 검증하고 할인 금액을 계산합니다.
     *
     * @param couponCode 쿠폰 코드
     * @param orderAmount 주문 금액
     * @return 쿠폰 검증 결과
     */
    fun validateCoupon(couponCode: String, orderAmount: Long): CouponValidationResult
}
