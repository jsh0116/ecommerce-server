package io.hhplus.ecommerce.domain

import io.hhplus.ecommerce.exception.CouponException
import java.time.LocalDateTime

/**
 * 쿠폰 유형 열거형
 */
enum class CouponType {
    FIXED_AMOUNT,
    PERCENTAGE,
    FREE_SHIPPING
}

/**
 * 쿠폰 도메인 모델
 */
data class Coupon(
    val id: Long,
    val code: String = "",
    val name: String,
    val type: CouponType = CouponType.FIXED_AMOUNT,
    val discount: Long = 0L,
    val discountRate: Int,
    val minOrderAmount: Long = 0L,
    val maxDiscountAmount: Long? = null,
    val totalQuantity: Int,
    var issuedQuantity: Int,
    val startDate: LocalDateTime,
    val endDate: LocalDateTime,
    val validFrom: String = "",
    val validUntil: String = "",
    val isActive: Boolean = true
) {
    /**
     * 발급 가능 여부
     */
    fun canIssue(): Boolean {
        val now = LocalDateTime.now()
        return issuedQuantity < totalQuantity &&
                now.isAfter(startDate) && now.isBefore(endDate)
    }

    /**
     * 쿠폰 발급
     */
    fun issue(): Int {
        if (!canIssue()) throw CouponException.CannotIssueCoupon()
        issuedQuantity++
        return totalQuantity - issuedQuantity
    }
}

/**
 * 사용자 쿠폰 엔티티
 */
data class UserCoupon(
    val userId: Long,
    val couponId: Long,
    val couponName: String,
    val discountRate: Int,
    var status: String = "AVAILABLE",
    val issuedAt: LocalDateTime = LocalDateTime.now(),
    var usedAt: LocalDateTime? = null,
    val expiresAt: LocalDateTime = LocalDateTime.now().plusDays(7)
) {
    /**
     * 쿠폰 유효성 확인
     */
    fun isValid(): Boolean {
        return "AVAILABLE" == status &&
                (expiresAt == null || LocalDateTime.now().isBefore(expiresAt))
    }

    /**
     * 쿠폰 사용
     */
    fun use() {
        if (!isValid()) throw CouponException.CannotUseCoupon()
        status = "USED"
        usedAt = LocalDateTime.now()
    }

    /**
     * 쿠폰 만료
     */
    fun expire() {
        status = "EXPIRED"
    }
}

/**
 * 쿠폰 검증 결과
 */
data class CouponValidationResult(
    val valid: Boolean,
    val coupon: Coupon? = null,
    val discount: Long = 0L,
    val message: String = "",
    val details: Map<String, Any>? = null
)