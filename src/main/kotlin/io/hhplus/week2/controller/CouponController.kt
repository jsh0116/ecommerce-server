package io.hhplus.week2.controller

import io.hhplus.week2.dto.CouponInfo
import io.hhplus.week2.dto.ValidateCouponRequest
import io.hhplus.week2.dto.ValidateCouponResponse
import io.hhplus.week2.application.CouponUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/coupons")
@Tag(name = "Coupons", description = "쿠폰 API")
class CouponController(
    private val couponUseCase: CouponUseCase
) {

    @PostMapping("/validate")
    @Operation(
        summary = "쿠폰 검증",
        description = "주어진 주문 금액에 대해 쿠폰의 유효성을 검증하고 할인 금액을 계산합니다."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "검증 완료",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "400",
                description = "유효하지 않은 쿠폰"
            )
        ]
    )
    fun validateCoupon(
        @RequestBody request: ValidateCouponRequest
    ): ResponseEntity<ValidateCouponResponse> {
        val result = couponUseCase.validateCoupon(request.couponCode, request.orderAmount)

        val couponInfo = result.coupon?.let { coupon ->
            CouponInfo(
                id = coupon.id,
                code = coupon.code,
                name = coupon.name,
                type = coupon.type.name,
                discount = coupon.discount,
                minOrderAmount = coupon.minOrderAmount,
                maxDiscountAmount = coupon.maxDiscountAmount,
                validFrom = coupon.validFrom,
                validUntil = coupon.validUntil
            )
        }

        return ResponseEntity.ok(
            ValidateCouponResponse(
                valid = result.valid,
                coupon = couponInfo,
                discount = result.discount,
                message = result.message,
                details = result.details
            )
        )
    }

    @GetMapping
    @Operation(
        summary = "쿠폰 목록 조회",
        description = "활성화된 모든 쿠폰의 목록을 조회합니다."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content = [Content(mediaType = "application/json")]
            )
        ]
    )
    fun getCoupons(
        @Parameter(
            name = "page",
            description = "페이지 번호",
            example = "1"
        )
        @RequestParam(defaultValue = "1") page: Int,

        @Parameter(
            name = "limit",
            description = "페이지당 항목 수",
            example = "20"
        )
        @RequestParam(defaultValue = "20") limit: Int
    ): ResponseEntity<Map<String, Any>> {
        // Mock data: 간단히 몇 개의 쿠폰만 반환
        val coupons = listOf(
            CouponInfo(
                id = "coupon_001",
                code = "SUMMER2024",
                name = "여름 세일 10,000원 할인",
                type = "FIXED_AMOUNT",
                discount = 10000,
                minOrderAmount = 50000,
                maxDiscountAmount = null,
                validFrom = "2024-06-01T00:00:00Z",
                validUntil = "2025-12-31T23:59:59Z"
            ),
            CouponInfo(
                id = "coupon_002",
                code = "WELCOME20",
                name = "신규 회원 20% 할인",
                type = "PERCENTAGE",
                discount = 20,
                minOrderAmount = 0,
                maxDiscountAmount = 50000,
                validFrom = "2024-06-01T00:00:00Z",
                validUntil = "2025-12-31T23:59:59Z"
            ),
            CouponInfo(
                id = "coupon_003",
                code = "FREESHIP",
                name = "배송비 무료 쿠폰",
                type = "FREE_SHIPPING",
                discount = 3000,
                minOrderAmount = 10000,
                maxDiscountAmount = null,
                validFrom = "2024-06-01T00:00:00Z",
                validUntil = "2025-12-31T23:59:59Z"
            )
        )

        return ResponseEntity.ok(
            mapOf(
                "data" to coupons,
                "pagination" to mapOf(
                    "page" to page,
                    "limit" to limit,
                    "total" to coupons.size,
                    "totalPages" to 1
                )
            )
        )
    }
}
