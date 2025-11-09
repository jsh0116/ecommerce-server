package io.hhplus.ecommerce.presentation.controllers

import io.hhplus.ecommerce.dto.CancelOrderRequest
import io.hhplus.ecommerce.dto.CancelOrderResponse
import io.hhplus.ecommerce.dto.CreateOrderRequest
import io.hhplus.ecommerce.dto.CreateOrderResponse
import io.hhplus.ecommerce.dto.OrderDetailResponse
import io.hhplus.ecommerce.dto.OrderItemResponse
import io.hhplus.ecommerce.dto.PaymentBreakdownResponse
import io.hhplus.ecommerce.dto.PaymentResponse
import io.hhplus.ecommerce.dto.ShippingResponse
import io.hhplus.ecommerce.dto.VariantInfo
import io.hhplus.ecommerce.application.usecases.OrderUseCase
import io.hhplus.ecommerce.application.usecases.ProductUseCase
import io.hhplus.ecommerce.application.usecases.InventoryUseCase
import io.hhplus.ecommerce.dto.ShippingAddressRequest
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
    private val orderUseCase: OrderUseCase,
    private val productUseCase: ProductUseCase,
    private val inventoryUseCase: InventoryUseCase
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
        // 입력 검증
        if (request.items.isEmpty()) {
            return ResponseEntity.badRequest()
                .body(mapOf("code" to "INVALID_ITEMS", "message" to "최소 1개 이상의 상품이 필요합니다"))
        }

        if (!request.agreeToTerms) {
            return ResponseEntity.badRequest()
                .body(mapOf("code" to "MUST_AGREE_TERMS", "message" to "약관에 동의해야 합니다"))
        }

        // 각 상품에 대해 재고 확인 및 주문 항목 생성
        val orderItems = mutableListOf<OrderItemResponse>()
        var totalAmount = 0L

        for (item in request.items) {
            val product = productUseCase.getProductById(item.variantId)
                ?: return ResponseEntity.notFound().build()

            // 재고 확인 (Inventory 사용)
            val inventory = inventoryUseCase.getInventoryBySku(product.id)
                ?: return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(
                        mapOf(
                            "code" to "INVENTORY_NOT_FOUND",
                            "message" to "재고 정보를 찾을 수 없습니다: ${product.name}"
                        )
                    )

            if (!inventory.canReserve(item.quantity)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(
                        mapOf(
                            "code" to "INSUFFICIENT_STOCK",
                            "message" to "재고 부족: ${product.name} (가용 재고 ${inventory.getAvailableStock()}개)"
                        )
                    )
            }

            val subtotal = product.calculatePrice(item.quantity)
            totalAmount += subtotal

            orderItems.add(
                OrderItemResponse(
                    id = "item_${product.id}",
                    productName = product.name,
                    variant = VariantInfo(
                        id = product.id,
                        sku = "SKU-${product.id}",
                        color = "Black",
                        size = "M"
                    ),
                    quantity = item.quantity,
                    price = product.price,
                    subtotal = subtotal
                )
            )
        }

        // 배송료 계산
        val shippingFee = when (request.shippingMethod) {
            "express" -> 5000L
            "dawn" -> 7000L
            else -> 3000L  // standard
        }

        // 쿠폰 검증 및 할인 계산
        var discountAmount = 0L
        if (!request.couponCode.isNullOrBlank()) {
            // TODO: CouponService를 통한 쿠폰 검증 로직
            // val validationResult = couponService.validateCoupon(request.couponCode, totalAmount)
            // 현재는 간단히 처리
            discountAmount = 0L
        }

        // 포인트 사용 처리
        val pointsUsed = minOf(request.pointsToUse, totalAmount)

        // 최종 결제액 계산
        val finalAmount = totalAmount - discountAmount - pointsUsed + shippingFee

        // 주문 생성
        val orderId = "ORD-${System.currentTimeMillis()}"
        val orderNumber = "ORD-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))}-${(System.currentTimeMillis() % 10000).toInt()}"

        val userId = authorization?.substringAfter("Bearer ")?.trim() ?: "user1"

        // UseCase를 통한 주문 생성
        val orderItemRequests = orderItems.map { itemResponse ->
            OrderUseCase.OrderItemRequest(
                productId = itemResponse.variant.id,
                quantity = itemResponse.quantity
            )
        }

        val order = orderUseCase.createOrder(
            userId = userId,
            items = orderItemRequests,
            couponId = if (!request.couponCode.isNullOrBlank()) request.couponCode else null
        )

        // 응답 생성
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(
                CreateOrderResponse(
                    id = order.id,
                    orderNumber = orderNumber,
                    status = order.status,
                    reservationExpiry = LocalDateTime.now().plusMinutes(15).format(dateFormatter),
                    items = orderItems,
                    payment = PaymentResponse(
                        amount = order.finalAmount,
                        breakdown = PaymentBreakdownResponse(
                            subtotal = order.totalAmount,
                            discount = order.discountAmount,
                            pointsUsed = pointsUsed,
                            shipping = shippingFee,
                            total = order.finalAmount
                        )
                    ),
                    createdAt = order.createdAt.format(dateFormatter)
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
        val order = orderUseCase.getOrderById(orderId)
            ?: return ResponseEntity.notFound().build()

        val itemResponses = order.items.map { item ->
            OrderItemResponse(
                id = item.productId,
                productName = item.productName,
                variant = VariantInfo(
                    id = item.productId,
                    sku = "SKU-${item.productId}",
                    color = "Black",
                    size = "M"
                ),
                quantity = item.quantity,
                price = item.unitPrice,
                subtotal = item.subtotal
            )
        }

        return ResponseEntity.ok(
            OrderDetailResponse(
                id = order.id,
                orderNumber = "ORD-${order.id}",
                status = order.status,
                items = itemResponses,
                payment = PaymentResponse(
                    amount = order.finalAmount,
                    breakdown = PaymentBreakdownResponse(
                        subtotal = order.totalAmount,
                        discount = order.discountAmount,
                        pointsUsed = 0L,
                        shipping = 3000L,
                        total = order.finalAmount
                    )
                ),
                shipping = ShippingResponse(
                    address = ShippingAddressRequest(
                        name = "수령인",
                        phone = "010-1234-5678",
                        address = "서울시 강남구",
                        addressDetail = "상세주소",
                        zipCode = "12345"
                    ),
                    method = "standard",
                    fee = 3000L,
                    trackingNumber = null,
                    carrier = "CJ대한통운",
                    estimatedDelivery = "2025-11-05"
                ),
                createdAt = order.createdAt.format(dateFormatter),
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
        val userId = authorization?.substringAfter("Bearer ")?.trim() ?: "user1"

        return try {
            // UseCase를 통한 주문 취소 및 재고 복구
            val cancelledOrder = orderUseCase.cancelOrder(orderId, userId)

            // 환불금액 계산
            val refundAmount = cancelledOrder.finalAmount

            // 환불 예상 일자 계산 (업무일 기준 2~3일)
            val estimatedRefundDate = LocalDateTime.now().plusDays(3).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

            ResponseEntity.ok(
                CancelOrderResponse(
                    message = "주문이 취소되었습니다.",
                    refundAmount = refundAmount,
                    estimatedRefundDate = estimatedRefundDate
                )
            )
        } catch (e: IllegalStateException) {
            ResponseEntity.badRequest()
                .body(
                    mapOf(
                        "code" to "CANNOT_CANCEL",
                        "message" to e.message
                    )
                )
        }
    }

}
