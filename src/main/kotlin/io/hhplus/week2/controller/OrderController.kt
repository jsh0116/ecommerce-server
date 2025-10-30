package io.hhplus.week2.controller

import io.hhplus.week2.domain.Order
import io.hhplus.week2.domain.OrderItem
import io.hhplus.week2.domain.OrderStatus
import io.hhplus.week2.domain.PaymentBreakdown
import io.hhplus.week2.domain.PaymentInfo
import io.hhplus.week2.domain.ShippingAddress
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
import io.hhplus.week2.service.CouponService
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
import java.util.UUID

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders", description = "주문 API")
class OrderController(
    private val orderService: OrderService,
    private val productService: ProductService,
    private val inventoryService: InventoryService,
    private val couponService: CouponService
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

        // 1. 변량 정보 및 재고 조회
        val orderItems = mutableListOf<OrderItem>()
        var subtotal = 0L

        for (itemRequest in request.items) {
            val variant = productService.getVariantById(itemRequest.variantId)
                ?: return ResponseEntity.badRequest()
                    .body(
                        mapOf(
                            "code" to "VARIANT_NOT_FOUND",
                            "message" to "변량 정보를 찾을 수 없습니다.",
                            "details" to mapOf("variantId" to itemRequest.variantId)
                        )
                    )

            val product = productService.getProductById(variant.productId)
                ?: return ResponseEntity.badRequest()
                    .body(mapOf("code" to "PRODUCT_NOT_FOUND", "message" to "상품을 찾을 수 없습니다."))

            val itemSubtotal = variant.price * itemRequest.quantity

            orderItems.add(
                OrderItem(
                    id = UUID.randomUUID().toString(),
                    productId = product.id,
                    variantId = variant.id,
                    productName = product.name,
                    quantity = itemRequest.quantity,
                    price = variant.price,
                    subtotal = itemSubtotal
                )
            )

            subtotal += itemSubtotal

            // 재고 예약
            val reservation = inventoryService.reserveInventory(variant.sku, itemRequest.quantity, 15)
            if (reservation == null) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(
                        mapOf(
                            "code" to "INSUFFICIENT_STOCK",
                            "message" to "재고가 부족합니다.",
                            "details" to mapOf(
                                "sku" to variant.sku,
                                "requestedQuantity" to itemRequest.quantity
                            )
                        )
                    )
            }
        }

        // 2. 쿠폰 검증 및 할인 계산
        var discount = 0L
        if (request.couponCode != null) {
            val validationResult = couponService.validateCoupon(request.couponCode, subtotal)
            if (!validationResult.valid) {
                return ResponseEntity.badRequest()
                    .body(
                        mapOf(
                            "code" to "INVALID_COUPON",
                            "message" to validationResult.message,
                            "details" to validationResult.details
                        )
                    )
            }
            discount = validationResult.discount
        }

        // 3. 배송비 계산
        val shippingFee = calculateShippingFee(request.shippingMethod, subtotal)

        // 4. 최종 금액 계산
        val pointsUsed = request.pointsToUse
        val totalAmount = subtotal - discount - pointsUsed + shippingFee

        // 5. 주문 생성
        val orderId = UUID.randomUUID().toString()
        val orderNumber = orderService.generateOrderNumber()
        val reservationExpiry = LocalDateTime.now().plusMinutes(15).format(dateFormatter)

        val order = Order(
            id = orderId,
            orderNumber = orderNumber,
            userId = userId,
            status = OrderStatus.PENDING_PAYMENT,
            items = orderItems,
            payment = PaymentInfo(
                amount = totalAmount,
                breakdown = PaymentBreakdown(
                    subtotal = subtotal,
                    discount = discount,
                    pointsUsed = pointsUsed,
                    shipping = shippingFee,
                    total = totalAmount
                ),
                method = null,
                status = null
            ),
            shippingAddress = ShippingAddress(
                name = request.shippingAddress.name,
                phone = request.shippingAddress.phone,
                address = request.shippingAddress.address,
                addressDetail = request.shippingAddress.addressDetail,
                zipCode = request.shippingAddress.zipCode
            ),
            shippingMethod = request.shippingMethod,
            couponCode = request.couponCode,
            pointsUsed = pointsUsed,
            subtotal = subtotal,
            discount = discount,
            shippingFee = shippingFee,
            totalAmount = totalAmount,
            requestMessage = request.requestMessage,
            reservationExpiry = reservationExpiry,
            createdAt = LocalDateTime.now().format(dateFormatter)
        )

        orderService.createOrder(order)

        // 응답 생성
        val itemResponses = orderItems.map { item ->
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
                    id = orderId,
                    orderNumber = orderNumber,
                    status = OrderStatus.PENDING_PAYMENT.name,
                    reservationExpiry = reservationExpiry,
                    items = itemResponses,
                    payment = PaymentResponse(
                        amount = totalAmount,
                        breakdown = PaymentBreakdownResponse(
                            subtotal = subtotal,
                            discount = discount,
                            pointsUsed = pointsUsed,
                            shipping = shippingFee,
                            total = totalAmount
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

        // 취소 가능 상태 확인
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

    private fun calculateShippingFee(shippingMethod: String, subtotal: Long): Long {
        // 간단한 배송비 계산
        return when {
            subtotal >= 30000 -> 0 // 30,000원 이상 무료배송
            shippingMethod == "dawn" -> 5000 // 새벽배송
            shippingMethod == "express" -> 4000 // 빠른배송
            else -> 3000 // 일반배송
        }
    }
}
