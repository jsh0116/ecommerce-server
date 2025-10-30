package io.hhplus.week2.domain

/**
 * 주문 도메인 모델
 */
data class Order(
    val id: String,
    val orderNumber: String,
    val userId: String,
    val status: OrderStatus,
    val items: List<OrderItem>,
    val payment: PaymentInfo,
    val shippingAddress: ShippingAddress,
    val shippingMethod: String,
    val couponCode: String?,
    val pointsUsed: Long,
    val subtotal: Long,
    val discount: Long,
    val shippingFee: Long,
    val totalAmount: Long,
    val requestMessage: String?,
    val reservationExpiry: String?,
    val createdAt: String
)

enum class OrderStatus {
    PENDING_PAYMENT,
    PAID,
    PREPARING,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    RETURN_REQUESTED,
    RETURN_COMPLETED,
    EXCHANGE_REQUESTED,
    EXCHANGE_COMPLETED
}

data class OrderItem(
    val id: String,
    val productId: String,
    val variantId: String,
    val productName: String,
    val quantity: Int,
    val price: Long,
    val subtotal: Long
)

data class PaymentInfo(
    val amount: Long,
    val breakdown: PaymentBreakdown,
    val method: String?,
    val status: String?
)

data class PaymentBreakdown(
    val subtotal: Long,
    val discount: Long,
    val pointsUsed: Long,
    val shipping: Long,
    val total: Long
)

data class ShippingAddress(
    val name: String,
    val phone: String,
    val address: String,
    val addressDetail: String,
    val zipCode: String
)