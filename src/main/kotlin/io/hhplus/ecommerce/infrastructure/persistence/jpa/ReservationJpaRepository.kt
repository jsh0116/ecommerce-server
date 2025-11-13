package io.hhplus.ecommerce.infrastructure.persistence.jpa

import io.hhplus.ecommerce.infrastructure.persistence.entity.ReservationJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * Reservation JPA Repository
 *
 * 성능 최적화:
 * 1. 배치 UPDATE로 만료된 예약 한 번에 처리
 * 2. 복합 인덱스로 스캔 범위 축소
 * 3. 상태 변경 쿼리 최적화
 */
@Repository
interface ReservationJpaRepository : JpaRepository<ReservationJpaEntity, String> {

    /**
     * 만료된 예약 조회 (ACTIVE 상태 + 현재 시간 > expires_at)
     *
     * - 인덱스: idx_status_expires (status, expires_at)
     * - 스캔 범위: ACTIVE 상태만 확인
     * - 용도: 배치 작업으로 TTL 만료된 예약 찾기
     *
     * 쿼리: SELECT * FROM reservations
     *       WHERE status = 'ACTIVE' AND expires_at <= CURRENT_TIMESTAMP
     *       ORDER BY expires_at ASC
     */
    @Query("""
        SELECT r FROM ReservationJpaEntity r
        WHERE r.status = 'ACTIVE'
        AND r.expiresAt <= CURRENT_TIMESTAMP
        ORDER BY r.expiresAt ASC
    """)
    fun findExpiredReservations(): List<ReservationJpaEntity>

    /**
     * 특정 주문의 예약 조회
     *
     * - 용도: 주문 취소 시 해당 예약 찾기
     * - 성능: SKU별 정렬로 배치 처리 최적화 가능
     *
     * 쿼리: SELECT * FROM reservations WHERE order_id = ?
     */
    fun findByOrderId(orderId: Long): List<ReservationJpaEntity>

    /**
     * SKU별 활성 예약 조회
     *
     * - 인덱스: idx_sku_status_expires (sku, status, expires_at)
     * - 용도: SKU별 총 예약 수량 계산
     *
     * 쿼리: SELECT * FROM reservations
     *       WHERE sku = ? AND status = 'ACTIVE'
     */
    @Query("""
        SELECT r FROM ReservationJpaEntity r
        WHERE r.sku = :sku
        AND r.status = 'ACTIVE'
    """)
    fun findActiveBySkus(@Param("sku") sku: String): List<ReservationJpaEntity>

    /**
     * 배치: 만료된 예약 상태 일괄 변경 (ACTIVE -> EXPIRED)
     *
     * - 성능: 만료 조회 + 루프 대신 단일 UPDATE 쿼리
     * - 효과: O(N) 루프 -> O(1) 배치 처리
     * - 복합 인덱스: idx_status_expires (status, expires_at)
     *
     * SQL: UPDATE reservations r
     *      SET r.status = 'EXPIRED',
     *          r.updated_at = CURRENT_TIMESTAMP
     *      WHERE r.status = 'ACTIVE' AND r.expires_at <= CURRENT_TIMESTAMP
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
     * 배치: 특정 예약 상태 일괄 변경
     *
     * - 성능: 상태별 일괄 업데이트
     * - 용도: 예약 확정 (ACTIVE -> CONFIRMED)
     *
     * SQL: UPDATE reservations r
     *      SET r.status = ?,
     *          r.updated_at = CURRENT_TIMESTAMP
     *      WHERE r.id IN (?, ?, ...)
     */
    @Modifying
    @Transactional
    @Query("""
        UPDATE ReservationJpaEntity r
        SET r.status = :newStatus,
            r.updatedAt = CURRENT_TIMESTAMP
        WHERE r.id IN :reservationIds
    """)
    fun batchUpdateStatus(
        @Param("reservationIds") reservationIds: List<String>,
        @Param("newStatus") newStatus: String
    ): Int

    /**
     * 배치: 주문별 예약 상태 일괄 변경
     *
     * - 성능: ORDER_ID별 일괄 처리
     * - 용도: 주문 취소 시 해당 예약 모두 취소 (ACTIVE -> CANCELLED)
     *
     * SQL: UPDATE reservations r
     *      SET r.status = ?,
     *          r.updated_at = CURRENT_TIMESTAMP
     *      WHERE r.order_id = ? AND r.status = 'ACTIVE'
     */
    @Modifying
    @Transactional
    @Query("""
        UPDATE ReservationJpaEntity r
        SET r.status = :newStatus,
            r.updatedAt = CURRENT_TIMESTAMP
        WHERE r.orderId = :orderId
        AND r.status = 'ACTIVE'
    """)
    fun cancelByOrderId(
        @Param("orderId") orderId: Long,
        @Param("newStatus") newStatus: String = "CANCELLED"
    ): Int

    /**
     * 배치: 여러 SKU의 만료된 예약 일괄 처리
     *
     * - 성능: SKU별 범위 조회 후 일괄 업데이트
     * - 용도: 특정 SKU들의 만료된 예약만 처리
     *
     * SQL: UPDATE reservations r
     *      SET r.status = 'EXPIRED',
     *          r.updated_at = CURRENT_TIMESTAMP
     *      WHERE r.sku IN (?, ?, ...)
     *      AND r.status = 'ACTIVE'
     *      AND r.expires_at <= CURRENT_TIMESTAMP
     */
    @Modifying
    @Transactional
    @Query("""
        UPDATE ReservationJpaEntity r
        SET r.status = 'EXPIRED',
            r.updatedAt = CURRENT_TIMESTAMP
        WHERE r.sku IN :skus
        AND r.status = 'ACTIVE'
        AND r.expiresAt <= CURRENT_TIMESTAMP
    """)
    fun expireBySkus(@Param("skus") skus: List<String>): Int

    /**
     * 복합 쿼리: 특정 시간 범위의 만료된 예약 찾기
     *
     * - 인덱스: idx_status_expires (status, expires_at)
     * - 용도: 시간대별 배치 처리 (예: 30분마다 실행)
     *
     * SQL: SELECT * FROM reservations
     *      WHERE status = 'ACTIVE'
     *      AND expires_at > ? AND expires_at <= ?
     */
    @Query("""
        SELECT r FROM ReservationJpaEntity r
        WHERE r.status = 'ACTIVE'
        AND r.expiresAt > :from
        AND r.expiresAt <= :to
        ORDER BY r.expiresAt ASC
    """)
    fun findReservationsExpiredBetween(
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime
    ): List<ReservationJpaEntity>

    /**
     * 통계: 상태별 예약 수
     *
     * - 인덱스: idx_status_expires (status)
     * - 용도: 모니터링 및 대시보드
     *
     * SQL: SELECT COUNT(*) FROM reservations WHERE status = ?
     */
    @Query("""
        SELECT COUNT(r) FROM ReservationJpaEntity r
        WHERE r.status = :status
    """)
    fun countByStatus(@Param("status") status: String): Long

    /**
     * 통계: SKU별 예약 수량 합계
     *
     * - 인덱스: idx_status_expires (sku, status)
     * - 용도: 재고별 총 예약 수량 조회
     *
     * SQL: SELECT SUM(r.quantity) FROM reservations r
     *      WHERE r.sku = ? AND r.status = 'ACTIVE'
     */
    @Query("""
        SELECT COALESCE(SUM(r.quantity), 0) FROM ReservationJpaEntity r
        WHERE r.sku = :sku
        AND r.status = 'ACTIVE'
    """)
    fun sumReservedQuantityBySku(@Param("sku") sku: String): Long
}
