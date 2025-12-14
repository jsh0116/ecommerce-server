package io.hhplus.ecommerce.application.events

import io.hhplus.ecommerce.domain.Order
import io.hhplus.ecommerce.domain.OrderItem
import org.springframework.context.ApplicationEvent
import java.time.LocalDateTime

/**
 * 주문 취소 이벤트
 *
 * 주문이 취소되었을 때 발행되며, 주문 취소 알림 등의 비동기 작업을 처리합니다.
 */
class OrderCancelledEvent(
    val orderId: Long,
    val userId: Long,
    val items: List<OrderItem>,
    val cancelledAt: LocalDateTime,
    val order: Order,
    source: Any = Object()
) : ApplicationEvent(source) {
    companion object {
        fun from(order: Order): OrderCancelledEvent {
            return OrderCancelledEvent(
                orderId = order.id,
                userId = order.userId,
                items = order.items,
                cancelledAt = LocalDateTime.now(),
                order = order
            )
        }
    }
}
