package io.hhplus.week2.service.impl

import io.hhplus.week2.domain.Order
import io.hhplus.week2.domain.OrderItem
import io.hhplus.week2.domain.OrderStatus
import io.hhplus.week2.domain.PaymentBreakdown
import io.hhplus.week2.domain.PaymentInfo
import io.hhplus.week2.domain.ShippingAddress
import io.hhplus.week2.dto.CreateOrderRequest
import io.hhplus.week2.repository.OrderRepository
import io.hhplus.week2.service.CouponService
import io.hhplus.week2.service.InventoryService
import io.hhplus.week2.service.OrderService
import io.hhplus.week2.service.OrderCreationResult
import io.hhplus.week2.service.ProductService
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * 주문 서비스 구현체
 */
@Service
class OrderServiceImpl(
    private val orderRepository: OrderRepository,
    private val productService: ProductService,
    private val inventoryService: InventoryService,
    private val couponService: CouponService
) : OrderService {

    companion object {
        private val orderSequence = AtomicInteger(0)
        private val dateFormatter = DateTimeFormatter.ISO_DATE_TIME
    }

    override fun createOrder(order: Order): Order {
        return orderRepository.save(order)
    }

    override fun getOrderById(orderId: String): Order? {
        return orderRepository.findById(orderId)
    }

    override fun getOrdersByUserId(userId: String): List<Order> {
        return orderRepository.findByUserId(userId)
    }

    override fun updateOrderStatus(orderId: String, newStatus: OrderStatus): Order? {
        val order = orderRepository.findById(orderId) ?: return null
        val updatedOrder = order.copy(status = newStatus)
        return orderRepository.update(updatedOrder)
    }

    override fun generateOrderNumber(): String {
        val today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        val sequence = orderSequence.incrementAndGet()
        return "$today${String.format("%05d", sequence)}"
    }

    override fun calculateShippingFee(shippingMethod: String, subtotal: Long): Long {
        return when {
            subtotal >= 30000 -> 0 // 30,000원 이상 무료배송
            shippingMethod == "dawn" -> 5000 // 새벽배송
            shippingMethod == "express" -> 4000 // 빠른배송
            else -> 3000 // 일반배송
        }
    }

    override fun processCreateOrder(request: CreateOrderRequest, userId: String): OrderCreationResult {
        try {
            // 1. 변량 정보 및 재고 조회
            val orderItems = mutableListOf<OrderItem>()
            var subtotal = 0L

            for (itemRequest in request.items) {
                val variant = productService.getVariantById(itemRequest.variantId)
                    ?: return OrderCreationResult(
                        success = false,
                        errorCode = "VARIANT_NOT_FOUND",
                        errorMessage = "변량 정보를 찾을 수 없습니다.",
                        errorDetails = mapOf("variantId" to itemRequest.variantId)
                    )

                val product = productService.getProductById(variant.productId)
                    ?: return OrderCreationResult(
                        success = false,
                        errorCode = "PRODUCT_NOT_FOUND",
                        errorMessage = "상품을 찾을 수 없습니다."
                    )

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
                    return OrderCreationResult(
                        success = false,
                        errorCode = "INSUFFICIENT_STOCK",
                        errorMessage = "재고가 부족합니다.",
                        errorDetails = mapOf(
                            "sku" to variant.sku,
                            "requestedQuantity" to itemRequest.quantity
                        )
                    )
                }
            }

            // 2. 쿠폰 검증 및 할인 계산
            var discount = 0L
            if (request.couponCode != null) {
                val validationResult = couponService.validateCoupon(request.couponCode, subtotal)
                if (!validationResult.valid) {
                    return OrderCreationResult(
                        success = false,
                        errorCode = "INVALID_COUPON",
                        errorMessage = validationResult.message,
                        errorDetails = validationResult.details
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
            val orderNumber = generateOrderNumber()
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
                    zipCode = request.shippingAddress.zipCode ?: ""
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

            createOrder(order)

            return OrderCreationResult(
                success = true,
                order = order
            )
        } catch (e: Exception) {
            return OrderCreationResult(
                success = false,
                errorCode = "INTERNAL_ERROR",
                errorMessage = "주문 생성 중 오류가 발생했습니다: ${e.message}"
            )
        }
    }
}