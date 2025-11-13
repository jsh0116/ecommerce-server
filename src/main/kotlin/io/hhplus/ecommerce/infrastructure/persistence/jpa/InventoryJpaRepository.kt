package io.hhplus.ecommerce.infrastructure.persistence.jpa

import io.hhplus.ecommerce.domain.StockStatus
import io.hhplus.ecommerce.infrastructure.persistence.entity.InventoryJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

/**
 * Inventory JPA Repository
 *
 * 성능 최적화:
 * 1. 배치 UPDATE를 통한 대량 작업 최적화
 * 2. 복합 인덱스를 활용한 범위 조회
 * 3. 메모리 정렬 제거 (DB 레벨 정렬)
 */
@Repository
interface InventoryJpaRepository : JpaRepository<InventoryJpaEntity, Long> {

    /**
     * SKU 기준 재고 조회
     *
     * - 인덱스: idx_inventory_sku (unique)
     * - 용도: 단일 SKU 조회는 빠른 응답
     *
     * 쿼리: SELECT * FROM inventory WHERE sku = ?
     */
    fun findBySku(sku: String): InventoryJpaEntity?

    /**
     * 상태별 재고 조회 (정렬)
     *
     * - 인덱스: idx_status_stock (status, available_stock DESC)
     * - 용도: 재고 부족 상품 찾기 (OUT_OF_STOCK 우선)
     *
     * 쿼리: SELECT * FROM inventory
     *       WHERE status = 'OUT_OF_STOCK'
     *       ORDER BY available_stock DESC
     */
    @Query("""
        SELECT i FROM InventoryJpaEntity i
        WHERE i.status = :status
        ORDER BY i.physicalStock - i.reservedStock - i.safetyStock DESC
    """)
    fun findByStatus(@Param("status") status: StockStatus): List<InventoryJpaEntity>

    /**
     * 가용 재고가 충분한 상품 조회
     *
     * - 계산: available_stock = physical_stock - reserved_stock - safety_stock
     * - 인덱스: idx_status_stock (상태 필터 + 정렬)
     *
     * 쿼리: SELECT * FROM inventory
     *       WHERE (physical_stock - reserved_stock - safety_stock) >= ?
     *       ORDER BY available_stock DESC
     */
    @Query("""
        SELECT i FROM InventoryJpaEntity i
        WHERE (i.physicalStock - i.reservedStock - i.safetyStock) >= :minQuantity
        ORDER BY (i.physicalStock - i.reservedStock - i.safetyStock) DESC
    """)
    fun findByAvailableStockGreaterThanEqual(
        @Param("minQuantity") minQuantity: Int
    ): List<InventoryJpaEntity>

    /**
     * 저재고 상품 조회 (재주문 알람 대상)
     *
     * - 조건: available_stock <= reorder_level
     * - 인덱스: idx_inventory_status (status)
     *
     * 쿼리: SELECT * FROM inventory
     *       WHERE (physical_stock - reserved_stock - safety_stock) <= reorder_level
     *       AND status != 'OUT_OF_STOCK'
     */
    @Query("""
        SELECT i FROM InventoryJpaEntity i
        WHERE (i.physicalStock - i.reservedStock - i.safetyStock) <= i.reorderLevel
        AND i.status != io.hhplus.ecommerce.domain.StockStatus.OUT_OF_STOCK
    """)
    fun findLowStockInventories(): List<InventoryJpaEntity>

    /**
     * 배치: 모든 재고의 상태를 업데이트
     *
     * - 성능: 루프 대신 단일 UPDATE 쿼리 실행
     * - 조건: 현재 상태가 IN_STOCK 또는 LOW_STOCK인 모든 항목
     *
     * SQL: UPDATE inventory i
     *      SET i.status = CASE
     *          WHEN (i.physical_stock - i.reserved_stock - i.safety_stock) <= 0 THEN 'OUT_OF_STOCK'
     *          WHEN (i.physical_stock - i.reserved_stock - i.safety_stock) <= 5 THEN 'LOW_STOCK'
     *          ELSE 'IN_STOCK'
     *      END
     *      WHERE i.status IN ('IN_STOCK', 'LOW_STOCK')
     */
    @Modifying
    @Transactional
    @Query("""
        UPDATE InventoryJpaEntity i
        SET i.status = CASE
            WHEN (i.physicalStock - i.reservedStock - i.safetyStock) <= 0 THEN io.hhplus.ecommerce.domain.StockStatus.OUT_OF_STOCK
            WHEN (i.physicalStock - i.reservedStock - i.safetyStock) <= 5 THEN io.hhplus.ecommerce.domain.StockStatus.LOW_STOCK
            ELSE io.hhplus.ecommerce.domain.StockStatus.IN_STOCK
        END,
        i.updatedAt = CURRENT_TIMESTAMP
        WHERE i.status IN (io.hhplus.ecommerce.domain.StockStatus.IN_STOCK, io.hhplus.ecommerce.domain.StockStatus.LOW_STOCK)
    """)
    fun updateAllInventoryStatuses(): Int

    /**
     * 배치: 특정 SKU 목록의 재고를 일괄 증가
     *
     * - 성능: 루프 없이 단일 UPDATE 쿼리
     * - 용도: 재주문 완료 시 다수의 SKU 한 번에 처리
     *
     * SQL: UPDATE inventory i
     *      SET i.physical_stock = i.physical_stock + ?,
     *          i.updated_at = CURRENT_TIMESTAMP
     *      WHERE i.sku IN (?, ?, ...)
     */
    @Modifying
    @Transactional
    @Query("""
        UPDATE InventoryJpaEntity i
        SET i.physicalStock = i.physicalStock + :quantity,
            i.updatedAt = CURRENT_TIMESTAMP
        WHERE i.sku IN :skus
    """)
    fun batchIncreaseStock(
        @Param("skus") skus: List<String>,
        @Param("quantity") quantity: Int
    ): Int

    /**
     * 배치: 특정 SKU 목록의 재고를 일괄 감소
     *
     * - 성능: 루프 없이 단일 UPDATE 쿼리
     * - 용도: 환불 처리 시 다수의 SKU 한 번에 처리
     *
     * SQL: UPDATE inventory i
     *      SET i.physical_stock = i.physical_stock - ?,
     *          i.updated_at = CURRENT_TIMESTAMP
     *      WHERE i.sku IN (?, ?, ...)
     *      AND i.physical_stock >= ?
     */
    @Modifying
    @Transactional
    @Query("""
        UPDATE InventoryJpaEntity i
        SET i.physicalStock = i.physicalStock - :quantity,
            i.updatedAt = CURRENT_TIMESTAMP
        WHERE i.sku IN :skus
        AND i.physicalStock >= :quantity
    """)
    fun batchDecreaseStock(
        @Param("skus") skus: List<String>,
        @Param("quantity") quantity: Int
    ): Int

    /**
     * 배치: 예약된 재고 확정 (예약 -> 실제 판매로 전환)
     *
     * - 성능: 루프 없이 단일 UPDATE 쿼리
     * - 효과: O(N) -> O(1) 성능 개선
     *
     * SQL: UPDATE inventory i
     *      SET i.reserved_stock = 0,
     *          i.updated_at = CURRENT_TIMESTAMP
     *      WHERE i.sku IN (?, ?, ...)
     */
    @Modifying
    @Transactional
    @Query("""
        UPDATE InventoryJpaEntity i
        SET i.reservedStock = 0,
            i.updatedAt = CURRENT_TIMESTAMP
        WHERE i.sku IN :skus
    """)
    fun batchConfirmReservations(@Param("skus") skus: List<String>): Int

    /**
     * 배치: 예약된 재고 취소 (예약 복구)
     *
     * - 성능: 루프 없이 단일 UPDATE 쿼리
     * - 용도: 주문 취소 시 다수 SKU의 예약 복구
     *
     * SQL: UPDATE inventory i
     *      SET i.reserved_stock = 0,
     *          i.physical_stock = i.physical_stock + ?,
     *          i.updated_at = CURRENT_TIMESTAMP
     *      WHERE i.sku IN (?, ?, ...)
     */
    @Modifying
    @Transactional
    @Query("""
        UPDATE InventoryJpaEntity i
        SET i.reservedStock = 0,
            i.physicalStock = i.physicalStock + :quantity,
            i.updatedAt = CURRENT_TIMESTAMP
        WHERE i.sku IN :skus
    """)
    fun batchCancelReservations(
        @Param("skus") skus: List<String>,
        @Param("quantity") quantity: Int
    ): Int

    /**
     * 배치: 상태별 재고 조회 (조건부 업데이트 전)
     *
     * - 인덱스: idx_inventory_status (status)
     * - 용도: 배치 작업 전 대상 선별
     *
     * SQL: SELECT * FROM inventory WHERE status = 'LOW_STOCK'
     */
    @Query("""
        SELECT i FROM InventoryJpaEntity i
        WHERE i.status = :status
    """)
    fun findAllByStatus(@Param("status") status: StockStatus): List<InventoryJpaEntity>

    /**
     * 카운트: 재고 상태별 통계
     *
     * - 용도: 대시보드 지표
     * - 인덱스: idx_inventory_status (status)
     */
    @Query("""
        SELECT COUNT(i) FROM InventoryJpaEntity i
        WHERE i.status = :status
    """)
    fun countByStatus(@Param("status") status: StockStatus): Long
}
