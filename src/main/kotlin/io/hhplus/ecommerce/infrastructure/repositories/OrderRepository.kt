package io.hhplus.ecommerce.infrastructure.repositories

import io.hhplus.ecommerce.domain.Order

/**
 * 주문 관련 저장소 인터페이스
 */
interface OrderRepository {

    /**
     * 주문을 저장합니다.
     *
     * @param order 주문
     * @return 저장된 주문
     */
    fun save(order: Order): Order

    /**
     * 주문 ID로 주문을 조회합니다.
     *
     * @param id 주문 ID
     * @return 주문 또는 null
     */
    fun findById(id: String): Order?

    /**
     * 사용자 ID로 주문 목록을 조회합니다.
     *
     * @param userId 사용자 ID
     * @return 주문 목록
     */
    fun findByUserId(userId: String): List<Order>

    /**
     * 주문을 업데이트합니다.
     *
     * @param order 업데이트될 주문
     * @return 업데이트된 주문
     */
    fun update(order: Order): Order
}