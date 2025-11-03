package io.hhplus.week2.controller

import io.hhplus.week2.domain.OrderStatus
import io.hhplus.week2.dto.CancelOrderRequest
import io.hhplus.week2.dto.CancelOrderResponse
import io.hhplus.week2.dto.CreateOrderRequest
import io.hhplus.week2.dto.CreateOrderResponse
import io.hhplus.week2.dto.OrderDetailResponse
import io.hhplus.week2.dto.OrderItemResponse
import io.hhplus.week2.dto.PaymentBreakdownResponse
import io.hhplus.week2.dto.PaymentResponse
import io.hhplus.week2.dto.ShippingResponse
import io.hhplus.week2.dto.VariantInfo
import io.hhplus.week2.service.OrderService
import io.hhplus.week2.service.ProductService
import io.hhplus.week2.service.InventoryService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders", description = "주문 API")
class OrderController(
    private val orderService: OrderService,
    private val productService: ProductService,
    private val inventoryService: InventoryService
) {

    private val dateFormatter = DateTimeFormatter.ISO_DATE_TIME

    @PostMapping
    @Operation(
        summary = "주문 생성",
        description = "장바구니의 상품으로 주문을 생성합니다. 재고 예약 및 최종 가격 계산이 수행됩니다."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "주문 생성 성공",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "400",
                description = "잘못된 요청"
            ),
            ApiResponse(
                responseCode = "409",
                description = "재고 부족 또는 쿠폰 검증 실패"
            )
        ]
    )
    fun createOrder(
        @Parameter(
            name = "Authorization",
            description = "JWT 토큰 (Bearer 토큰)",
            example = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
        )
        @RequestHeader(required = false) authorization: String?,

        @RequestBody request: CreateOrderRequest
    ): ResponseEntity<Any> {
        // 사용자 ID 추출 (실제로는 JWT 파싱)
        val userId = "user_001"

        // 비즈니스 로직을 서비스에 위임
        val result = orderService.processCreateOrder(request, userId)

        if (!result.success) {
            val statusCode = when (result.errorCode) {
                "INSUFFICIENT_STOCK" -> HttpStatus.CONFLICT
                "INVALID_COUPON" -> HttpStatus.BAD_REQUEST
                "VARIANT_NOT_FOUND", "PRODUCT_NOT_FOUND" -> HttpStatus.BAD_REQUEST
                else -> HttpStatus.INTERNAL_SERVER_ERROR
            }

            return ResponseEntity.status(statusCode)
                .body(
                    mapOf(
                        "code" to result.errorCode,
                        "message" to result.errorMessage,
                        "details" to (result.errorDetails ?: emptyMap<String, Any>())
                    )
                )
        }

        val order = result.order!!

        // 응답 생성
        val itemResponses = order.items.map { item ->
            OrderItemResponse(
                id = item.id,
                productName = item.productName,
                variant = VariantInfo(
                    id = item.variantId,
                    sku = "", // SKU는 variant 조회로 가능
                    color = "",
                    size = ""
                ),
                quantity = item.quantity,
                price = item.price,
                subtotal = item.subtotal
            )
        }

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(
                CreateOrderResponse(
                    id = order.id,
                    orderNumber = order.orderNumber,
                    status = order.status.name,
                    reservationExpiry = order.reservationExpiry ?: "",
                    items = itemResponses,
                    payment = PaymentResponse(
                        amount = order.totalAmount,
                        breakdown = PaymentBreakdownResponse(
                            subtotal = order.subtotal,
                            discount = order.discount,
                            pointsUsed = order.pointsUsed,
                            shipping = order.shippingFee,
                            total = order.totalAmount
                        )
                    ),
                    createdAt = order.createdAt
                )
            )
    }

    @GetMapping("/{orderId}")
    @Operation(
        summary = "주문 상세 조회",
        description = "주문 ID로 주문의 상세 정보를 조회합니다."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "404",
                description = "주문을 찾을 수 없음"
            )
        ]
    )
    fun getOrder(
        @Parameter(
            name = "orderId",
            description = "주문 ID",
            example = "550e8400-e29b-41d4-a716-446655440000"
        )
        @PathVariable orderId: String,

        @Parameter(
            name = "Authorization",
            description = "JWT 토큰",
            required = false
        )
        @RequestHeader(required = false) authorization: String?
    ): ResponseEntity<OrderDetailResponse> {
        val order = orderService.getOrderById(orderId)
            ?: return ResponseEntity.notFound().build()

        val itemResponses = order.items.map { item ->
            OrderItemResponse(
                id = item.id,
                productName = item.productName,
                variant = VariantInfo(
                    id = item.variantId,
                    sku = "SKU-" + item.variantId,
                    color = "color",
                    size = "M"
                ),
                quantity = item.quantity,
                price = item.price,
                subtotal = item.subtotal
            )
        }

        return ResponseEntity.ok(
            OrderDetailResponse(
                id = order.id,
                orderNumber = order.orderNumber,
                status = order.status.name,
                items = itemResponses,
                payment = PaymentResponse(
                    amount = order.payment.amount,
                    breakdown = PaymentBreakdownResponse(
                        subtotal = order.payment.breakdown.subtotal,
                        discount = order.payment.breakdown.discount,
                        pointsUsed = order.payment.breakdown.pointsUsed,
                        shipping = order.payment.breakdown.shipping,
                        total = order.payment.breakdown.total
                    )
                ),
                shipping = ShippingResponse(
                    address = io.hhplus.week2.dto.ShippingAddressRequest(
                        name = order.shippingAddress.name,
                        phone = order.shippingAddress.phone,
                        address = order.shippingAddress.address,
                        addressDetail = order.shippingAddress.addressDetail,
                        zipCode = order.shippingAddress.zipCode
                    ),
                    method = order.shippingMethod,
                    fee = order.shippingFee,
                    trackingNumber = null,
                    carrier = "CJ대한통운",
                    estimatedDelivery = "2025-11-05"
                ),
                createdAt = order.createdAt,
                updatedAt = LocalDateTime.now().format(dateFormatter)
            )
        )
    }

    @PostMapping("/{orderId}/cancel")
    @Operation(
        summary = "주문 취소",
        description = "아직 배송이 시작되지 않은 주문을 취소하고 환불 처리합니다."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "취소 성공",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "400",
                description = "취소할 수 없는 주문 상태"
            ),
            ApiResponse(
                responseCode = "404",
                description = "주문을 찾을 수 없음"
            )
        ]
    )
    fun cancelOrder(
        @Parameter(
            name = "orderId",
            description = "주문 ID",
            example = "550e8400-e29b-41d4-a716-446655440000"
        )
        @PathVariable orderId: String,

        @RequestBody request: CancelOrderRequest,

        @Parameter(
            name = "Authorization",
            description = "JWT 토큰",
            required = false
        )
        @RequestHeader(required = false) authorization: String?
    ): ResponseEntity<Any> {
        val order = orderService.getOrderById(orderId)
            ?: return ResponseEntity.notFound().build()

        // 취소 가능 상태 확인 (비즈니스 로직)
        val cancellable = order.status in listOf(
            OrderStatus.PENDING_PAYMENT,
            OrderStatus.PAID,
            OrderStatus.PREPARING
        )

        if (!cancellable) {
            return ResponseEntity.badRequest()
                .body(
                    mapOf(
                        "code" to "CANNOT_CANCEL",
                        "message" to "이미 배송이 시작되어 취소할 수 없습니다. 반품을 신청해주세요."
                    )
                )
        }

        // 주문 상태 업데이트
        orderService.updateOrderStatus(orderId, OrderStatus.CANCELLED)

        // 재고 복구 (예약 해제)
        for (item in order.items) {
            val variant = productService.getVariantById(item.variantId)
            if (variant != null) {
                inventoryService.cancelReservation(variant.sku, item.quantity)
            }
        }

        return ResponseEntity.ok(
            CancelOrderResponse(
                message = "주문이 취소되었습니다.",
                refundAmount = order.totalAmount,
                estimatedRefundDate = "2025-11-10"
            )
        )
    }

}
