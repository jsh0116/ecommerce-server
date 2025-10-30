package io.hhplus.week2.domain

/**
 * 쿠폰 도메인 모델
 */
data class Coupon(
    val id: String,
    val code: String,
    val name: String,
    val type: CouponType,
    val discount: Long,
    val minOrderAmount: Long,
    val maxDiscountAmount: Long?,
    val validFrom: String,
    val validUntil: String,
    val maxPerUser: Int,
    val isActive: Boolean
)

enum class CouponType {
    FIXED_AMOUNT,    // 정액
    PERCENTAGE,      // 정률
    FREE_SHIPPING    // 배송비 무료
}

/**
 * 쿠폰 검증 결과
 */
data class CouponValidationResult(
    val valid: Boolean,
    val coupon: Coupon?,
    val discount: Long = 0,
    val message: String,
    val details: Map<String, Any>? = null
)