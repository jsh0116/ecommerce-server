package io.hhplus.ecommerce.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 재고 조회 응답 DTO
 */
@Schema(description = "재고 정보")
data class InventoryResponse(
    @Schema(description = "SKU 코드")
    val sku: String,

    @Schema(description = "가용 재고 수량")
    val available: Int,

    @Schema(description = "예약 재고 수량")
    val reserved: Int,

    @Schema(description = "물리적 재고 수량")
    val physical: Int,

    @Schema(description = "안전 재고 수량")
    val safetyStock: Int,

    @Schema(description = "재고 상태: IN_STOCK, LOW_STOCK, OUT_OF_STOCK")
    val status: String,

    @Schema(description = "마지막 업데이트 시간")
    val lastUpdated: String
)

/**
 * 재고 예약 요청 DTO
 */
@Schema(description = "재고 예약 요청")
data class ReserveInventoryRequest(
    @Schema(description = "SKU 코드")
    val sku: String,

    @Schema(description = "예약 수량")
    val quantity: Int
)

/**
 * 재고 예약 응답 DTO
 */
@Schema(description = "재고 예약 응답")
data class ReserveInventoryResponse(
    @Schema(description = "예약 ID")
    val reservationId: String,

    @Schema(description = "SKU 코드")
    val sku: String,

    @Schema(description = "예약 수량")
    val quantity: Int,

    @Schema(description = "예약 만료 시간 (ISO 8601)")
    val expiresAt: String,

    @Schema(description = "예약 성공 여부")
    val success: Boolean
)

/**
 * 재고 차감 요청 DTO
 */
@Schema(description = "재고 차감 요청")
data class DeductInventoryRequest(
    @Schema(description = "SKU 코드")
    val sku: String,

    @Schema(description = "차감 수량")
    val quantity: Int
)

/**
 * 재고 차감 응답 DTO
 */
@Schema(description = "재고 차감 응답")
data class DeductInventoryResponse(
    @Schema(description = "SKU 코드")
    val sku: String,

    @Schema(description = "차감 후 재고 수량")
    val remainingStock: Int,

    @Schema(description = "차감 성공 여부")
    val success: Boolean,

    @Schema(description = "메시지")
    val message: String
)