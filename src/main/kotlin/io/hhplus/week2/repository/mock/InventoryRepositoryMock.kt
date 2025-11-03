package io.hhplus.week2.repository.mock

import io.hhplus.week2.domain.Inventory
import io.hhplus.week2.domain.StockStatus
import io.hhplus.week2.repository.InventoryRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

/**
 * InventoryRepository의 메모리 기반 구현체
 * MockData로 동작합니다.
 */
@Repository
class InventoryRepositoryMock : InventoryRepository {

    private val inventory = ConcurrentHashMap<String, Inventory>()
    private val dateFormatter = DateTimeFormatter.ISO_DATE_TIME

    init {
        initializeMockData()
    }

    private fun initializeMockData() {
        // 재고 정보
        val inv1 = Inventory(
            sku = "LEVI-501-BLK-32-REG",
            available = 15,
            reserved = 0,
            physical = 20,
            safetyStock = 5,
            status = StockStatus.IN_STOCK,
            lastUpdated = LocalDateTime.now().format(dateFormatter)
        )
        inventory["LEVI-501-BLK-32-REG"] = inv1

        val inv2 = Inventory(
            sku = "LEVI-501-BLK-34-REG",
            available = 3,
            reserved = 0,
            physical = 5,
            safetyStock = 2,
            status = StockStatus.LOW_STOCK,
            lastUpdated = LocalDateTime.now().format(dateFormatter)
        )
        inventory["LEVI-501-BLK-34-REG"] = inv2

        val inv3 = Inventory(
            sku = "NIKE-270-WHT-270-REG",
            available = 8,
            reserved = 0,
            physical = 10,
            safetyStock = 2,
            status = StockStatus.IN_STOCK,
            lastUpdated = LocalDateTime.now().format(dateFormatter)
        )
        inventory["NIKE-270-WHT-270-REG"] = inv3
    }

    override fun findBySku(sku: String): Inventory? {
        return inventory[sku]
    }

    override fun save(inv: Inventory) {
        inventory[inv.sku] = inv
    }

    override fun update(sku: String, inv: Inventory) {
        inventory[sku] = inv
    }
}
