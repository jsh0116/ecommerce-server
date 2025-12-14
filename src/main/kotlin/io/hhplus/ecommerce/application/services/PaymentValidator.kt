package io.hhplus.ecommerce.application.services

import io.hhplus.ecommerce.domain.Order
import io.hhplus.ecommerce.exception.OrderException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 결제 검증 컴포넌트
 *
 * 결제 프로세스의 모든 검증 로직을 담당합니다.
 * Single Responsibility: 검증 로직만 집중
 */
@Component
class PaymentValidator {
    companion object {
        private val logger = LoggerFactory.getLogger(PaymentValidator::class.java)
    }

    /**
     * 주문 소유권 검증
     *
     * @param order 주문
     * @param userId 결제 시도하는 사용자 ID
     * @throws OrderException.UnauthorizedOrderAccess 권한이 없는 경우
     */
    fun validateOrderOwnership(order: Order, userId: Long) {
        if (order.userId != userId) {
            logger.warn("권한 없는 사용자의 결제 시도: orderId=${order.id}, userId=$userId, order.userId=${order.userId}")
            throw OrderException.UnauthorizedOrderAccess()
        }
    }

    /**
     * 결제 가능 상태 검증
     *
     * @param order 주문
     * @throws OrderException.CannotPayOrder 결제 불가능한 상태인 경우
     */
    fun validatePayableStatus(order: Order) {
        if (!order.canPay()) {
            logger.warn("결제 불가능한 주문 상태: orderId=${order.id}, status=${order.status}")
            throw OrderException.CannotPayOrder()
        }
    }

    /**
     * 결제 전체 검증 (편의 메서드)
     *
     * @param order 주문
     * @param userId 사용자 ID
     */
    fun validatePayment(order: Order, userId: Long) {
        validateOrderOwnership(order, userId)
        validatePayableStatus(order)
    }
}
