package io.hhplus.ecommerce.presentation.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 쿠폰 검증 요청 DTO
 */
@Schema(description = "쿠폰 검증 요청")
data class ValidateCouponRequest(
    @Schema(description = "쿠폰 코드")
    val couponCode: String,

    @Schema(description = "주문 금액 (원)")
    val orderAmount: Long
)

/**
 * 쿠폰 검증 응답 DTO
 */
@Schema(description = "쿠폰 검증 응답")
data class ValidateCouponResponse(
    @Schema(description = "쿠폰 유효 여부")
    val valid: Boolean,

    @Schema(description = "쿠폰 정보")
    val coupon: CouponInfo?,

    @Schema(description = "할인 금액 (원)")
    val discount: Long,

    @Schema(description = "메시지")
    val message: String,

    @Schema(description = "상세 정보")
    val details: Map<String, Any>? = null
)

/**
 * 쿠폰 정보 DTO
 */
@Schema(description = "쿠폰 정보")
data class CouponInfo(
    @Schema(description = "쿠폰 ID")
    val id: String,

    @Schema(description = "쿠폰 코드")
    val code: String,

    @Schema(description = "쿠폰명")
    val name: String,

    @Schema(description = "쿠폰 유형: FIXED_AMOUNT, PERCENTAGE, FREE_SHIPPING")
    val type: String,

    @Schema(description = "할인액 또는 할인율")
    val discount: Long,

    @Schema(description = "최소 주문 금액 (원)")
    val minOrderAmount: Long,

    @Schema(description = "최대 할인액 (원)")
    val maxDiscountAmount: Long?,

    @Schema(description = "유효 시작일")
    val validFrom: String,

    @Schema(description = "유효 종료일")
    val validUntil: String
)

/**
 * 쿠폰 목록 조회 응답 DTO
 */
@Schema(description = "쿠폰 목록 응답")
data class CouponListResponse(
    @Schema(description = "쿠폰 목록")
    val data: List<CouponInfo>,

    @Schema(description = "페이지네이션 정보")
    val pagination: Map<String, Any>
)