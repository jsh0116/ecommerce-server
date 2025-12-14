package io.hhplus.ecommerce.application.services

import io.hhplus.ecommerce.application.events.OrderCancelledEvent
import io.hhplus.ecommerce.application.events.OrderCreatedEvent
import io.hhplus.ecommerce.domain.Order
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/**
 * 주문 이벤트 발행 컴포넌트
 *
 * 주문 관련 이벤트 발행을 담당합니다.
 * Single Responsibility: 이벤트 발행만 집중
 *
 * 발행하는 이벤트:
 * - OrderCreatedEvent: 주문 생성 시
 * - OrderCancelledEvent: 주문 취소 시
 */
@Component
class OrderEventPublisher(
    private val eventPublisher: ApplicationEventPublisher
) {
    companion object {
        private val logger = LoggerFactory.getLogger(OrderEventPublisher::class.java)
    }

    /**
     * 주문 생성 이벤트 발행
     *
     * @param order 생성된 주문
     */
    fun publishOrderCreatedEvent(order: Order) {
        eventPublisher.publishEvent(OrderCreatedEvent.from(order))
        logger.debug("OrderCreatedEvent 발행: orderId=${order.id}")
    }

    /**
     * 주문 취소 이벤트 발행
     *
     * @param order 취소된 주문
     */
    fun publishOrderCancelledEvent(order: Order) {
        eventPublisher.publishEvent(OrderCancelledEvent.from(order))
        logger.debug("OrderCancelledEvent 발행: orderId=${order.id}")
    }
}
