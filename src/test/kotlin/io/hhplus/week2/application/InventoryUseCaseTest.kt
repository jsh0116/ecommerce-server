package io.hhplus.week2.application

import io.hhplus.week2.domain.Inventory
import io.hhplus.week2.repository.InventoryRepository
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("InventoryUseCase 테스트")
class InventoryUseCaseTest {

    private lateinit var inventoryUseCase: InventoryUseCase
    private lateinit var inventoryRepository: InventoryRepository

    @BeforeEach
    fun setUp() {
        inventoryRepository = mockk()
        inventoryUseCase = InventoryUseCase(inventoryRepository)
    }

    @Test
    @DisplayName("재고를 조회할 수 있다")
    fun testGetInventoryBySku() {
        // Given
        val sku = "SKU-001"
        val inventory = Inventory(
            sku = sku,
            physicalStock = 100,
            reservedStock = 20,
            safetyStock = 10
        )

        every { inventoryRepository.findBySku(sku) } returns inventory

        // When
        val result = inventoryUseCase.getInventoryBySku(sku)

        // Then
        assert(result != null)
        assert(result?.sku == sku)
        assert(result?.physicalStock == 100)
    }

    @Test
    @DisplayName("재고를 예약할 수 있다")
    fun testReserveInventory() {
        // Given
        val sku = "SKU-001"
        val quantity = 30
        val ttlMinutes = 15
        val inventory = Inventory(
            sku = sku,
            physicalStock = 100,
            reservedStock = 20,
            safetyStock = 10
        )

        every { inventoryRepository.findBySku(sku) } returns inventory
        every { inventoryRepository.save(any()) } returnsArgument 0

        // When
        val result = inventoryUseCase.reserveInventory(sku, quantity, ttlMinutes)

        // Then
        assert(result != null)
        assert(result?.sku == sku)
        assert(result?.quantity == quantity)
        verify { inventoryRepository.save(any()) }
    }

    @Test
    @DisplayName("재고가 부족하면 예약이 실패한다")
    fun testReserveInventoryWithInsufficientStock() {
        // Given
        val sku = "SKU-001"
        val quantity = 100
        val ttlMinutes = 15
        val inventory = Inventory(
            sku = sku,
            physicalStock = 100,
            reservedStock = 80,
            safetyStock = 10
        )

        every { inventoryRepository.findBySku(sku) } returns inventory

        // When
        val result = inventoryUseCase.reserveInventory(sku, quantity, ttlMinutes)

        // Then
        assert(result == null)
        verify(exactly = 0) { inventoryRepository.save(any()) }
    }

    @Test
    @DisplayName("재고를 차감할 수 있다")
    fun testDeductInventory() {
        // Given
        val sku = "SKU-001"
        val quantity = 20
        val inventory = Inventory(
            sku = sku,
            physicalStock = 100,
            reservedStock = 30,
            safetyStock = 10
        )

        every { inventoryRepository.findBySku(sku) } returns inventory
        every { inventoryRepository.save(any()) } returnsArgument 0

        // When
        val result = inventoryUseCase.deductInventory(sku, quantity)

        // Then
        assert(result)
        verify { inventoryRepository.save(any()) }
    }

    @Test
    @DisplayName("예약 재고가 부족하면 차감이 실패한다")
    fun testDeductInventoryWithInsufficientReserved() {
        // Given
        val sku = "SKU-001"
        val quantity = 50
        val inventory = Inventory(
            sku = sku,
            physicalStock = 100,
            reservedStock = 30,
            safetyStock = 10
        )

        every { inventoryRepository.findBySku(sku) } returns inventory

        // When
        val result = inventoryUseCase.deductInventory(sku, quantity)

        // Then
        assert(!result)
        verify(exactly = 0) { inventoryRepository.save(any()) }
    }

    @Test
    @DisplayName("예약을 취소할 수 있다")
    fun testCancelReservation() {
        // Given
        val sku = "SKU-001"
        val quantity = 20
        val inventory = Inventory(
            sku = sku,
            physicalStock = 100,
            reservedStock = 30,
            safetyStock = 10
        )

        every { inventoryRepository.findBySku(sku) } returns inventory
        every { inventoryRepository.save(any()) } returnsArgument 0

        // When
        val result = inventoryUseCase.cancelReservation(sku, quantity)

        // Then
        assert(result)
        verify { inventoryRepository.save(any()) }
    }

    @Test
    @DisplayName("재고를 복구할 수 있다")
    fun testRestoreInventory() {
        // Given
        val sku = "SKU-001"
        val quantity = 20
        val inventory = Inventory(
            sku = sku,
            physicalStock = 100,
            reservedStock = 20,
            safetyStock = 10
        )

        every { inventoryRepository.findBySku(sku) } returns inventory
        every { inventoryRepository.save(any()) } returnsArgument 0

        // When
        val result = inventoryUseCase.restoreInventory(sku, quantity)

        // Then
        assert(result)
        verify { inventoryRepository.save(any()) }
    }

    @Test
    @DisplayName("재고 차감 중 예외 발생 시 false를 반환한다")
    fun testDeductInventoryWithException() {
        // Given
        val sku = "SKU-001"
        val quantity = 20
        val inventory = Inventory(
            sku = sku,
            physicalStock = 100,
            reservedStock = 30,
            safetyStock = 10
        )

        every { inventoryRepository.findBySku(sku) } returns inventory
        every { inventoryRepository.save(any()) } throws RuntimeException("저장 실패")

        // When
        val result = inventoryUseCase.deductInventory(sku, quantity)

        // Then
        assert(!result)
    }

    @Test
    @DisplayName("예약 취소 중 예외 발생 시 false를 반환한다")
    fun testCancelReservationWithException() {
        // Given
        val sku = "SKU-001"
        val quantity = 20
        val inventory = Inventory(
            sku = sku,
            physicalStock = 100,
            reservedStock = 30,
            safetyStock = 10
        )

        every { inventoryRepository.findBySku(sku) } returns inventory
        every { inventoryRepository.save(any()) } throws RuntimeException("저장 실패")

        // When
        val result = inventoryUseCase.cancelReservation(sku, quantity)

        // Then
        assert(!result)
    }

    @Test
    @DisplayName("차감 할 재고 정보가 없으면 false를 반환한다")
    fun testDeductInventoryNotFound() {
        // Given
        val sku = "SKU-001"
        val quantity = 20

        every { inventoryRepository.findBySku(sku) } returns null

        // When
        val result = inventoryUseCase.deductInventory(sku, quantity)

        // Then
        assert(!result)
        verify(exactly = 0) { inventoryRepository.save(any()) }
    }

    @Test
    @DisplayName("취소할 재고 정보가 없으면 false를 반환한다")
    fun testCancelReservationNotFound() {
        // Given
        val sku = "SKU-001"
        val quantity = 20

        every { inventoryRepository.findBySku(sku) } returns null

        // When
        val result = inventoryUseCase.cancelReservation(sku, quantity)

        // Then
        assert(!result)
        verify(exactly = 0) { inventoryRepository.save(any()) }
    }

    @Test
    @DisplayName("복구할 재고 정보가 없으면 false를 반환한다")
    fun testRestoreInventoryNotFound() {
        // Given
        val sku = "SKU-001"
        val quantity = 20

        every { inventoryRepository.findBySku(sku) } returns null

        // When
        val result = inventoryUseCase.restoreInventory(sku, quantity)

        // Then
        assert(!result)
        verify(exactly = 0) { inventoryRepository.save(any()) }
    }
}
