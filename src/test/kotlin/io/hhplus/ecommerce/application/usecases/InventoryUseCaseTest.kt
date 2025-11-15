package io.hhplus.ecommerce.application.usecases

import io.hhplus.ecommerce.domain.Inventory
import io.hhplus.ecommerce.infrastructure.repositories.InventoryRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("InventoryUseCase 테스트")
class InventoryUseCaseTest {

    private val inventoryRepository = mockk<InventoryRepository>()
    private val useCase = InventoryUseCase(inventoryRepository)

    @Nested
    @DisplayName("getInventoryBySku 테스트")
    inner class GetInventoryBySkuTest {
        @Test
        fun `SKU로 재고 조회 성공`() {
            val inventory = Inventory(sku = "SKU-001", physicalStock = 100)
            every { inventoryRepository.findBySku("SKU-001") } returns inventory

            val result = useCase.getInventoryBySku("SKU-001")

            assertThat(result).isNotNull
            assertThat(result?.sku).isEqualTo("SKU-001")
        }

        @Test
        fun `존재하지 않는 SKU는 null 반환`() {
            every { inventoryRepository.findBySku("INVALID") } returns null

            val result = useCase.getInventoryBySku("INVALID")

            assertThat(result).isNull()
        }
    }

    @Nested
    @DisplayName("reserveInventory 테스트")
    inner class ReserveInventoryTest {
        @Test
        fun `재고 예약 성공`() {
            val inventory = Inventory(sku = "SKU-001", physicalStock = 100, reservedStock = 0)
            every { inventoryRepository.findBySku("SKU-001") } returns inventory
            every { inventoryRepository.save(inventory) } just runs

            val result = useCase.reserveInventory("SKU-001", 20, 15)

            assertThat(result).isNotNull
            assertThat(result?.sku).isEqualTo("SKU-001")
            assertThat(result?.quantity).isEqualTo(20)
            verify { inventoryRepository.save(inventory) }
        }

        @Test
        fun `재고 부족 시 null 반환`() {
            val inventory = Inventory(sku = "SKU-001", physicalStock = 10, reservedStock = 0)
            every { inventoryRepository.findBySku("SKU-001") } returns inventory

            val result = useCase.reserveInventory("SKU-001", 20, 15)

            assertThat(result).isNull()
        }

        @Test
        fun `재고 예약 시 물리 재고보다 많은 수량 예약 불가`() {
            val inventory = Inventory(sku = "SKU-001", physicalStock = 50, reservedStock = 0)
            every { inventoryRepository.findBySku("SKU-001") } returns inventory

            val result = useCase.reserveInventory("SKU-001", 100, 15)

            assertThat(result).isNull()
        }
    }

    @Nested
    @DisplayName("deductInventory 테스트")
    inner class DeductInventoryTest {
        @Test
        fun `재고 차감 성공`() {
            val inventory = Inventory(sku = "SKU-001", physicalStock = 100, reservedStock = 20)
            every { inventoryRepository.findBySku("SKU-001") } returns inventory
            every { inventoryRepository.save(inventory) } just runs

            val result = useCase.deductInventory("SKU-001", 20)

            assertThat(result).isTrue
            verify { inventoryRepository.save(inventory) }
        }

        @Test
        fun `존재하지 않는 SKU는 false 반환`() {
            every { inventoryRepository.findBySku("INVALID") } returns null

            val result = useCase.deductInventory("INVALID", 10)

            assertThat(result).isFalse
        }

        @Test
        fun `예약되지 않은 재고는 차감 불가`() {
            val inventory = Inventory(sku = "SKU-001", physicalStock = 100, reservedStock = 0)
            every { inventoryRepository.findBySku("SKU-001") } returns inventory

            val result = useCase.deductInventory("SKU-001", 20)

            assertThat(result).isFalse
        }
    }

    @Nested
    @DisplayName("cancelReservation 테스트")
    inner class CancelReservationTest {
        @Test
        fun `예약 취소 성공`() {
            val inventory = Inventory(sku = "SKU-001", physicalStock = 100, reservedStock = 20)
            every { inventoryRepository.findBySku("SKU-001") } returns inventory
            every { inventoryRepository.save(inventory) } just runs

            val result = useCase.cancelReservation("SKU-001", 20)

            assertThat(result).isTrue
            verify { inventoryRepository.save(inventory) }
        }

        @Test
        fun `존재하지 않는 SKU 예약 취소는 false 반환`() {
            every { inventoryRepository.findBySku("INVALID") } returns null

            val result = useCase.cancelReservation("INVALID", 20)

            assertThat(result).isFalse
        }

        @Test
        fun `예약되지 않은 재고는 취소 불가`() {
            val inventory = Inventory(sku = "SKU-001", physicalStock = 100, reservedStock = 0)
            every { inventoryRepository.findBySku("SKU-001") } returns inventory

            val result = useCase.cancelReservation("SKU-001", 20)

            assertThat(result).isFalse
        }
    }

    @Nested
    @DisplayName("restoreInventory 테스트")
    inner class RestoreInventoryTest {
        @Test
        fun `재고 복구 성공`() {
            val inventory = Inventory(sku = "SKU-001", physicalStock = 80, reservedStock = 0)
            every { inventoryRepository.findBySku("SKU-001") } returns inventory
            every { inventoryRepository.save(inventory) } just runs

            val result = useCase.restoreInventory("SKU-001", 20)

            assertThat(result).isTrue
            verify { inventoryRepository.save(inventory) }
        }

        @Test
        fun `존재하지 않는 SKU 복구는 false 반환`() {
            every { inventoryRepository.findBySku("INVALID") } returns null

            val result = useCase.restoreInventory("INVALID", 20)

            assertThat(result).isFalse
        }
    }
}
