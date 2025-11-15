package io.hhplus.ecommerce.infrastructure.persistence.repository

import io.hhplus.ecommerce.infrastructure.persistence.entity.InventoryJpaEntity
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

/**
 * 재고 JPA Repository
 *
 * 비관적 락(PESSIMISTIC_WRITE)을 통해 동시성 제어
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
}
