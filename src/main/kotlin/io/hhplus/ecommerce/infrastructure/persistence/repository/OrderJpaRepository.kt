package io.hhplus.ecommerce.infrastructure.persistence.repository

import io.hhplus.ecommerce.infrastructure.persistence.entity.OrderJpaEntity
import io.hhplus.ecommerce.infrastructure.persistence.entity.OrderJpaStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

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

    /**
     * 사용자별 주문 조회 - 복합 인덱스 활용
     */
    @Query("""
        SELECT o FROM OrderJpaEntity o
        WHERE o.userId = :userId
        ORDER BY o.createdAt DESC
    """)
    fun findByUserIdOptimized(@Param("userId") userId: Long): List<OrderJpaEntity>

    /**
     * 사용자별, 상태별 주문 조회 - 복합 인덱스 활용
     */
    @Query("""
        SELECT o FROM OrderJpaEntity o
        WHERE o.userId = :userId AND o.status = :status
        ORDER BY o.createdAt DESC
    """)
    fun findByUserIdAndStatusOptimized(
        @Param("userId") userId: Long,
        @Param("status") status: OrderJpaStatus
    ): List<OrderJpaEntity>

    /**
     * 특정 상태의 모든 주문 조회 (배치 처리용)
     */
    @Query("""
        SELECT o FROM OrderJpaEntity o
        WHERE o.status = :status
        ORDER BY o.createdAt DESC
    """)
    fun findByStatus(@Param("status") status: OrderJpaStatus): List<OrderJpaEntity>

    /**
     * 배치: 주문 상태 대량 업데이트
     * Priority 1 인덱스: idx_user_status_paid (user_id, status, paid_at DESC) 활용
     */
    @Modifying
    @Transactional
    @Query("""
        UPDATE OrderJpaEntity o
        SET o.status = :newStatus, o.updatedAt = CURRENT_TIMESTAMP
        WHERE o.status = :currentStatus AND o.createdAt <= :cutoffDate
    """)
    fun batchUpdateStatus(
        @Param("currentStatus") currentStatus: OrderJpaStatus,
        @Param("newStatus") newStatus: OrderJpaStatus,
        @Param("cutoffDate") cutoffDate: java.time.LocalDateTime
    ): Int

    /**
     * 최근 주문 조회 - 생성일 기준 정렬
     */
    @Query("""
        SELECT o FROM OrderJpaEntity o
        WHERE o.userId = :userId AND o.status IN :statuses
        ORDER BY o.createdAt DESC
        LIMIT :limit
    """)
    fun findRecentOrdersByUserIdAndStatuses(
        @Param("userId") userId: Long,
        @Param("statuses") statuses: List<OrderJpaStatus>,
        @Param("limit") limit: Int
    ): List<OrderJpaEntity>
}
