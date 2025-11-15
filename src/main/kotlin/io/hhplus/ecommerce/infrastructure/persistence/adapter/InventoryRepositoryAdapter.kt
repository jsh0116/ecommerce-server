package io.hhplus.ecommerce.infrastructure.persistence.adapter

import io.hhplus.ecommerce.domain.Inventory
import io.hhplus.ecommerce.infrastructure.repositories.InventoryRepository
import org.springframework.stereotype.Repository

/**
 * InventoryRepository JPA 어댑터
 */
@Repository
class InventoryRepositoryAdapter : InventoryRepository {

    override fun findBySku(sku: String): Inventory? {
        // TODO: 구현 필요
        return null
    }

    override fun save(inventory: Inventory) {
        // TODO: 구현 필요
    }

    override fun update(sku: String, inventory: Inventory) {
        // TODO: 구현 필요
    }
}
