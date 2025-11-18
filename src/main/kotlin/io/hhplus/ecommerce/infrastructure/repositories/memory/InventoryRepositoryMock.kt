package io.hhplus.ecommerce.infrastructure.repositories.memory

import io.hhplus.ecommerce.domain.Inventory
import io.hhplus.ecommerce.infrastructure.repositories.InventoryRepository
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
            physicalStock = 20,
            reservedStock = 0,
            safetyStock = 5,
            lastUpdated = LocalDateTime.now()
        )
        inventory["LEVI-501-BLK-32-REG"] = inv1

        val inv2 = Inventory(
            sku = "LEVI-501-BLK-34-REG",
            physicalStock = 5,
            reservedStock = 0,
            safetyStock = 2,
            lastUpdated = LocalDateTime.now()
        )
        inventory["LEVI-501-BLK-34-REG"] = inv2

        val inv3 = Inventory(
            sku = "NIKE-270-WHT-270-REG",
            physicalStock = 10,
            reservedStock = 0,
            safetyStock = 2,
            lastUpdated = LocalDateTime.now()
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
