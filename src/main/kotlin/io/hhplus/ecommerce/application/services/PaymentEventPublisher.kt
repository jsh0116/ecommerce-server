package io.hhplus.ecommerce.application.services

import io.hhplus.ecommerce.application.events.OrderPaidEvent
import io.hhplus.ecommerce.domain.Order
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/**
 * 결제 이벤트 발행 컴포넌트
 *
 * 결제 관련 이벤트 발행을 담당합니다.
 * Single Responsibility: 이벤트 발행만 집중
 */
@Component
class PaymentEventPublisher(
    private val eventPublisher: ApplicationEventPublisher
) {
    companion object {
        private val logger = LoggerFactory.getLogger(PaymentEventPublisher::class.java)
    }

    /**
     * 주문 결제 완료 이벤트 발행
     *
     * @param order 완료된 주문
     */
    fun publishOrderPaidEvent(order: Order) {
        eventPublisher.publishEvent(OrderPaidEvent.from(order))
        logger.debug("OrderPaidEvent 발행 완료: orderId=${order.id}")
    }
}
