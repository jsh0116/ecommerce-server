package io.hhplus.week2.repository.impl

import io.hhplus.week2.domain.Inventory
import io.hhplus.week2.domain.StockStatus
import io.hhplus.week2.repository.InventoryRepository
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

/**
 * InventoryRepository의 실제 구현체
 */
@Repository
@Primary
class InventoryRepositoryImpl : InventoryRepository {

    override fun findBySku(sku: String): Inventory? {
        TODO("Not yet implemented")
    }

    override fun save(inv: Inventory) {
        TODO("Not yet implemented")
    }

    override fun update(sku: String, inv: Inventory) {
        TODO("Not yet implemented")
    }
}
