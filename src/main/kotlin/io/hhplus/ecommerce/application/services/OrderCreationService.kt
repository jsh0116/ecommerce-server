package io.hhplus.ecommerce.application.services

import io.hhplus.ecommerce.domain.Order
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 주문 생성 Domain Service
 *
 * 주문 생성과 재고 예약을 조율하는 Domain Service입니다.
 * Validator, Executor, Publisher 패턴을 활용하여 책임을 명확히 분리합니다.
 *
 * 주문 생성 프로세스:
 * 1. 사용자 검증 → OrderValidator
 * 2. 상품 및 재고 검증/예약 → OrderValidator + OrderExecutor
 * 3. 쿠폰 검증 → OrderValidator
 * 4. 주문 생성 → OrderExecutor
 * 5. 이벤트 발행 (미래) → OrderEventPublisher
 */
@Service
class OrderCreationService(
    private val orderValidator: OrderValidator,
    private val orderExecutor: OrderExecutor,
    private val orderEventPublisher: OrderEventPublisher
) {
    companion object {
        private val logger = LoggerFactory.getLogger(OrderCreationService::class.java)
    }

    /**
     * 주문 생성 (재고 예약 포함)
     *
     * Validator, Executor, Publisher 패턴을 활용한 깔끔한 orchestration
     *
     * @param userId 사용자 ID
     * @param items 주문 아이템 요청 목록
     * @param couponId 쿠폰 ID (nullable)
     * @return 생성된 주문
     */
    @Transactional
    fun createOrder(
        userId: Long,
        items: List<OrderItemRequest>,
        couponId: Long?
    ): Order {
        logger.debug("주문 생성 시작: userId=$userId, items=${items.size}개, couponId=$couponId")

        // 1. 사용자 검증 (OrderValidator)
        val user = orderValidator.validateUser(userId)

        // 2. 상품 검증 및 재고 예약 (OrderValidator + OrderExecutor)
        val orderItems = items.map { req ->
            val product = orderValidator.validateProduct(req.productId)
            orderExecutor.reserveStockAndCreateOrderItem(product, req.quantity)
        }

        // 3. 쿠폰 검증 (OrderValidator)
        val userCoupon = orderValidator.validateCoupon(userId, couponId)

        // 4. 주문 생성 (OrderExecutor)
        val order = orderExecutor.createOrder(user, orderItems, userCoupon)

        // 5. 이벤트 발행 (OrderEventPublisher - 미래 확장용)
        // orderEventPublisher.publishOrderCreatedEvent(order)

        return order
    }

    /**
     * 주문 취소 (재고 예약 취소 포함)
     *
     * Executor, Publisher 패턴을 활용한 취소 프로세스
     *
     * @param orderId 주문 ID
     * @param userId 사용자 ID
     * @return 취소된 주문
     */
    @Transactional
    fun cancelOrder(orderId: Long, userId: Long): Order {
        logger.debug("주문 취소 시작: orderId=$orderId, userId=$userId")

        // 1. 주문 취소 (OrderExecutor)
        val order = orderExecutor.cancelOrder(orderId, userId)

        // 2. 재고 예약 취소 (OrderExecutor)
        orderExecutor.cancelReservations(order.items)

        // 3. 이벤트 발행 (OrderEventPublisher - 미래 확장용)
        // orderEventPublisher.publishOrderCancelledEvent(order)

        return order
    }

    // DTO
    data class OrderItemRequest(val productId: Long, val quantity: Int)
}
