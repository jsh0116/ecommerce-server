package io.hhplus.week2.domain

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

@DisplayName("Inventory 도메인 테스트")
class InventoryTest {

    @Test
    @DisplayName("가용 재고를 계산할 수 있다")
    fun testGetAvailableStock() {
        // Given
        val inventory = Inventory(
            sku = "SKU-001",
            physicalStock = 100,
            reservedStock = 20,
            safetyStock = 10
        )

        // When
        val availableStock = inventory.getAvailableStock()

        // Then
        assert(availableStock == 70) // 100 - 20 - 10
    }

    @Test
    @DisplayName("재고 상태를 반환할 수 있다")
    fun testGetStatus() {
        // Given
        val inStockInventory = Inventory(
            sku = "SKU-001",
            physicalStock = 100,
            reservedStock = 20,
            safetyStock = 10
        )

        val lowStockInventory = Inventory(
            sku = "SKU-002",
            physicalStock = 10,
            reservedStock = 3,
            safetyStock = 2
        )

        val outOfStockInventory = Inventory(
            sku = "SKU-003",
            physicalStock = 10,
            reservedStock = 8,
            safetyStock = 2
        )

        // When & Then
        assert(inStockInventory.getStatus() == StockStatus.IN_STOCK)
        assert(lowStockInventory.getStatus() == StockStatus.LOW_STOCK)
        assert(outOfStockInventory.getStatus() == StockStatus.OUT_OF_STOCK)
    }

    @Test
    @DisplayName("재고를 예약할 수 있다")
    fun testReserve() {
        // Given
        val inventory = Inventory(
            sku = "SKU-001",
            physicalStock = 100,
            reservedStock = 20,
            safetyStock = 10
        )

        // When
        inventory.reserve(30)

        // Then
        assert(inventory.reservedStock == 50)
        assert(inventory.getAvailableStock() == 40)
    }

    @Test
    @DisplayName("가용 재고가 부족하면 예약 시 예외를 발생시킨다")
    fun testReserveWithInsufficientStock() {
        // Given
        val inventory = Inventory(
            sku = "SKU-001",
            physicalStock = 100,
            reservedStock = 80,
            safetyStock = 10
        )

        // When & Then
        val exception = assertThrows<IllegalStateException> {
            inventory.reserve(20)
        }
        assert(exception.message?.contains("재고 부족") ?: false)
    }

    @Test
    @DisplayName("예약을 확정할 수 있다")
    fun testConfirmReservation() {
        // Given
        val inventory = Inventory(
            sku = "SKU-001",
            physicalStock = 100,
            reservedStock = 30,
            safetyStock = 10
        )

        // When
        inventory.confirmReservation(20)

        // Then
        assert(inventory.physicalStock == 80)
        assert(inventory.reservedStock == 10)
    }

    @Test
    @DisplayName("예약 수량보다 확정 수량이 많으면 예외를 발생시킨다")
    fun testConfirmReservationWithExcessQuantity() {
        // Given
        val inventory = Inventory(
            sku = "SKU-001",
            physicalStock = 100,
            reservedStock = 10,
            safetyStock = 10
        )

        // When & Then
        val exception = assertThrows<IllegalStateException> {
            inventory.confirmReservation(20)
        }
        assert(exception.message?.contains("예약 수량 부족") ?: false)
    }

    @Test
    @DisplayName("예약을 취소할 수 있다")
    fun testCancelReservation() {
        // Given
        val inventory = Inventory(
            sku = "SKU-001",
            physicalStock = 100,
            reservedStock = 30,
            safetyStock = 10
        )

        // When
        inventory.cancelReservation(20)

        // Then
        assert(inventory.reservedStock == 10)
        assert(inventory.getAvailableStock() == 80)
    }

    @Test
    @DisplayName("예약 취소 수량이 예약 수량보다 많으면 예외를 발생시킨다")
    fun testCancelReservationWithExcessQuantity() {
        // Given
        val inventory = Inventory(
            sku = "SKU-001",
            physicalStock = 100,
            reservedStock = 10,
            safetyStock = 10
        )

        // When & Then
        val exception = assertThrows<IllegalStateException> {
            inventory.cancelReservation(20)
        }
        assert(exception.message?.contains("예약 취소 불가") ?: false)
    }

    @Test
    @DisplayName("재고를 복구할 수 있다")
    fun testRestoreStock() {
        // Given
        val inventory = Inventory(
            sku = "SKU-001",
            physicalStock = 100,
            reservedStock = 20,
            safetyStock = 10
        )

        // When
        inventory.restoreStock(30)

        // Then
        assert(inventory.physicalStock == 130)
        assert(inventory.getAvailableStock() == 100)
    }

    @Test
    @DisplayName("재고를 조정할 수 있다")
    fun testAdjustStock() {
        // Given
        val inventory = Inventory(
            sku = "SKU-001",
            physicalStock = 100,
            reservedStock = 20,
            safetyStock = 10
        )

        // When
        inventory.adjustStock(200)

        // Then
        assert(inventory.physicalStock == 200)
    }

    @Test
    @DisplayName("음수로 재고를 조정하면 예외를 발생시킨다")
    fun testAdjustStockWithNegativeValue() {
        // Given
        val inventory = Inventory(
            sku = "SKU-001",
            physicalStock = 100,
            reservedStock = 20,
            safetyStock = 10
        )

        // When & Then
        val exception = assertThrows<IllegalArgumentException> {
            inventory.adjustStock(-10)
        }
        assert(exception.message?.contains("0 이상") ?: false)
    }

    @Test
    @DisplayName("예약 가능 여부를 확인할 수 있다")
    fun testCanReserve() {
        // Given
        val inventory = Inventory(
            sku = "SKU-001",
            physicalStock = 100,
            reservedStock = 20,
            safetyStock = 10
        )

        // When & Then
        assert(inventory.canReserve(50))
        assert(inventory.canReserve(70))
        assert(!inventory.canReserve(71))
        assert(!inventory.canReserve(100))
    }
}
