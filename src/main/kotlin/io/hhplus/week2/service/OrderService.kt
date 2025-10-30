package io.hhplus.week2.service

import io.hhplus.week2.domain.Order
import io.hhplus.week2.domain.OrderStatus

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
}
