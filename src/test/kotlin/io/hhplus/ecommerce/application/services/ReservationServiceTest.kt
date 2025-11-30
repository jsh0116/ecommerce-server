package io.hhplus.ecommerce.application.services

import io.hhplus.ecommerce.domain.StockStatus
import io.hhplus.ecommerce.exception.InventoryException
import io.hhplus.ecommerce.infrastructure.persistence.entity.InventoryJpaEntity
import io.hhplus.ecommerce.infrastructure.persistence.entity.ReservationJpaEntity
import io.hhplus.ecommerce.infrastructure.persistence.entity.ReservationStatusJpa
import io.hhplus.ecommerce.infrastructure.persistence.repository.InventoryJpaRepository
import io.hhplus.ecommerce.infrastructure.persistence.repository.ReservationJpaRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

@DisplayName("ReservationService 테스트")
class ReservationServiceTest {

    private val reservationRepository = mockk<ReservationJpaRepository>()
    private val inventoryRepository = mockk<InventoryJpaRepository>()
    // Spring Cache 어노테이션을 사용하므로 CacheService는 제거
    private val inventoryService = InventoryService(inventoryRepository)
    private val service = ReservationService(reservationRepository, inventoryService)

    @Nested
    @DisplayName("예약 생성 테스트")
    inner class CreateReservationTest {
        @Test
        fun `재고 예약을 생성할 수 있다`() {
            // Given
            val inventory = InventoryJpaEntity(
                id = 1L,
                sku = "SKU-001",
                physicalStock = 100,
                reservedStock = 0,
                safetyStock = 0,
                status = StockStatus.IN_STOCK
            )
            val reservation = ReservationJpaEntity(
                id = 1L,
                orderId = 1L,
                sku = "SKU-001",
                quantity = 20,
                status = ReservationStatusJpa.ACTIVE,
                expiresAt = LocalDateTime.now().plusMinutes(15)
            )

            every { inventoryRepository.findBySkuForUpdate("SKU-001") } returns inventory
            every { inventoryRepository.save(any()) } returns inventory
            every { reservationRepository.save(any()) } returns reservation

            // When
            val result = service.createReservation(1L, "SKU-001", 20)

            // Then
            assertThat(result).isNotNull
            assertThat(result.orderId).isEqualTo(1L)
            assertThat(result.sku).isEqualTo("SKU-001")
            assertThat(result.quantity).isEqualTo(20)
            assertThat(result.status).isEqualTo(ReservationStatusJpa.ACTIVE)
            verify { inventoryRepository.findBySkuForUpdate("SKU-001") }
            verify { inventoryRepository.save(any()) }
            verify { reservationRepository.save(any()) }
        }

        @Test
        fun `재고 부족 시 예약 생성에 실패한다`() {
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
                service.createReservation(1L, "SKU-001", 20)
            }.isInstanceOf(InventoryException.InsufficientStock::class.java)
        }
    }

    @Nested
    @DisplayName("예약 확정 테스트")
    inner class ConfirmReservationTest {
        @Test
        fun `예약을 확정할 수 있다`() {
            // Given
            val reservation = ReservationJpaEntity(
                id = 1L,
                orderId = 1L,
                sku = "SKU-001",
                quantity = 20,
                status = ReservationStatusJpa.ACTIVE,
                expiresAt = LocalDateTime.now().plusMinutes(15)
            )
            val inventory = InventoryJpaEntity(
                id = 1L,
                sku = "SKU-001",
                physicalStock = 80,
                reservedStock = 0,
                safetyStock = 0,
                status = StockStatus.IN_STOCK
            )
            val confirmedReservation = ReservationJpaEntity(
                id = 1L,
                orderId = 1L,
                sku = "SKU-001",
                quantity = 20,
                status = ReservationStatusJpa.CONFIRMED,
                expiresAt = LocalDateTime.now().plusMinutes(15)
            )

            every { reservationRepository.findByOrderId(1L) } returns reservation
            every { inventoryRepository.findBySkuForUpdate("SKU-001") } returns inventory
            every { inventoryRepository.save(any()) } returns inventory
            every { reservationRepository.save(any()) } returns confirmedReservation

            // When
            val result = service.confirmReservation(1L)

            // Then
            assertThat(result).isNotNull
            assertThat(result.status).isEqualTo(ReservationStatusJpa.CONFIRMED)
            verify { reservationRepository.findByOrderId(1L) }
            verify { inventoryRepository.findBySkuForUpdate("SKU-001") }
            verify { reservationRepository.save(any()) }
        }

        @Test
        fun `존재하지 않는 예약은 예외를 발생시킨다`() {
            // Given
            every { reservationRepository.findByOrderId(999L) } returns null

            // When/Then
            assertThatThrownBy {
                service.confirmReservation(999L)
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("예약을 찾을 수 없습니다")
        }
    }

    @Nested
    @DisplayName("예약 취소 테스트")
    inner class CancelReservationTest {
        @Test
        fun `예약을 취소할 수 있다`() {
            // Given
            val reservation = ReservationJpaEntity(
                id = 1L,
                orderId = 1L,
                sku = "SKU-001",
                quantity = 20,
                status = ReservationStatusJpa.ACTIVE,
                expiresAt = LocalDateTime.now().plusMinutes(15)
            )
            val inventory = InventoryJpaEntity(
                id = 1L,
                sku = "SKU-001",
                physicalStock = 80,
                reservedStock = 0,
                safetyStock = 0,
                status = StockStatus.IN_STOCK
            )
            val cancelledReservation = ReservationJpaEntity(
                id = 1L,
                orderId = 1L,
                sku = "SKU-001",
                quantity = 20,
                status = ReservationStatusJpa.CANCELLED,
                expiresAt = LocalDateTime.now().plusMinutes(15)
            )

            every { reservationRepository.findByOrderId(1L) } returns reservation
            every { inventoryRepository.findBySkuForUpdate("SKU-001") } returns inventory
            every { inventoryRepository.save(any()) } returns inventory
            every { reservationRepository.save(any()) } returns cancelledReservation

            // When
            val result = service.cancelReservation(1L)

            // Then
            assertThat(result).isNotNull
            assertThat(result.status).isEqualTo(ReservationStatusJpa.CANCELLED)
            verify { reservationRepository.findByOrderId(1L) }
            verify { inventoryRepository.findBySkuForUpdate("SKU-001") }
            verify { reservationRepository.save(any()) }
        }

        @Test
        fun `존재하지 않는 예약은 예외를 발생시킨다`() {
            // Given
            every { reservationRepository.findByOrderId(999L) } returns null

            // When/Then
            assertThatThrownBy {
                service.cancelReservation(999L)
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("예약을 찾을 수 없습니다")
        }
    }

    @Nested
    @DisplayName("만료된 예약 처리 테스트")
    inner class ExpireReservationsTest {
        @Test
        fun `만료된 예약을 처리할 수 있다`() {
            // Given
            val expiredReservation = ReservationJpaEntity(
                id = 1L,
                orderId = 1L,
                sku = "SKU-001",
                quantity = 20,
                status = ReservationStatusJpa.ACTIVE,
                expiresAt = LocalDateTime.now().minusMinutes(1)
            )
            val inventory = InventoryJpaEntity(
                id = 1L,
                sku = "SKU-001",
                physicalStock = 80,
                reservedStock = 0,
                safetyStock = 0,
                status = StockStatus.IN_STOCK
            )
            val expiredReservationResult = ReservationJpaEntity(
                id = 1L,
                orderId = 1L,
                sku = "SKU-001",
                quantity = 20,
                status = ReservationStatusJpa.EXPIRED,
                expiresAt = LocalDateTime.now().minusMinutes(1)
            )

            every { reservationRepository.findExpiredReservations() } returns listOf(expiredReservation)
            every { inventoryRepository.findBySkuForUpdate("SKU-001") } returns inventory
            every { inventoryRepository.save(any()) } returns inventory
            every { reservationRepository.save(any()) } returns expiredReservationResult

            // When
            val result = service.expireReservations()

            // Then
            assertThat(result).isEqualTo(1)
            verify { reservationRepository.findExpiredReservations() }
            verify { inventoryRepository.findBySkuForUpdate("SKU-001") }
            verify { reservationRepository.save(any()) }
        }

        @Test
        fun `만료된 예약이 없으면 0을 반환한다`() {
            // Given
            every { reservationRepository.findExpiredReservations() } returns emptyList()

            // When
            val result = service.expireReservations()

            // Then
            assertThat(result).isEqualTo(0)
        }

        @Test
        fun `여러 만료된 예약을 처리할 수 있다`() {
            // Given
            val reservation1 = ReservationJpaEntity(
                id = 1L,
                orderId = 1L,
                sku = "SKU-001",
                quantity = 20,
                status = ReservationStatusJpa.ACTIVE,
                expiresAt = LocalDateTime.now().minusMinutes(1)
            )
            val reservation2 = ReservationJpaEntity(
                id = 2L,
                orderId = 2L,
                sku = "SKU-002",
                quantity = 30,
                status = ReservationStatusJpa.ACTIVE,
                expiresAt = LocalDateTime.now().minusMinutes(1)
            )
            val inventory1 = InventoryJpaEntity(
                id = 1L,
                sku = "SKU-001",
                physicalStock = 80,
                reservedStock = 0,
                safetyStock = 0,
                status = StockStatus.IN_STOCK
            )
            val inventory2 = InventoryJpaEntity(
                id = 2L,
                sku = "SKU-002",
                physicalStock = 70,
                reservedStock = 0,
                safetyStock = 0,
                status = StockStatus.IN_STOCK
            )

            every { reservationRepository.findExpiredReservations() } returns listOf(reservation1, reservation2)
            every { inventoryRepository.findBySkuForUpdate("SKU-001") } returns inventory1
            every { inventoryRepository.findBySkuForUpdate("SKU-002") } returns inventory2
            every { inventoryRepository.save(any()) } returns inventory1
            every { reservationRepository.save(any()) } returns reservation1

            // When
            val result = service.expireReservations()

            // Then
            assertThat(result).isEqualTo(2)
        }
    }

    @Nested
    @DisplayName("예약 조회 테스트")
    inner class GetReservationTest {
        @Test
        fun `주문별 예약을 조회할 수 있다`() {
            // Given
            val reservation = ReservationJpaEntity(
                id = 1L,
                orderId = 1L,
                sku = "SKU-001",
                quantity = 20,
                status = ReservationStatusJpa.ACTIVE,
                expiresAt = LocalDateTime.now().plusMinutes(15)
            )

            every { reservationRepository.findByOrderId(1L) } returns reservation

            // When
            val result = service.getReservation(1L)

            // Then
            assertThat(result).isNotNull
            assertThat(result?.orderId).isEqualTo(1L)
        }

        @Test
        fun `존재하지 않는 주문은 null을 반환한다`() {
            // Given
            every { reservationRepository.findByOrderId(999L) } returns null

            // When
            val result = service.getReservation(999L)

            // Then
            assertThat(result).isNull()
        }
    }

    @Nested
    @DisplayName("활성 예약 조회 테스트")
    inner class GetActiveReservationsTest {
        @Test
        fun `활성 예약 목록을 조회할 수 있다`() {
            // Given
            val reservations = listOf(
                ReservationJpaEntity(
                    id = 1L,
                    orderId = 1L,
                    sku = "SKU-001",
                    quantity = 20,
                    status = ReservationStatusJpa.ACTIVE,
                    expiresAt = LocalDateTime.now().plusMinutes(15)
                ),
                ReservationJpaEntity(
                    id = 2L,
                    orderId = 2L,
                    sku = "SKU-002",
                    quantity = 30,
                    status = ReservationStatusJpa.ACTIVE,
                    expiresAt = LocalDateTime.now().plusMinutes(10)
                )
            )

            every { reservationRepository.findByStatus(ReservationStatusJpa.ACTIVE) } returns reservations

            // When
            val result = service.getActiveReservations()

            // Then
            assertThat(result).hasSize(2)
            assertThat(result).extracting<String> { it.sku }.contains("SKU-001", "SKU-002")
        }

        @Test
        fun `활성 예약이 없으면 빈 목록을 반환한다`() {
            // Given
            every { reservationRepository.findByStatus(ReservationStatusJpa.ACTIVE) } returns emptyList()

            // When
            val result = service.getActiveReservations()

            // Then
            assertThat(result).isEmpty()
        }
    }

    @Nested
    @DisplayName("만료 임박 예약 조회 테스트")
    inner class GetExpiringReservationsTest {
        @Test
        fun `만료 임박 예약을 조회할 수 있다`() {
            // Given
            val now = LocalDateTime.now()
            val expiringReservations = listOf(
                ReservationJpaEntity(
                    id = 1L,
                    orderId = 1L,
                    sku = "SKU-001",
                    quantity = 20,
                    status = ReservationStatusJpa.ACTIVE,
                    expiresAt = now.plusSeconds(30)
                )
            )
            val allActiveReservations = expiringReservations + listOf(
                ReservationJpaEntity(
                    id = 2L,
                    orderId = 2L,
                    sku = "SKU-002",
                    quantity = 30,
                    status = ReservationStatusJpa.ACTIVE,
                    expiresAt = now.plusMinutes(10)
                )
            )

            every { reservationRepository.findByStatus(ReservationStatusJpa.ACTIVE) } returns allActiveReservations

            // When
            val result = service.getExpiringReservations(1)

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].sku).isEqualTo("SKU-001")
        }

        @Test
        fun `만료 임박 예약이 없으면 빈 목록을 반환한다`() {
            // Given
            val now = LocalDateTime.now()
            val allActiveReservations = listOf(
                ReservationJpaEntity(
                    id = 1L,
                    orderId = 1L,
                    sku = "SKU-001",
                    quantity = 20,
                    status = ReservationStatusJpa.ACTIVE,
                    expiresAt = now.plusMinutes(10)
                )
            )

            every { reservationRepository.findByStatus(ReservationStatusJpa.ACTIVE) } returns allActiveReservations

            // When
            val result = service.getExpiringReservations(1)

            // Then
            assertThat(result).isEmpty()
        }
    }
}
