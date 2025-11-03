package io.hhplus.week2.service

import io.hhplus.week2.repository.mock.InventoryRepositoryMock
import io.hhplus.week2.service.impl.InventoryServiceImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.format.DateTimeFormatter

@DisplayName("InventoryServiceImpl 테스트")
class InventoryServiceTest {

    private lateinit var inventoryRepository: InventoryRepositoryMock
    private lateinit var inventoryService: InventoryService

    private val dateFormatter = DateTimeFormatter.ISO_DATE_TIME

    @BeforeEach
    fun setUp() {
        inventoryRepository = InventoryRepositoryMock()
        inventoryService = InventoryServiceImpl(inventoryRepository)
    }

    @Test
    @DisplayName("SKU 코드로 재고 정보를 조회할 수 있다")
    fun testGetInventoryBySku() {
        // when
        val result = inventoryService.getInventoryBySku("LEVI-501-BLK-32-REG")

        // then
        assertThat(result).isNotNull
        assertThat(result?.sku).isEqualTo("LEVI-501-BLK-32-REG")
        assertThat(result?.available).isEqualTo(15)
        assertThat(result?.reserved).isEqualTo(0)
    }

    @Test
    @DisplayName("재고를 예약할 수 있다")
    fun testReserveInventory() {
        // when
        val result = inventoryService.reserveInventory("LEVI-501-BLK-32-REG", 5, 15)

        // then
        assertThat(result).isNotNull
        assertThat(result?.sku).isEqualTo("LEVI-501-BLK-32-REG")
        assertThat(result?.quantity).isEqualTo(5)
    }

    @Test
    @DisplayName("재고 부족 시 예약 실패")
    fun testReserveInventoryFail() {
        // when
        val result = inventoryService.reserveInventory("LEVI-501-BLK-32-REG", 20, 15)

        // then
        assertThat(result).isNull()
    }

    @Test
    @DisplayName("재고를 차감할 수 있다")
    fun testDeductInventory() {
        // when
        val result = inventoryService.deductInventory("LEVI-501-BLK-32-REG", 5)

        // then
        assertThat(result).isTrue
    }

    @Test
    @DisplayName("재고 차감 시 LOW_STOCK 상태로 변경된다")
    fun testDeductInventoryLowStock() {
        // when
        val result = inventoryService.deductInventory("LEVI-501-BLK-34-REG", 3)

        // then
        assertThat(result).isTrue
    }

    @Test
    @DisplayName("재고 부족 시 차감 실패")
    fun testDeductInventoryFail() {
        // when
        val result = inventoryService.deductInventory("LEVI-501-BLK-32-REG", 25)

        // then
        assertThat(result).isFalse
    }

    @Test
    @DisplayName("예약된 재고를 취소할 수 있다")
    fun testCancelReservation() {
        // given - 먼저 재고를 예약
        inventoryService.reserveInventory("LEVI-501-BLK-32-REG", 5, 15)

        // when
        val result = inventoryService.cancelReservation("LEVI-501-BLK-32-REG", 5)

        // then
        assertThat(result).isTrue
    }

    @Test
    @DisplayName("존재하지 않는 SKU의 예약 취소는 실패한다")
    fun testCancelReservationNotFound() {
        // when
        val result = inventoryService.cancelReservation("nonexistent_sku", 5)

        // then
        assertThat(result).isFalse
    }
}
