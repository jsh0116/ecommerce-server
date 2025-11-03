package io.hhplus.week2.service

import io.hhplus.week2.domain.Order
import io.hhplus.week2.domain.OrderStatus
import io.hhplus.week2.dto.CreateOrderRequest

/**
 * 주문 관련 도메인 서비스
 */
interface OrderService {

    /**
     * 주문을 생성합니다.
     *
     * @param order 주문 정보
     * @return 생성된 주문
     */
    fun createOrder(order: Order): Order

    /**
     * 주문 ID로 주문 정보를 조회합니다.
     *
     * @param orderId 주문 ID
     * @return 주문 정보 또는 null
     */
    fun getOrderById(orderId: String): Order?

    /**
     * 사용자 ID로 해당 사용자의 주문 목록을 조회합니다.
     *
     * @param userId 사용자 ID
     * @return 주문 목록
     */
    fun getOrdersByUserId(userId: String): List<Order>

    /**
     * 주문 상태를 업데이트합니다.
     *
     * @param orderId 주문 ID
     * @param newStatus 새로운 상태
     * @return 업데이트된 주문 정보 또는 null
     */
    fun updateOrderStatus(orderId: String, newStatus: OrderStatus): Order?

    /**
     * 주문 번호를 생성합니다.
     *
     * @return 생성된 주문 번호
     */
    fun generateOrderNumber(): String

    /**
     * 배송비를 계산합니다.
     *
     * @param shippingMethod 배송 방법 (standard, express, dawn)
     * @param subtotal 소계 금액
     * @return 배송비
     */
    fun calculateShippingFee(shippingMethod: String, subtotal: Long): Long

    /**
     * 주문 생성 요청을 처리합니다. 재고 예약, 쿠폰 검증, 배송비 계산 등의 로직을 포함합니다.
     *
     * @param request 주문 생성 요청 DTO
     * @param userId 사용자 ID
     * @return 주문 생성 결과 (주문 또는 에러 정보)
     */
    fun processCreateOrder(request: CreateOrderRequest, userId: String): OrderCreationResult
}

/**
 * 주문 생성 결과를 나타내는 데이터 클래스
 */
data class OrderCreationResult(
    val success: Boolean,
    val order: Order? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val errorDetails: Map<String, Any>? = null
)
