package io.hhplus.ecommerce.presentation.controllers

import io.hhplus.ecommerce.presentation.dto.CouponInfo
import io.hhplus.ecommerce.presentation.dto.IssueCouponRequest
import io.hhplus.ecommerce.presentation.dto.IssueCouponResponse
import io.hhplus.ecommerce.presentation.dto.ValidateCouponRequest
import io.hhplus.ecommerce.presentation.dto.ValidateCouponResponse
import io.hhplus.ecommerce.application.usecases.CouponUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
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
    private val logger = LoggerFactory.getLogger(javaClass)

    @PostMapping("/issue")
    @Operation(
        summary = "쿠폰 발급 요청 (Kafka 비동기)",
        description = """
            Kafka를 통한 비동기 쿠폰 발급 요청입니다.
            - 요청이 즉시 접수되고 requestId가 반환됩니다.
            - 실제 발급은 Kafka Consumer에서 비동기적으로 처리됩니다.
            - requestId로 발급 상태를 조회할 수 있습니다 (멱등성 보장).
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "발급 요청 접수 완료",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "404",
                description = "쿠폰을 찾을 수 없음"
            ),
            ApiResponse(
                responseCode = "500",
                description = "서버 오류"
            )
        ]
    )
    fun issueCoupon(
        @RequestBody request: IssueCouponRequest
    ): ResponseEntity<IssueCouponResponse> {
        logger.info("쿠폰 발급 요청 수신: couponId={}, userId={}", request.couponId, request.userId)

        // UseCase를 통해 비동기 발급 요청 (구현체는 인터페이스를 통해 주입됨)
        val requestId = couponUseCase.requestCouponIssuanceAsync(request.couponId, request.userId)

        val response = IssueCouponResponse(
            requestId = requestId,
            couponId = request.couponId,
            userId = request.userId,
            status = "PROCESSING",
            message = "쿠폰 발급 요청이 접수되었습니다. 처리 중입니다."
        )

        logger.info("쿠폰 발급 요청 응답: requestId={}", requestId)

        return ResponseEntity.ok(response)
    }

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
                id = coupon.id.toString(),
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
