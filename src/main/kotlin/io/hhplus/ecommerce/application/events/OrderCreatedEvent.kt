package io.hhplus.ecommerce.application.events

import io.hhplus.ecommerce.domain.Order
import io.hhplus.ecommerce.domain.OrderItem
import org.springframework.context.ApplicationEvent
import java.time.LocalDateTime

/**
 * 주문 생성 이벤트
 *
 * 주문이 생성되었을 때 발행되며, 주문 생성 알림 등의 비동기 작업을 처리합니다.
 */
class OrderCreatedEvent(
    val orderId: Long,
    val userId: Long,
    val items: List<OrderItem>,
    val totalAmount: Long,
    val discountAmount: Long,
    val finalAmount: Long,
    val createdAt: LocalDateTime?,
    val order: Order,
    source: Any = Object()
) : ApplicationEvent(source) {
    companion object {
        fun from(order: Order): OrderCreatedEvent {
            return OrderCreatedEvent(
                orderId = order.id,
                userId = order.userId,
                items = order.items,
                totalAmount = order.totalAmount,
                discountAmount = order.discountAmount,
                finalAmount = order.finalAmount,
                createdAt = order.createdAt,
                order = order
            )
        }
    }
}
