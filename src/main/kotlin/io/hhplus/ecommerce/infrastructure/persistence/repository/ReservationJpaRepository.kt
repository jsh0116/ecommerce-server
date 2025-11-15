package io.hhplus.ecommerce.infrastructure.persistence.repository

import io.hhplus.ecommerce.infrastructure.persistence.entity.ReservationJpaEntity
import io.hhplus.ecommerce.infrastructure.persistence.entity.ReservationStatusJpa
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

/**
 * 재고 예약 JPA Repository
 *
 * Saga 패턴: 주문 생성 시 예약, 15분 TTL로 자동 만료
 */
@Repository
interface ReservationJpaRepository : JpaRepository<ReservationJpaEntity, Long> {

    /**
     * 주문별 예약 조회
     */
    fun findByOrderId(orderId: Long): ReservationJpaEntity?

    /**
     * SKU별 예약 조회
     */
    fun findBySku(sku: String): List<ReservationJpaEntity>

    /**
     * 만료된 예약 조회 (Saga 스케줄러용)
     * 상태가 ACTIVE이고 현재 시간이 expiresAt을 초과한 경우
     */
    @Query("""
        SELECT r FROM ReservationJpaEntity r
        WHERE r.status = 'ACTIVE'
        AND r.expiresAt <= CURRENT_TIMESTAMP
    """)
    fun findExpiredReservations(): List<ReservationJpaEntity>

    /**
     * 활성 예약 조회
     */
    fun findByStatus(status: ReservationStatusJpa): List<ReservationJpaEntity>

    /**
     * SKU별, 상태별 예약 조회
     */
    fun findBySkuAndStatus(sku: String, status: ReservationStatusJpa): List<ReservationJpaEntity>
}
