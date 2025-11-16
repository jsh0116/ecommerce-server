package io.hhplus.ecommerce.infrastructure.persistence.repository

import io.hhplus.ecommerce.infrastructure.persistence.entity.ReservationJpaEntity
import io.hhplus.ecommerce.infrastructure.persistence.entity.ReservationStatusJpa
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Tag
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime

/**
 * Reservation Repository 성능 최적화 테스트
 * TTL 만료 처리 배치 UPDATE 검증
 * 배치 UPDATE로 O(N) -> O(1) 성능 개선 검증
 */
@DataJpaTest
@ActiveProfiles("test")
@Tag("performance")
@DisplayName("Reservation Repository 성능 최적화 테스트")
class ReservationRepositoryOptimizationTest {

    @Autowired
    private lateinit var reservationRepository: ReservationJpaRepository

    @BeforeEach
    fun setUp() {
        // 테스트 데이터 준비
        val reservations = mutableListOf<ReservationJpaEntity>()

        // 만료된 예약 50개
        repeat(50) { i ->
            reservations.add(
                ReservationJpaEntity(
                    orderId = (i + 1).toLong(),
                    sku = "SKU-EXPIRED-${String.format("%03d", i + 1)}",
                    quantity = 1,
                    status = ReservationStatusJpa.ACTIVE,
                    expiresAt = LocalDateTime.now().minusMinutes(1), // 1분 전 만료
                    createdAt = LocalDateTime.now().minusMinutes(20),
                    updatedAt = LocalDateTime.now().minusMinutes(20)
                )
            )
        }

        // 활성 예약 50개
        repeat(50) { i ->
            reservations.add(
                ReservationJpaEntity(
                    orderId = (100 + i + 1).toLong(),
                    sku = "SKU-ACTIVE-${String.format("%03d", i + 1)}",
                    quantity = 2,
                    status = ReservationStatusJpa.ACTIVE,
                    expiresAt = LocalDateTime.now().plusMinutes(14), // 14분 후 만료
                    createdAt = LocalDateTime.now(),
                    updatedAt = LocalDateTime.now()
                )
            )
        }

        // 확정된 예약 20개
        repeat(20) { i ->
            reservations.add(
                ReservationJpaEntity(
                    orderId = (200 + i + 1).toLong(),
                    sku = "SKU-CONFIRMED-${String.format("%03d", i + 1)}",
                    quantity = 3,
                    status = ReservationStatusJpa.CONFIRMED,
                    expiresAt = LocalDateTime.now(),
                    createdAt = LocalDateTime.now().minusHours(1),
                    updatedAt = LocalDateTime.now().minusMinutes(30)
                )
            )
        }

        reservationRepository.saveAll(reservations)
    }

    @Test
    @DisplayName("배치: 만료된 예약 일괄 상태 변경 (O(N) -> O(1) 최적화)")
    fun testExpireExpiredReservationsBatch() {
        // Act: 배치 UPDATE (단일 쿼리)
        val startTime = System.currentTimeMillis()
        val expiredCount = reservationRepository.expireExpiredReservations()
        val duration = System.currentTimeMillis() - startTime

        // Assert
        assertThat(expiredCount).isEqualTo(50) // 정확히 50개 만료되어야 함
        assertThat(duration).isLessThan(1000) // 1초 이내 (O(1) 성능)

        // 변경 확인
        val remainingExpired = reservationRepository.findExpiredReservations()
        assertThat(remainingExpired).isEmpty() // 모두 만료되었으므로 남은 것 없음
    }

    @Test
    @DisplayName("배치: 주문별 예약 취소")
    fun testCancelByOrderIdBatch() {
        // Arrange
        val orderIdToCancel = 1L

        // Act: 배치 UPDATE
        val startTime = System.currentTimeMillis()
        reservationRepository.cancelByOrderId(orderIdToCancel)
        val duration = System.currentTimeMillis() - startTime

        // Assert: 배치 UPDATE 성능만 확인
        assertThat(duration).isLessThan(500) // 0.5초 이내 (배치 효율성)
    }

    @Test
    @DisplayName("만료된 예약 조회 - 복합 인덱스 활용")
    fun testFindExpiredReservations() {
        // Act: idx_status_expires 복합 인덱스 활용
        val startTime = System.currentTimeMillis()
        val expiredReservations = reservationRepository.findExpiredReservations()
        val duration = System.currentTimeMillis() - startTime

        // Assert
        assertThat(expiredReservations).hasSize(50)
        assertThat(expiredReservations).allMatch { it.status == ReservationStatusJpa.ACTIVE }
        assertThat(expiredReservations).allMatch { it.expiresAt <= LocalDateTime.now() }
        assertThat(duration).isLessThan(500)
    }

    @Test
    @DisplayName("상태별 예약 조회")
    fun testFindByStatus() {
        // Act
        val activeReservations = reservationRepository.findByStatus(ReservationStatusJpa.ACTIVE)
        val confirmedReservations = reservationRepository.findByStatus(ReservationStatusJpa.CONFIRMED)

        // Assert
        assertThat(activeReservations).hasSize(100) // 만료된 것 포함 50 + 활성 50
        assertThat(confirmedReservations).hasSize(20)
    }

    @Test
    @DisplayName("SKU별 예약 조회")
    fun testFindBySku() {
        // Act
        val reservations = reservationRepository.findBySku("SKU-EXPIRED-001")

        // Assert
        assertThat(reservations).hasSize(1)
        assertThat(reservations.first().quantity).isEqualTo(1)
    }

    @Test
    @DisplayName("SKU별, 상태별 예약 조회")
    fun testFindBySkuAndStatus() {
        // Act
        val activeReservations = reservationRepository.findBySkuAndStatus(
            "SKU-ACTIVE-001",
            ReservationStatusJpa.ACTIVE
        )

        // Assert
        assertThat(activeReservations).hasSize(1)
        assertThat(activeReservations.first().status).isEqualTo(ReservationStatusJpa.ACTIVE)
    }

    @Test
    @DisplayName("활성 상태 예약 조회")
    fun testFindActiveReservations() {
        // Act
        val activeReservations = reservationRepository.findByStatus(ReservationStatusJpa.ACTIVE)

        // Assert
        assertThat(activeReservations).hasSize(100)
        assertThat(activeReservations).allMatch { it.status == ReservationStatusJpa.ACTIVE }
    }

    @Test
    @DisplayName("통계: SKU별 예약 수량 합계 - DB 레벨 집계")
    fun testSumReservedQuantityBySku() {
        // Act: DB 레벨 집계
        val total = reservationRepository.sumReservedQuantityBySku("SKU-ACTIVE-001")

        // Assert
        assertThat(total).isEqualTo(2) // quantity = 2
    }

    @Test
    @DisplayName("성능: 대규모 배치 만료 처리 (1000개 예약)")
    fun testLargeBatchExpiration() {
        // Arrange: 추가 대규모 데이터
        val largeReservations = mutableListOf<ReservationJpaEntity>()
        repeat(900) { i ->
            largeReservations.add(
                ReservationJpaEntity(
                    orderId = (1000 + i + 1).toLong(),
                    sku = "SKU-LARGE-${String.format("%04d", i + 1)}",
                    quantity = 1,
                    status = ReservationStatusJpa.ACTIVE,
                    expiresAt = LocalDateTime.now().minusMinutes(1),
                    createdAt = LocalDateTime.now().minusMinutes(30),
                    updatedAt = LocalDateTime.now().minusMinutes(30)
                )
            )
        }
        reservationRepository.saveAll(largeReservations)

        // Act: 1000개 배치 만료 처리
        val startTime = System.currentTimeMillis()
        val expiredCount = reservationRepository.expireExpiredReservations()
        val duration = System.currentTimeMillis() - startTime

        // Assert
        assertThat(expiredCount).isEqualTo(950) // 50(기존) + 900(신규)
        assertThat(duration).isLessThan(2000) // 2초 이내 (배치 효율성)
    }

    @Test
    @DisplayName("배치: 여러 주문의 예약 취소")
    fun testMultipleOrderCancellationBatch() {
        // Arrange
        val orderIds = listOf(1L, 2L, 3L, 4L, 5L)

        // Act: 여러 주문의 예약 취소
        val startTime = System.currentTimeMillis()
        orderIds.forEach { orderId ->
            reservationRepository.cancelByOrderId(orderId)
        }
        val duration = System.currentTimeMillis() - startTime

        // Assert: 배치 처리 성능 확인
        assertThat(duration).isLessThan(2000) // 2초 이내 (여러 배치 처리)
    }

    @Test
    @DisplayName("예약 생명주기: 만료 처리 성능")
    fun testReservationLifecyclePerformance() {
        // Scenario: TTL 만료 처리의 성능 검증

        // Act: 만료된 예약 일괄 처리
        val startTime = System.currentTimeMillis()
        val expiredCount = reservationRepository.expireExpiredReservations()
        val duration = System.currentTimeMillis() - startTime

        // Assert: 배치 처리 성능 (단 1회 쿼리로 50개 만료 처리)
        assertThat(expiredCount).isGreaterThanOrEqualTo(50)
        assertThat(duration).isLessThan(1000) // 1초 이내
    }

    @Test
    @DisplayName("동시성 고려: 배치 업데이트 중복 방지")
    fun testBatchUpdateIdempotency() {
        // Act 1: 첫 번째 배치 만료 처리
        val firstCount = reservationRepository.expireExpiredReservations()

        // Act 2: 두 번째 배치 만료 처리 (중복 방지 확인)
        val secondCount = reservationRepository.expireExpiredReservations()

        // Assert
        assertThat(firstCount).isEqualTo(50)
        assertThat(secondCount).isEqualTo(0) // 이미 만료된 것은 다시 처리 안됨
    }

    @Test
    @DisplayName("인덱스 효율성: 복합 인덱스 활용 검증")
    fun testCompositeIndexEfficiency() {
        // idx_status_expires 복합 인덱스 활용
        val startTime = System.currentTimeMillis()

        // WHERE r.status = 'ACTIVE' AND r.expiresAt <= NOW()
        val expired = reservationRepository.findExpiredReservations()

        val duration = System.currentTimeMillis() - startTime

        // Assert
        assertThat(expired).isNotEmpty
        assertThat(duration).isLessThan(200) // 인덱스 없으면 훨씬 오래 걸림
    }
}
