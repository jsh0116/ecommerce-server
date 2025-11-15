package io.hhplus.ecommerce.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

@DisplayName("Inventory 도메인 모델 테스트")
class InventoryTest {

    @Nested
    @DisplayName("getAvailableStock 테스트")
    inner class GetAvailableStockTest {
        @Test
        fun `가용 재고 = 실제재고 - 예약재고 - 안전재고`() {
            val inventory = Inventory(
                sku = "SKU-001",
                physicalStock = 100,
                reservedStock = 20,
                safetyStock = 10
            )

            assertThat(inventory.getAvailableStock()).isEqualTo(70)
        }

        @Test
        fun `가용 재고가 음수이면 0을 반환한다`() {
            val inventory = Inventory(
                sku = "SKU-001",
                physicalStock = 10,
                reservedStock = 15,
                safetyStock = 5
            )

            assertThat(inventory.getAvailableStock()).isEqualTo(0)
        }

        @Test
        fun `안전재고가 0이면 경고 없이 차감된다`() {
            val inventory = Inventory(
                sku = "SKU-001",
                physicalStock = 100,
                reservedStock = 20,
                safetyStock = 0
            )

            assertThat(inventory.getAvailableStock()).isEqualTo(80)
        }
    }

    @Nested
    @DisplayName("getStatus 테스트")
    inner class GetStatusTest {
        @Test
        fun `가용 재고가 0 이하면 OUT_OF_STOCK 반환`() {
            val inventory = Inventory(
                sku = "SKU-001",
                physicalStock = 5,
                reservedStock = 5,
                safetyStock = 0
            )

            assertThat(inventory.getStatus()).isEqualTo(StockStatus.OUT_OF_STOCK)
        }

        @Test
        fun `가용 재고가 1~5이면 LOW_STOCK 반환`() {
            val inventory = Inventory(
                sku = "SKU-001",
                physicalStock = 25,
                reservedStock = 20,
                safetyStock = 0
            )

            assertThat(inventory.getStatus()).isEqualTo(StockStatus.LOW_STOCK)
        }

        @Test
        fun `가용 재고가 5를 초과하면 IN_STOCK 반환`() {
            val inventory = Inventory(
                sku = "SKU-001",
                physicalStock = 100,
                reservedStock = 0,
                safetyStock = 0
            )

            assertThat(inventory.getStatus()).isEqualTo(StockStatus.IN_STOCK)
        }
    }

    @Nested
    @DisplayName("canReserve 테스트")
    inner class CanReserveTest {
        @Test
        fun `가용 재고가 충분하면 true 반환`() {
            val inventory = Inventory(
                sku = "SKU-001",
                physicalStock = 100,
                reservedStock = 0,
                safetyStock = 0
            )

            assertThat(inventory.canReserve(50)).isTrue
        }

        @Test
        fun `가용 재고가 정확히 일치하면 true 반환`() {
            val inventory = Inventory(
                sku = "SKU-001",
                physicalStock = 100,
                reservedStock = 0,
                safetyStock = 0
            )

            assertThat(inventory.canReserve(100)).isTrue
        }

        @Test
        fun `가용 재고가 부족하면 false 반환`() {
            val inventory = Inventory(
                sku = "SKU-001",
                physicalStock = 50,
                reservedStock = 0,
                safetyStock = 0
            )

            assertThat(inventory.canReserve(100)).isFalse
        }

        @Test
        fun `안전재고를 고려한 예약 가능 여부 판단`() {
            val inventory = Inventory(
                sku = "SKU-001",
                physicalStock = 100,
                reservedStock = 0,
                safetyStock = 30
            )

            assertThat(inventory.canReserve(71)).isFalse
            assertThat(inventory.canReserve(70)).isTrue
        }
    }

    @Nested
    @DisplayName("reserve 테스트")
    inner class ReserveTest {
        @Test
        fun `예약 재고 정상 증가`() {
            val inventory = Inventory(
                sku = "SKU-001",
                physicalStock = 100,
                reservedStock = 0,
                safetyStock = 0
            )

            inventory.reserve(30)

            assertThat(inventory.reservedStock).isEqualTo(30)
            assertThat(inventory.getAvailableStock()).isEqualTo(70)
        }

        @Test
        fun `재고 부족 시 예외 발생`() {
            val inventory = Inventory(
                sku = "SKU-001",
                physicalStock = 50,
                reservedStock = 0,
                safetyStock = 0
            )

            val exception = assertThrows<IllegalStateException> {
                inventory.reserve(100)
            }

            assertThat(exception.message).isEqualTo("재고 부족: 요청 100개, 가용 재고 50개 (SKU: SKU-001)")
        }

        @Test
        fun `여러 번 예약 가능`() {
            val inventory = Inventory(
                sku = "SKU-001",
                physicalStock = 100,
                reservedStock = 0,
                safetyStock = 0
            )

            inventory.reserve(30)
            inventory.reserve(20)

            assertThat(inventory.reservedStock).isEqualTo(50)
            assertThat(inventory.getAvailableStock()).isEqualTo(50)
        }
    }

    @Nested
    @DisplayName("confirmReservation 테스트")
    inner class ConfirmReservationTest {
        @Test
        fun `예약 확정 시 실제 재고 감소`() {
            val inventory = Inventory(
                sku = "SKU-001",
                physicalStock = 100,
                reservedStock = 30,
                safetyStock = 0
            )

            inventory.confirmReservation(30)

            assertThat(inventory.physicalStock).isEqualTo(70)
            assertThat(inventory.reservedStock).isEqualTo(0)
        }

        @Test
        fun `예약된 수량보다 많이 확정 시 예외 발생`() {
            val inventory = Inventory(
                sku = "SKU-001",
                physicalStock = 100,
                reservedStock = 20,
                safetyStock = 0
            )

            val exception = assertThrows<IllegalStateException> {
                inventory.confirmReservation(30)
            }

            assertThat(exception.message).isEqualTo("예약 수량 부족: 요청 30개, 예약 재고 20개 (SKU: SKU-001)")
        }

        @Test
        fun `부분 확정 가능`() {
            val inventory = Inventory(
                sku = "SKU-001",
                physicalStock = 100,
                reservedStock = 50,
                safetyStock = 0
            )

            inventory.confirmReservation(30)

            assertThat(inventory.physicalStock).isEqualTo(70)
            assertThat(inventory.reservedStock).isEqualTo(20)
        }
    }

    @Nested
    @DisplayName("cancelReservation 테스트")
    inner class CancelReservationTest {
        @Test
        fun `예약 취소 시 예약 재고 감소`() {
            val inventory = Inventory(
                sku = "SKU-001",
                physicalStock = 100,
                reservedStock = 30,
                safetyStock = 0
            )

            inventory.cancelReservation(20)

            assertThat(inventory.physicalStock).isEqualTo(100)
            assertThat(inventory.reservedStock).isEqualTo(10)
            assertThat(inventory.getAvailableStock()).isEqualTo(90)
        }

        @Test
        fun `예약된 수량보다 많이 취소 시 예외 발생`() {
            val inventory = Inventory(
                sku = "SKU-001",
                physicalStock = 100,
                reservedStock = 20,
                safetyStock = 0
            )

            val exception = assertThrows<IllegalStateException> {
                inventory.cancelReservation(30)
            }

            assertThat(exception.message).isEqualTo("예약 취소 불가: 요청 30개, 예약 재고 20개 (SKU: SKU-001)")
        }
    }

    @Nested
    @DisplayName("restoreStock 테스트")
    inner class RestoreStockTest {
        @Test
        fun `실제 재고 증가`() {
            val inventory = Inventory(
                sku = "SKU-001",
                physicalStock = 100,
                reservedStock = 0,
                safetyStock = 0
            )

            inventory.restoreStock(50)

            assertThat(inventory.physicalStock).isEqualTo(150)
        }

        @Test
        fun `여러 번 복구 가능`() {
            val inventory = Inventory(
                sku = "SKU-001",
                physicalStock = 100,
                reservedStock = 0,
                safetyStock = 0
            )

            inventory.restoreStock(30)
            inventory.restoreStock(20)

            assertThat(inventory.physicalStock).isEqualTo(150)
        }
    }

    @Nested
    @DisplayName("adjustStock 테스트")
    inner class AdjustStockTest {
        @Test
        fun `재고를 특정 값으로 설정`() {
            val inventory = Inventory(
                sku = "SKU-001",
                physicalStock = 100,
                reservedStock = 0,
                safetyStock = 0
            )

            inventory.adjustStock(200)

            assertThat(inventory.physicalStock).isEqualTo(200)
        }

        @Test
        fun `음수 재고 설정 시 예외 발생`() {
            val inventory = Inventory(
                sku = "SKU-001",
                physicalStock = 100,
                reservedStock = 0,
                safetyStock = 0
            )

            val exception = assertThrows<IllegalArgumentException> {
                inventory.adjustStock(-10)
            }

            assertThat(exception.message).isEqualTo("재고는 0 이상이어야 합니다")
        }

        @Test
        fun `0으로 설정 가능`() {
            val inventory = Inventory(
                sku = "SKU-001",
                physicalStock = 100,
                reservedStock = 0,
                safetyStock = 0
            )

            inventory.adjustStock(0)

            assertThat(inventory.physicalStock).isEqualTo(0)
        }
    }
}
