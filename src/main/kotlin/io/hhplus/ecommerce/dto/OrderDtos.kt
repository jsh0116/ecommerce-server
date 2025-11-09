package io.hhplus.ecommerce.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 주문 생성 요청 DTO
 */
@Schema(description = "주문 생성 요청")
data class CreateOrderRequest(
    @Schema(description = "주문 상품 목록")
    val items: List<OrderItemRequest>,

    @Schema(description = "배송지 정보")
    val shippingAddress: ShippingAddressRequest,

    @Schema(description = "배송 방법: standard, express, dawn")
    val shippingMethod: String,

    @Schema(description = "쿠폰 코드 (선택)")
    val couponCode: String? = null,

    @Schema(description = "사용 포인트 (선택)")
    val pointsToUse: Long = 0,

    @Schema(description = "구매 약관 동의")
    val agreeToTerms: Boolean,

    @Schema(description = "배송 요청사항 (선택)")
    val requestMessage: String? = null
)

/**
 * 주문 항목 요청 DTO
 */
@Schema(description = "주문 항목")
data class OrderItemRequest(
    @Schema(description = "변량 ID")
    val variantId: String,

    @Schema(description = "수량 (1-99)")
    val quantity: Int
)

/**
 * 배송지 요청 DTO
 */
@Schema(description = "배송지 정보")
data class ShippingAddressRequest(
    @Schema(description = "수령인 이름")
    val name: String,

    @Schema(description = "수령인 전화번호")
    val phone: String,

    @Schema(description = "도로명 주소")
    val address: String,

    @Schema(description = "상세 주소")
    val addressDetail: String,

    @Schema(description = "우편번호")
    val zipCode: String
)

/**
 * 주문 생성 응답 DTO
 */
@Schema(description = "주문 생성 응답")
data class CreateOrderResponse(
    @Schema(description = "주문 ID")
    val id: String,

    @Schema(description = "주문번호")
    val orderNumber: String,

    @Schema(description = "주문 상태")
    val status: String,

    @Schema(description = "재고 예약 만료 시간")
    val reservationExpiry: String,

    @Schema(description = "주문 항목 목록")
    val items: List<OrderItemResponse>,

    @Schema(description = "결제 정보")
    val payment: PaymentResponse,

    @Schema(description = "생성 시간")
    val createdAt: String
)

/**
 * 주문 항목 응답 DTO
 */
@Schema(description = "주문 항목 정보")
data class OrderItemResponse(
    @Schema(description = "항목 ID")
    val id: String,

    @Schema(description = "상품명")
    val productName: String,

    @Schema(description = "변량 정보 (색상, 사이즈 등)")
    val variant: VariantInfo,

    @Schema(description = "수량")
    val quantity: Int,

    @Schema(description = "단가 (원)")
    val price: Long,

    @Schema(description = "소계 (원)")
    val subtotal: Long
)

/**
 * 변량 정보 응답 DTO
 */
@Schema(description = "변량 정보")
data class VariantInfo(
    @Schema(description = "변량 ID")
    val id: String,

    @Schema(description = "SKU 코드")
    val sku: String,

    @Schema(description = "색상")
    val color: String,

    @Schema(description = "사이즈")
    val size: String
)

/**
 * 결제 정보 응답 DTO
 */
@Schema(description = "결제 정보")
data class PaymentResponse(
    @Schema(description = "결제 금액 (원)")
    val amount: Long,

    @Schema(description = "결제 내역 분석")
    val breakdown: PaymentBreakdownResponse
)

/**
 * 결제 내역 분석 DTO
 */
@Schema(description = "결제 내역 분석")
data class PaymentBreakdownResponse(
    @Schema(description = "상품 금액 (원)")
    val subtotal: Long,

    @Schema(description = "할인 금액 (원)")
    val discount: Long,

    @Schema(description = "사용 포인트 (원)")
    val pointsUsed: Long,

    @Schema(description = "배송비 (원)")
    val shipping: Long,

    @Schema(description = "총액 (원)")
    val total: Long
)

/**
 * 주문 상세 조회 응답 DTO
 */
@Schema(description = "주문 상세 정보")
data class OrderDetailResponse(
    @Schema(description = "주문 ID")
    val id: String,

    @Schema(description = "주문번호")
    val orderNumber: String,

    @Schema(description = "주문 상태")
    val status: String,

    @Schema(description = "주문 항목 목록")
    val items: List<OrderItemResponse>,

    @Schema(description = "결제 정보")
    val payment: PaymentResponse,

    @Schema(description = "배송 정보")
    val shipping: ShippingResponse,

    @Schema(description = "생성 시간")
    val createdAt: String,

    @Schema(description = "수정 시간")
    val updatedAt: String
)

/**
 * 배송 정보 응답 DTO
 */
@Schema(description = "배송 정보")
data class ShippingResponse(
    @Schema(description = "배송 주소")
    val address: ShippingAddressRequest,

    @Schema(description = "배송 방법")
    val method: String,

    @Schema(description = "배송비 (원)")
    val fee: Long,

    @Schema(description = "송장번호")
    val trackingNumber: String? = null,

    @Schema(description = "배송사")
    val carrier: String? = null,

    @Schema(description = "예상 배송일")
    val estimatedDelivery: String? = null
)

/**
 * 주문 취소 요청 DTO
 */
@Schema(description = "주문 취소 요청")
data class CancelOrderRequest(
    @Schema(description = "취소 사유")
    val reason: String,

    @Schema(description = "상세 취소 사유")
    val detailReason: String? = null
)

/**
 * 주문 취소 응답 DTO
 */
@Schema(description = "주문 취소 응답")
data class CancelOrderResponse(
    @Schema(description = "응답 메시지")
    val message: String,

    @Schema(description = "환불 금액 (원)")
    val refundAmount: Long,

    @Schema(description = "환불 예상 시간")
    val estimatedRefundDate: String
)