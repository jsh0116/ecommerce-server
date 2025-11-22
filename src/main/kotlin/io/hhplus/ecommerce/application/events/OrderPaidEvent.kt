package io.hhplus.ecommerce.application.events

import io.hhplus.ecommerce.domain.Order
import io.hhplus.ecommerce.domain.OrderItem
import org.springframework.context.ApplicationEvent
import java.time.LocalDateTime

/**
 * 주문 결제 완료 이벤트
 *
 * 주문 결제가 완료되었을 때 발행되며, 외부 데이터 전송 등의 비동기 작업을 처리합니다.
 * 이벤트 기반 처리를 통해 DB 트랜잭션 완료 후 별도의 비동기 작업으로 분리합니다.
 */
class OrderPaidEvent(
    val orderId: Long,
    val userId: Long,
    val items: List<OrderItem>,
    val totalAmount: Long,
    val discountAmount: Long,
    val paidAt: LocalDateTime?,
    source: Any = Object()
) : ApplicationEvent(source) {
    companion object {
        fun from(order: Order): OrderPaidEvent {
            return OrderPaidEvent(
                orderId = order.id,
                userId = order.userId,
                items = order.items,
                totalAmount = order.totalAmount,
                discountAmount = order.discountAmount,
                paidAt = order.paidAt
            )
        }
    }
}
