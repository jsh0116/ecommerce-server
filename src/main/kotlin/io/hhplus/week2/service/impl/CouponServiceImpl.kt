package io.hhplus.week2.service.impl

import io.hhplus.week2.domain.Coupon
import io.hhplus.week2.domain.CouponType
import io.hhplus.week2.domain.CouponValidationResult
import io.hhplus.week2.repository.CouponRepository
import io.hhplus.week2.service.CouponService
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 쿠폰 서비스 구현체
 */
@Service
class CouponServiceImpl(
    private val couponRepository: CouponRepository
) : CouponService {

    private val dateFormatter = DateTimeFormatter.ISO_DATE_TIME

    override fun getCouponByCode(code: String): Coupon? {
        return couponRepository.findByCode(code)
    }

    override fun validateCoupon(couponCode: String, orderAmount: Long): CouponValidationResult {
        val coupon = couponRepository.findByCode(couponCode)
            ?: return CouponValidationResult(
                valid = false,
                coupon = null,
                message = "유효하지 않은 쿠폰 코드입니다.",
                details = mapOf("code" to couponCode)
            )

        if (!coupon.isActive) {
            return CouponValidationResult(
                valid = false,
                coupon = null,
                message = "비활성화된 쿠폰입니다.",
                details = mapOf("code" to coupon.code)
            )
        }

        val now = LocalDateTime.now()
        val validFrom = LocalDateTime.parse(coupon.validFrom, dateFormatter)
        val validUntil = LocalDateTime.parse(coupon.validUntil, dateFormatter)

        if (now < validFrom || now > validUntil) {
            return CouponValidationResult(
                valid = false,
                coupon = null,
                message = "유효 기간이 만료되었습니다.",
                details = mapOf(
                    "validFrom" to coupon.validFrom,
                    "validUntil" to coupon.validUntil
                )
            )
        }

        if (orderAmount < coupon.minOrderAmount) {
            return CouponValidationResult(
                valid = false,
                coupon = null,
                message = "최소 주문 금액(${coupon.minOrderAmount}원)을 충족하지 못했습니다.",
                details = mapOf(
                    "minOrderAmount" to coupon.minOrderAmount,
                    "currentAmount" to orderAmount
                )
            )
        }

        val discount = when (coupon.type) {
            CouponType.FIXED_AMOUNT -> coupon.discount
            CouponType.PERCENTAGE -> {
                val calculatedDiscount = (orderAmount * coupon.discount) / 100
                coupon.maxDiscountAmount?.let { minOf(calculatedDiscount, it) } ?: calculatedDiscount
            }
            CouponType.FREE_SHIPPING -> coupon.discount
        }

        return CouponValidationResult(
            valid = true,
            coupon = coupon,
            discount = discount,
            message = "${discount}원 할인이 적용됩니다"
        )
    }
}