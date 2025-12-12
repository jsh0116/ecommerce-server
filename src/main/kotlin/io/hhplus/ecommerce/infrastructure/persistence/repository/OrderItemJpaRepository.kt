package io.hhplus.ecommerce.infrastructure.persistence.repository

import io.hhplus.ecommerce.infrastructure.persistence.entity.OrderItemJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * 주문 아이템 JPA Repository
 */
@Repository
interface OrderItemJpaRepository : JpaRepository<OrderItemJpaEntity, Long> {

    /**
     * 주문 ID로 주문 아이템 목록 조회
     */
    fun findByOrderId(orderId: Long): List<OrderItemJpaEntity>

    /**
     * 주문 ID로 주문 아이템 삭제
     */
    fun deleteByOrderId(orderId: Long)
}
