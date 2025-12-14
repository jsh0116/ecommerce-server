package io.hhplus.ecommerce.application.services

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
 * 현재는 사용하지 않지만, 향후 OrderCreatedEvent, OrderCancelledEvent 등을
 * 추가할 때 사용할 수 있도록 구조만 준비해둡니다.
 */
@Component
class OrderEventPublisher(
    private val eventPublisher: ApplicationEventPublisher
) {
    companion object {
        private val logger = LoggerFactory.getLogger(OrderEventPublisher::class.java)
    }

    /**
     * 주문 생성 이벤트 발행 (미래 확장용)
     *
     * @param order 생성된 주문
     */
    fun publishOrderCreatedEvent(order: Order) {
        // TODO: OrderCreatedEvent 구현 시 활성화
        // eventPublisher.publishEvent(OrderCreatedEvent.from(order))
        logger.debug("OrderCreatedEvent 발행 (미구현): orderId=${order.id}")
    }

    /**
     * 주문 취소 이벤트 발행 (미래 확장용)
     *
     * @param order 취소된 주문
     */
    fun publishOrderCancelledEvent(order: Order) {
        // TODO: OrderCancelledEvent 구현 시 활성화
        // eventPublisher.publishEvent(OrderCancelledEvent.from(order))
        logger.debug("OrderCancelledEvent 발행 (미구현): orderId=${order.id}")
    }
}
