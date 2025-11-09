package io.hhplus.week2.domain

import java.time.LocalDateTime

/**
 * 주문 도메인 모델
 */
data class Order(
    val id: String,
    val userId: String,
    val items: List<OrderItem>,
    val totalAmount: Long,
    val discountAmount: Long,
    val finalAmount: Long,
    var status: String = "PENDING",
    val couponId: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    var paidAt: LocalDateTime? = null
) {
    /**
     * 금액 계산
     */
    fun calculateAmount(coupon: UserCoupon?) {
        // TODO: 금액 계산 로직 (별도의 setter가 필요할 경우)
    }

    /**
     * 결제 가능 여부
     */
    fun canPay(): Boolean = "PENDING" == status && finalAmount > 0

    /**
     * 결제 완료
     */
    fun complete() {
        if (!canPay()) throw IllegalStateException("결제할 수 없는 주문입니다")
        status = "PAID"
        paidAt = LocalDateTime.now()
    }

    /**
     * 취소 가능 여부
     */
    fun canCancel(): Boolean {
        return status in listOf("PENDING", "PENDING_PAYMENT")
    }

    /**
     * 주문 취소
     */
    fun cancel() {
        if (!canCancel()) {
            if (status == "PAID") {
                throw IllegalStateException("결제 완료된 주문은 취소할 수 없습니다")
            }
            throw IllegalStateException("이미 배송이 시작되어 취소할 수 없습니다")
        }
        status = "CANCELLED"
    }
}

/**
 * 주문 항목 엔티티
 */
data class OrderItem(
    val productId: String,
    val productName: String,
    val quantity: Int,
    val unitPrice: Long,
    val subtotal: Long
) {
    companion object {
        fun create(product: Product, quantity: Int): OrderItem {
            return OrderItem(
                productId = product.id,
                productName = product.name,
                quantity = quantity,
                unitPrice = product.price,
                subtotal = product.calculatePrice(quantity)
            )
        }
    }
}