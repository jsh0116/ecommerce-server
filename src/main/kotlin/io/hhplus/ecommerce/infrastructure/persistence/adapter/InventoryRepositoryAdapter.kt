package io.hhplus.ecommerce.infrastructure.persistence.adapter

import io.hhplus.ecommerce.domain.Inventory
import io.hhplus.ecommerce.domain.StockStatus
import io.hhplus.ecommerce.infrastructure.repositories.InventoryRepository
import io.hhplus.ecommerce.infrastructure.persistence.repository.InventoryJpaRepository
import io.hhplus.ecommerce.infrastructure.persistence.entity.InventoryJpaEntity
import org.springframework.stereotype.Repository

/**
 * InventoryRepository JPA 어댑터
 *
 * Domain Inventory와 JPA Entity InventoryJpaEntity 간의 변환을 담당합니다.
 */
@Repository
class InventoryRepositoryAdapter(
    private val jpaRepository: InventoryJpaRepository
) : InventoryRepository {

    override fun findBySku(sku: String): Inventory? {
        return jpaRepository.findBySku(sku)?.toDomain()
    }

    override fun save(inventory: Inventory) {
        val entity = inventory.toEntity()
        jpaRepository.save(entity)
    }

    override fun update(sku: String, inventory: Inventory) {
        val existingEntity = jpaRepository.findBySku(sku)
            ?: throw IllegalArgumentException("재고를 찾을 수 없습니다: $sku")

        existingEntity.apply {
            physicalStock = inventory.physicalStock
            reservedStock = inventory.reservedStock
            safetyStock = inventory.safetyStock
            updateStatus()
        }
        jpaRepository.save(existingEntity)
    }

    /**
     * Domain Inventory를 JPA Entity로 변환
     */
    private fun Inventory.toEntity(): InventoryJpaEntity {
        return InventoryJpaEntity(
            sku = this.sku,
            physicalStock = this.physicalStock,
            reservedStock = this.reservedStock,
            safetyStock = this.safetyStock,
            status = this.getStatus(),
            createdAt = this.lastUpdated,
            updatedAt = this.lastUpdated
        )
    }

    /**
     * JPA Entity를 Domain Inventory로 변환
     */
    private fun InventoryJpaEntity.toDomain(): Inventory {
        return Inventory(
            sku = this.sku,
            physicalStock = this.physicalStock,
            reservedStock = this.reservedStock,
            safetyStock = this.safetyStock,
            lastUpdated = this.updatedAt
        )
    }
}
