package io.hhplus.ecommerce.infrastructure.persistence.repository

import io.hhplus.ecommerce.infrastructure.persistence.entity.ReservationJpaEntity
import io.hhplus.ecommerce.infrastructure.persistence.entity.ReservationStatusJpa
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

/**
 * 재고 예약 JPA Repository
 *
 * Saga 패턴: 주문 생성 시 예약, 15분 TTL로 자동 만료
 * 배치 UPDATE로 O(N) -> O(1) 성능 최적화
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

    /**
     * 배치: 만료된 예약 상태 일괄 변경 (ACTIVE -> EXPIRED)
     * 성능: 루프 없이 단일 UPDATE 쿼리 (O(N) -> O(1))
     */
    @Modifying
    @Transactional
    @Query("""
        UPDATE ReservationJpaEntity r
        SET r.status = 'EXPIRED',
            r.updatedAt = CURRENT_TIMESTAMP
        WHERE r.status = 'ACTIVE'
        AND r.expiresAt <= CURRENT_TIMESTAMP
    """)
    fun expireExpiredReservations(): Int

    /**
     * 배치: 주문별 예약 상태 일괄 변경 (ACTIVE -> CANCELLED)
     * 성능: 루프 없이 단일 UPDATE 쿼리
     */
    @Modifying
    @Transactional
    @Query("""
        UPDATE ReservationJpaEntity r
        SET r.status = 'CANCELLED',
            r.updatedAt = CURRENT_TIMESTAMP
        WHERE r.orderId = :orderId
        AND r.status = 'ACTIVE'
    """)
    fun cancelByOrderId(@Param("orderId") orderId: Long): Int

    /**
     * 통계: 상태별 예약 수
     */
    @Query("""
        SELECT COUNT(r) FROM ReservationJpaEntity r
        WHERE r.status = :status
    """)
    fun countByStatus(@Param("status") status: String): Long

    /**
     * 통계: SKU별 예약 수량 합계 (DB 레벨 집계)
     */
    @Query("""
        SELECT COALESCE(SUM(r.quantity), 0) FROM ReservationJpaEntity r
        WHERE r.sku = :sku
        AND r.status = 'ACTIVE'
    """)
    fun sumReservedQuantityBySku(@Param("sku") sku: String): Long
}
