package io.hhplus.ecommerce.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("Inventory 엣지 케이스 테스트")
class InventoryEdgeCaseTest {

    @Nested
    @DisplayName("재고 수량 테스트")
    inner class InventoryQuantityTest {
        @Test
        fun `재고 수량이 올바르게 초기화된다`() {
            // Given
            val inventory = Inventory(sku = "SKU-001", physicalStock = 100, reservedStock = 20)

            // Then
            assertThat(inventory.physicalStock).isEqualTo(100)
            assertThat(inventory.reservedStock).isEqualTo(20)
        }

        @Test
        fun `0개의 물리 재고를 관리할 수 있다`() {
            // Given
            val inventory = Inventory(sku = "SKU-002", physicalStock = 0, reservedStock = 0)

            // Then
            assertThat(inventory.physicalStock).isEqualTo(0)
            assertThat(inventory.reservedStock).isEqualTo(0)
        }

        @Test
        fun `큰 수량의 재고를 관리할 수 있다`() {
            // Given
            val inventory = Inventory(sku = "SKU-003", physicalStock = 1000000, reservedStock = 500000)

            // Then
            assertThat(inventory.physicalStock).isEqualTo(1000000)
            assertThat(inventory.reservedStock).isEqualTo(500000)
        }
    }

    @Nested
    @DisplayName("가용 재고 계산 테스트")
    inner class AvailableStockTest {
        @Test
        fun `가용 재고가 올바르게 계산된다`() {
            // Given
            val inventory = Inventory(sku = "SKU-001", physicalStock = 100, reservedStock = 30)

            // When
            val available = inventory.getAvailableStock()

            // Then
            assertThat(available).isEqualTo(70)
        }

        @Test
        fun `예약 재고가 물리 재고를 초과할 수 없다`() {
            // Given - 이 케이스는 비즈니스 로직에서 방지되어야 함
            val inventory = Inventory(sku = "SKU-002", physicalStock = 50, reservedStock = 50)

            // When
            val available = inventory.getAvailableStock()

            // Then
            assertThat(available).isEqualTo(0)
        }

        @Test
        fun `모든 재고가 예약된 경우`() {
            // Given
            val inventory = Inventory(sku = "SKU-003", physicalStock = 100, reservedStock = 100)

            // When
            val available = inventory.getAvailableStock()

            // Then
            assertThat(available).isEqualTo(0)
        }

        @Test
        fun `예약 재고가 없는 경우`() {
            // Given
            val inventory = Inventory(sku = "SKU-004", physicalStock = 100, reservedStock = 0)

            // When
            val available = inventory.getAvailableStock()

            // Then
            assertThat(available).isEqualTo(100)
        }
    }

    @Nested
    @DisplayName("SKU 테스트")
    inner class SkuTest {
        @Test
        fun `다양한 SKU 형식을 저장할 수 있다`() {
            // Given
            val skuFormats = listOf(
                "SKU-001",
                "PROD-2024-001",
                "AA-BB-CC-DD-001",
                "SKU_WITH_UNDERSCORE"
            )

            // When & Then
            for (sku in skuFormats) {
                val inventory = Inventory(sku = sku, physicalStock = 100, reservedStock = 0)
                assertThat(inventory.sku).isEqualTo(sku)
            }
        }

        @Test
        fun `특수 문자가 포함된 SKU를 저장할 수 있다`() {
            // Given
            val inventory = Inventory(sku = "SKU-2024-001-A/B", physicalStock = 100, reservedStock = 0)

            // Then
            assertThat(inventory.sku).isEqualTo("SKU-2024-001-A/B")
        }

        @Test
        fun `긴 SKU를 저장할 수 있다`() {
            // Given
            val longSku = "SKU-" + "0".repeat(100)
            val inventory = Inventory(sku = longSku, physicalStock = 100, reservedStock = 0)

            // Then
            assertThat(inventory.sku).isEqualTo(longSku)
        }
    }

    @Nested
    @DisplayName("재고 수량 변동 테스트")
    inner class InventoryFluctuationTest {
        @Test
        fun `물리 재고를 증가시킬 수 있다`() {
            // Given
            val inventory = Inventory(sku = "SKU-001", physicalStock = 100, reservedStock = 0)

            // When
            inventory.physicalStock = 150

            // Then
            assertThat(inventory.physicalStock).isEqualTo(150)
        }

        @Test
        fun `예약 재고를 변경할 수 있다`() {
            // Given
            val inventory = Inventory(sku = "SKU-001", physicalStock = 100, reservedStock = 20)

            // When
            inventory.reservedStock = 50

            // Then
            assertThat(inventory.reservedStock).isEqualTo(50)
        }

        @Test
        fun `여러 번의 재고 변동을 처리할 수 있다`() {
            // Given
            val inventory = Inventory(sku = "SKU-001", physicalStock = 100, reservedStock = 0)

            // When
            inventory.physicalStock = 80  // 판매 20
            inventory.reservedStock = 10  // 예약
            inventory.physicalStock = 70  // 추가 판매
            inventory.reservedStock = 20  // 예약 증가

            // Then
            assertThat(inventory.physicalStock).isEqualTo(70)
            assertThat(inventory.reservedStock).isEqualTo(20)
            assertThat(inventory.getAvailableStock()).isEqualTo(50)
        }
    }
}
