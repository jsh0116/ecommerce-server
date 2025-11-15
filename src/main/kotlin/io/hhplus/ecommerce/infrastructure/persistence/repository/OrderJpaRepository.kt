package io.hhplus.ecommerce.infrastructure.persistence.repository

import io.hhplus.ecommerce.infrastructure.persistence.entity.OrderJpaEntity
import io.hhplus.ecommerce.infrastructure.persistence.entity.OrderJpaStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

/**
 * 주문 JPA Repository
 */
@Repository
interface OrderJpaRepository : JpaRepository<OrderJpaEntity, Long> {

    /**
     * 주문 번호로 조회
     */
    fun findByOrderNumber(orderNumber: String): OrderJpaEntity?

    /**
     * 사용자별 주문 조회
     */
    fun findByUserIdOrderByCreatedAtDesc(userId: Long): List<OrderJpaEntity>

    /**
     * 사용자별, 상태별 주문 조회
     */
    fun findByUserIdAndStatusOrderByCreatedAtDesc(
        userId: Long,
        status: OrderJpaStatus
    ): List<OrderJpaEntity>

    /**
     * 예약 만료 임박 주문 조회 (Saga 스케줄러용)
     */
    @Query("""
        SELECT o FROM OrderJpaEntity o
        WHERE o.status = 'PENDING_PAYMENT'
        AND o.reservationExpiresAt IS NOT NULL
        AND o.reservationExpiresAt <= CURRENT_TIMESTAMP
    """)
    fun findExpiredReservationOrders(): List<OrderJpaEntity>
}
