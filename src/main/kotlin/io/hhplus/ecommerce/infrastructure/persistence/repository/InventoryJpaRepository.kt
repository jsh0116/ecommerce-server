package io.hhplus.ecommerce.infrastructure.persistence.repository

import io.hhplus.ecommerce.infrastructure.persistence.entity.InventoryJpaEntity
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

/**
 * 재고 JPA Repository
 *
 * 비관적 락(PESSIMISTIC_WRITE)을 통해 동시성 제어
 * 배치 UPDATE로 O(N) -> O(1) 성능 최적화
 */
@Repository
interface InventoryJpaRepository : JpaRepository<InventoryJpaEntity, Long> {

    /**
     * SKU로 재고 조회 (비관적 락 적용)
     * 다른 트랜잭션이 접근 시 대기
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM InventoryJpaEntity i WHERE i.sku = :sku")
    fun findBySkuForUpdate(@Param("sku") sku: String): InventoryJpaEntity?

    /**
     * SKU로 재고 조회 (락 없음)
     */
    fun findBySku(sku: String): InventoryJpaEntity?

    /**
     * 배치: 특정 SKU 목록의 재고를 일괄 증가
     * 성능: 루프 없이 단일 UPDATE 쿼리
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
     * 성능: 루프 없이 단일 UPDATE 쿼리
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
}
