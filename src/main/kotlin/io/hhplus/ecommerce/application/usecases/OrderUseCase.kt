package io.hhplus.ecommerce.application.usecases

import io.hhplus.ecommerce.domain.Order
import io.hhplus.ecommerce.application.services.OrderService
import io.hhplus.ecommerce.application.services.OrderCreationService
import io.hhplus.ecommerce.application.services.PaymentProcessingService
import io.hhplus.ecommerce.exception.*
import org.springframework.stereotype.Service

/**
 * 주문 유스케이스
 *
 * Domain Service를 활용하여 비즈니스 로직을 캡슐화합니다.
 * UseCase는 순수한 orchestration만 담당합니다.
 */
@Service
class OrderUseCase(
    private val orderService: OrderService,
    private val orderCreationService: OrderCreationService,
    private val paymentProcessingService: PaymentProcessingService
) {
    /**
     * 주문 생성
     *
     * OrderCreationService에 위임하여 비즈니스 로직을 캡슐화합니다.
     */
    fun createOrder(
        userId: Long,
        items: List<OrderItemRequest>,
        couponId: Long?
    ): Order {
        return orderCreationService.createOrder(
            userId = userId,
            items = items.map { OrderCreationService.OrderItemRequest(it.productId, it.quantity) },
            couponId = couponId
        )
    }

    /**
     * 주문 조회
     */
    fun getOrderById(orderId: Long): Order? {
        return try {
            orderService.getById(orderId)
        } catch (e: OrderException.OrderNotFound) {
            null
        }
    }

    /**
     * 사용자 주문 목록 조회
     */
    fun getOrdersByUserId(userId: Long): List<Order> {
        return orderService.getByUserId(userId)
    }

    /**
     * 주문 상태 업데이트
     */
    fun updateOrderStatus(orderId: Long, newStatus: String): Order? {
        return try {
            orderService.updateOrderStatus(orderId, newStatus)
        } catch (e: OrderException.OrderNotFound) {
            null
        }
    }

    /**
     * 주문 취소 및 재고 예약 취소
     *
     * OrderCreationService에 위임하여 비즈니스 로직을 캡슐화합니다.
     */
    fun cancelOrder(orderId: Long, userId: Long): Order {
        return orderCreationService.cancelOrder(orderId, userId)
    }

    /**
     * 결제 처리
     *
     * PaymentProcessingService에 위임하여 비즈니스 로직을 캡슐화합니다.
     */
    fun processPayment(orderId: Long, userId: Long): PaymentProcessingService.PaymentResult {
        return paymentProcessingService.processPayment(orderId, userId)
    }

    // 내부 DTO 정의
    data class OrderItemRequest(val productId: Long, val quantity: Int)
}