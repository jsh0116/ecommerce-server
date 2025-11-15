package io.hhplus.ecommerce.application.services

import io.hhplus.ecommerce.domain.StockStatus
import io.hhplus.ecommerce.infrastructure.persistence.entity.InventoryJpaEntity
import io.hhplus.ecommerce.infrastructure.persistence.repository.InventoryJpaRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

@DisplayName("InventoryService 테스트")
class InventoryServiceTest {

    private val inventoryRepository = mockk<InventoryJpaRepository>()
    private val service = InventoryService(inventoryRepository)

    @Nested
    @DisplayName("재고 예약 테스트")
    inner class ReserveStockTest {
        @Test
        fun `재고를 예약할 수 있다`() {
            // Given
            val inventory = InventoryJpaEntity(
                id = 1L,
                sku = "SKU-001",
                physicalStock = 100,
                reservedStock = 0,
                safetyStock = 0,
                status = StockStatus.IN_STOCK,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )

            every { inventoryRepository.findBySkuForUpdate("SKU-001") } returns inventory
            every { inventoryRepository.save(any()) } returns inventory

            // When
            val result = service.reserveStock("SKU-001", 20)

            // Then
            assertThat(result).isNotNull
            verify { inventoryRepository.findBySkuForUpdate("SKU-001") }
            verify { inventoryRepository.save(any()) }
        }

        @Test
        fun `존재하지 않는 SKU는 예외를 발생시킨다`() {
            // Given
            every { inventoryRepository.findBySkuForUpdate("INVALID-SKU") } returns null

            // When/Then
            assertThatThrownBy {
                service.reserveStock("INVALID-SKU", 10)
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("상품을 찾을 수 없습니다")
        }

        @Test
        fun `재고 부족 시 예외를 발생시킨다`() {
            // Given
            val inventory = InventoryJpaEntity(
                id = 1L,
                sku = "SKU-001",
                physicalStock = 10,
                reservedStock = 0,
                safetyStock = 0,
                status = StockStatus.LOW_STOCK
            )

            every { inventoryRepository.findBySkuForUpdate("SKU-001") } returns inventory

            // When/Then
            assertThatThrownBy {
                service.reserveStock("SKU-001", 20)
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("재고 부족")
        }
    }

    @Nested
    @DisplayName("예약 확정 테스트")
    inner class ConfirmReservationTest {
        @Test
        fun `예약을 확정할 수 있다`() {
            // Given
            val inventory = InventoryJpaEntity(
                id = 1L,
                sku = "SKU-001",
                physicalStock = 80,
                reservedStock = 20,
                safetyStock = 0,
                status = StockStatus.IN_STOCK
            )

            every { inventoryRepository.findBySkuForUpdate("SKU-001") } returns inventory
            every { inventoryRepository.save(any()) } returns inventory

            // When
            val result = service.confirmReservation("SKU-001", 20)

            // Then
            assertThat(result).isNotNull
            verify { inventoryRepository.findBySkuForUpdate("SKU-001") }
            verify { inventoryRepository.save(any()) }
        }

        @Test
        fun `존재하지 않는 SKU는 예외를 발생시킨다`() {
            // Given
            every { inventoryRepository.findBySkuForUpdate("INVALID-SKU") } returns null

            // When/Then
            assertThatThrownBy {
                service.confirmReservation("INVALID-SKU", 10)
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("상품을 찾을 수 없습니다")
        }
    }

    @Nested
    @DisplayName("예약 취소 테스트")
    inner class CancelReservationTest {
        @Test
        fun `예약을 취소할 수 있다`() {
            // Given
            val inventory = InventoryJpaEntity(
                id = 1L,
                sku = "SKU-001",
                physicalStock = 80,
                reservedStock = 20,
                safetyStock = 0,
                status = StockStatus.IN_STOCK
            )

            every { inventoryRepository.findBySkuForUpdate("SKU-001") } returns inventory
            every { inventoryRepository.save(any()) } returns inventory

            // When
            val result = service.cancelReservation("SKU-001", 20)

            // Then
            assertThat(result).isNotNull
            verify { inventoryRepository.findBySkuForUpdate("SKU-001") }
            verify { inventoryRepository.save(any()) }
        }

        @Test
        fun `존재하지 않는 SKU는 예외를 발생시킨다`() {
            // Given
            every { inventoryRepository.findBySkuForUpdate("INVALID-SKU") } returns null

            // When/Then
            assertThatThrownBy {
                service.cancelReservation("INVALID-SKU", 10)
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("상품을 찾을 수 없습니다")
        }
    }

    @Nested
    @DisplayName("재고 복구 테스트")
    inner class RestoreStockTest {
        @Test
        fun `재고를 복구할 수 있다`() {
            // Given
            val inventory = InventoryJpaEntity(
                id = 1L,
                sku = "SKU-001",
                physicalStock = 80,
                reservedStock = 0,
                safetyStock = 0,
                status = StockStatus.IN_STOCK
            )

            every { inventoryRepository.findBySkuForUpdate("SKU-001") } returns inventory
            every { inventoryRepository.save(any()) } returns inventory

            // When
            val result = service.restoreStock("SKU-001", 20)

            // Then
            assertThat(result).isNotNull
            verify { inventoryRepository.findBySkuForUpdate("SKU-001") }
            verify { inventoryRepository.save(any()) }
        }

        @Test
        fun `존재하지 않는 SKU는 예외를 발생시킨다`() {
            // Given
            every { inventoryRepository.findBySkuForUpdate("INVALID-SKU") } returns null

            // When/Then
            assertThatThrownBy {
                service.restoreStock("INVALID-SKU", 10)
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("상품을 찾을 수 없습니다")
        }
    }

    @Nested
    @DisplayName("재고 조회 테스트")
    inner class GetInventoryTest {
        @Test
        fun `SKU로 재고를 조회할 수 있다`() {
            // Given
            val inventory = InventoryJpaEntity(
                id = 1L,
                sku = "SKU-001",
                physicalStock = 100,
                reservedStock = 20,
                safetyStock = 0,
                status = StockStatus.IN_STOCK
            )

            every { inventoryRepository.findBySku("SKU-001") } returns inventory

            // When
            val result = service.getInventory("SKU-001")

            // Then
            assertThat(result).isNotNull
            assertThat(result?.sku).isEqualTo("SKU-001")
            verify { inventoryRepository.findBySku("SKU-001") }
        }

        @Test
        fun `존재하지 않는 SKU는 null을 반환한다`() {
            // Given
            every { inventoryRepository.findBySku("INVALID-SKU") } returns null

            // When
            val result = service.getInventory("INVALID-SKU")

            // Then
            assertThat(result).isNull()
        }
    }

    @Nested
    @DisplayName("재고 생성 테스트")
    inner class CreateInventoryTest {
        @Test
        fun `재고를 생성할 수 있다`() {
            // Given
            val inventory = InventoryJpaEntity(
                id = 1L,
                sku = "SKU-NEW",
                physicalStock = 100,
                reservedStock = 0,
                safetyStock = 10,
                status = StockStatus.IN_STOCK
            )

            every { inventoryRepository.save(any()) } returns inventory

            // When
            val result = service.createInventory("SKU-NEW", 100, 10)

            // Then
            assertThat(result).isNotNull
            assertThat(result.sku).isEqualTo("SKU-NEW")
            assertThat(result.physicalStock).isEqualTo(100)
            assertThat(result.safetyStock).isEqualTo(10)
            verify { inventoryRepository.save(any()) }
        }

        @Test
        fun `기본값으로 안전재고 0으로 재고를 생성할 수 있다`() {
            // Given
            val inventory = InventoryJpaEntity(
                id = 1L,
                sku = "SKU-NEW",
                physicalStock = 50,
                reservedStock = 0,
                safetyStock = 0,
                status = StockStatus.IN_STOCK
            )

            every { inventoryRepository.save(any()) } returns inventory

            // When
            val result = service.createInventory("SKU-NEW", 50)

            // Then
            assertThat(result).isNotNull
            assertThat(result.safetyStock).isEqualTo(0)
        }
    }
}
