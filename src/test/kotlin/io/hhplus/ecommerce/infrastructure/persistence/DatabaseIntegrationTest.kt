package io.hhplus.ecommerce.infrastructure.persistence

import io.hhplus.ecommerce.application.services.InventoryService
import io.hhplus.ecommerce.application.services.PaymentService
import io.hhplus.ecommerce.application.services.ReservationService
import io.hhplus.ecommerce.infrastructure.persistence.entity.PaymentMethodJpa
import io.hhplus.ecommerce.infrastructure.persistence.entity.ReservationStatusJpa
import io.hhplus.ecommerce.infrastructure.persistence.repository.InventoryJpaRepository
import io.hhplus.ecommerce.infrastructure.persistence.repository.PaymentJpaRepository
import io.hhplus.ecommerce.infrastructure.persistence.repository.ReservationJpaRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Tag
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Database Integration Tests - 3가지 핵심 시나리오 검증
 *
 * 1. Concurrency Test: 비관적 락을 통한 동시성 제어
 * 2. Idempotency Test: 멱등성을 통한 중복 결제 방지
 * 3. Saga Test: TTL 기반 자동 만료 및 재고 복구
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Tag("integration")
class DatabaseIntegrationTest {

    @Autowired
    private lateinit var inventoryService: InventoryService

    @Autowired
    private lateinit var paymentService: PaymentService

    @Autowired
    private lateinit var reservationService: ReservationService

    @Autowired
    private lateinit var inventoryRepository: InventoryJpaRepository

    @Autowired
    private lateinit var paymentRepository: PaymentJpaRepository

    @Autowired
    private lateinit var reservationRepository: ReservationJpaRepository

    @BeforeEach
    fun setUp() {
        // 테스트용 재고 초기화
        inventoryService.createInventory(
            sku = "SKU-001",
            physicalStock = 100,
            safetyStock = 10
        )

        inventoryService.createInventory(
            sku = "SKU-002",
            physicalStock = 10,
            safetyStock = 0
        )
    }

    // ========================================
    // Scenario 1: Concurrency Test
    // ========================================
    @Test
    @DisplayName("시나리오 1: 10개 순차 예약 요청이 모두 성공한다 (비관적 락 동시성 제어)")
    fun concurrency_inventoryReservation_shouldSucceedAllRequests() {
        // Given
        val sku = "SKU-001" // 100개 재고
        val reservationQuantity = 1
        val threadCount = 10

        val successCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)

        val executor = Executors.newFixedThreadPool(5) // 스레드 풀 크기 제한
        val latch = CountDownLatch(threadCount)

        // When: 10개 스레드가 동시에 재고 1개씩 예약 시도
        repeat(threadCount) { threadIndex ->
            executor.submit {
                try {
                    inventoryService.reserveStock(sku, reservationQuantity)
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    failureCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        // Then: 모든 스레드 종료 대기
        latch.await(30, TimeUnit.SECONDS)
        executor.shutdown()

        // 모든 요청이 성공해야 함 (재고가 충분함)
        assertThat(successCount.get()).isEqualTo(threadCount)
        assertThat(failureCount.get()).isEqualTo(0)

        // 재고 확인: physicalStock이 90으로 감소 (100 - 10 = 90)
        val updatedInventory = inventoryRepository.findBySku(sku)
        assertThat(updatedInventory).isNotNull
        assertThat(updatedInventory!!.physicalStock).isEqualTo(90)
    }

    @Test
    @DisplayName("시나리오 1-2: 동시 예약으로 재고 일관성 검증 (여러 번 테스트)")
    fun concurrency_inventoryReservation_consistencyCheck() {
        // Given
        val sku = "SKU-001" // 100개 재고
        val reservationQuantity = 2
        val threadCount = 5

        val successCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)

        val executor = Executors.newFixedThreadPool(3)
        val latch = CountDownLatch(threadCount)

        // When: 5개 스레드가 동시에 재고 2개씩 예약 (총 10개)
        repeat(threadCount) { _ ->
            executor.submit {
                try {
                    inventoryService.reserveStock(sku, reservationQuantity)
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    failureCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(30, TimeUnit.SECONDS)
        executor.shutdown()

        // Then: 모든 요청이 성공
        assertThat(successCount.get()).isEqualTo(threadCount)
        assertThat(failureCount.get()).isEqualTo(0)

        // 재고 확인: 정확히 10개 차감 (100 - 10 = 90)
        val updatedInventory = inventoryRepository.findBySku(sku)
        assertThat(updatedInventory).isNotNull
        assertThat(updatedInventory!!.physicalStock).isEqualTo(90)
    }

    // ========================================
    // Scenario 2: Idempotency Test
    // ========================================
    @Test
    @DisplayName("시나리오 2: 동일한 idempotencyKey로 중복 결제 요청 시 기존 결과를 반환한다 (멱등성)")
    fun idempotency_duplicatePaymentRequest_shouldReturnExistingResult() {
        // Given
        val orderId = 2000L
        val amount = 50000L
        val idempotencyKey = "payment-key-2000"
        val method = PaymentMethodJpa.CARD

        // When: 첫 번째 결제 요청
        val firstPayment = paymentService.processPayment(
            orderId = orderId,
            amount = amount,
            method = method,
            idempotencyKey = idempotencyKey
        )

        // 동일한 키로 재요청 (중복 요청)
        val secondPayment = paymentService.processPayment(
            orderId = orderId,
            amount = amount,
            method = method,
            idempotencyKey = idempotencyKey
        )

        val thirdPayment = paymentService.processPayment(
            orderId = orderId,
            amount = amount,
            method = method,
            idempotencyKey = idempotencyKey
        )

        // Then: 모든 요청이 동일한 결제 레코드를 반환해야 함
        assertThat(firstPayment.id).isEqualTo(secondPayment.id)
        assertThat(firstPayment.id).isEqualTo(thirdPayment.id)

        // DB에 단 1개의 결제 레코드만 있어야 함
        val allPayments = paymentRepository.findByOrderId(orderId)
        assertThat(allPayments).isNotNull
        assertThat(allPayments!!.id).isEqualTo(firstPayment.id)
    }

    @Test
    @DisplayName("시나리오 2-2: 다른 orderId로 결제 요청 시 각각 새로운 레코드가 생성된다")
    fun idempotency_differentOrderId_shouldCreateNewPayment() {
        // Given
        val amount = 30000L
        val method = PaymentMethodJpa.CARD
        val idempotencyKey = "payment-key-different-order"

        // When: 다른 orderId로 결제 요청
        val payment1 = paymentService.processPayment(
            orderId = 2001L,
            amount = amount,
            method = method,
            idempotencyKey = idempotencyKey + "-1"
        )

        val payment2 = paymentService.processPayment(
            orderId = 2002L,
            amount = amount,
            method = method,
            idempotencyKey = idempotencyKey + "-2"
        )

        // Then: 서로 다른 결제 레코드가 생성되어야 함
        assertThat(payment1.id).isNotEqualTo(payment2.id)
        assertThat(payment1.orderId).isNotEqualTo(payment2.orderId)
    }

    @Test
    @DisplayName("시나리오 2-3: Concurrent 멱등성 요청도 동일 결과를 반환한다")
    fun idempotency_concurrentDuplicateRequests_shouldReturnSamePayment() {
        // Given
        val orderId = 2002L
        val amount = 25000L
        val idempotencyKey = "concurrent-payment-key"
        val method = PaymentMethodJpa.CARD
        val threadCount = 5

        val paymentIds = mutableListOf<Long>()
        val executor = Executors.newFixedThreadPool(3)
        val latch = CountDownLatch(threadCount)

        // When: 5개 스레드가 동시에 동일한 키로 결제 요청
        repeat(threadCount) { _ ->
            executor.submit {
                try {
                    val payment = paymentService.processPayment(
                        orderId = orderId,
                        amount = amount,
                        method = method,
                        idempotencyKey = idempotencyKey
                    )
                    synchronized(paymentIds) {
                        paymentIds.add(payment.id)
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // Then: 모든 요청이 동일한 결제 ID를 반환해야 함
        val firstPaymentId = paymentIds[0]
        assertThat(paymentIds).allMatch { it == firstPaymentId }
        assertThat(paymentIds.toSet()).hasSize(1) // 단 1개의 unique ID
    }

    // ========================================
    // Scenario 3: Saga Pattern - TTL Expiration Test
    // ========================================
    @Test
    @DisplayName("시나리오 3: 만료된 예약을 처리하면 상태가 EXPIRED로 변경되고 재고가 복구된다")
    fun saga_reservationExpiration_shouldExpireAndRestoreInventory() {
        // Given
        val orderId = 3000L
        val sku = "SKU-001"
        val quantity = 5
        val initialStock = 100

        // 재고 예약 생성
        val reservation = reservationService.createReservation(
            orderId = orderId,
            sku = sku,
            quantity = quantity
        )

        // 초기 검증: 예약 상태는 ACTIVE, 재고는 감소
        assertThat(reservation.status).isEqualTo(ReservationStatusJpa.ACTIVE)
        val inventoryAfterReservation = inventoryRepository.findBySku(sku)
        assertThat(inventoryAfterReservation!!.physicalStock).isEqualTo(initialStock - quantity)

        // When: 예약의 expiresAt을 과거로 설정하여 만료된 상태 만들기
        val expiredReservation = reservationRepository.findByOrderId(orderId)!!
        expiredReservation.expiresAt = LocalDateTime.now().minusMinutes(1)
        reservationRepository.save(expiredReservation)

        // expireReservations() 실행 - Scheduler에서 호출되는 메서드
        val expiredCount = reservationService.expireReservations()

        // Then: 만료된 예약이 처리되어야 함
        assertThat(expiredCount).isEqualTo(1)

        // 예약 상태 확인: EXPIRED로 변경됨
        val result = reservationRepository.findByOrderId(orderId)
        assertThat(result).isNotNull
        assertThat(result!!.status).isEqualTo(ReservationStatusJpa.EXPIRED)

        // 재고 확인: 복구되어야 함
        val finalInventory = inventoryRepository.findBySku(sku)
        assertThat(finalInventory).isNotNull
        assertThat(finalInventory!!.physicalStock).isEqualTo(initialStock)
    }

    @Test
    @DisplayName("시나리오 3-2: 여러 개의 만료된 예약을 한 번에 처리한다")
    fun saga_multipleExpiredReservations_shouldProcessAll() {
        // Given
        val sku = "SKU-001"
        val initialStock = 100
        val reservationCount = 3
        val quantityPerReservation = 2

        // 3개의 예약 생성
        val reservationIds = mutableListOf<Long>()
        repeat(reservationCount) { index ->
            val orderId = 3100L + index
            val reservation = reservationService.createReservation(
                orderId = orderId,
                sku = sku,
                quantity = quantityPerReservation
            )
            reservationIds.add(reservation.id)
        }

        // 모든 예약의 expiresAt을 과거로 설정
        for (reservationId in reservationIds) {
            val reservation = reservationRepository.findById(reservationId).orElse(null)
            if (reservation != null) {
                reservation.expiresAt = LocalDateTime.now().minusMinutes(1)
                reservationRepository.save(reservation)
            }
        }

        // 초기 재고 확인: 예약된 양이 차감됨 (100 - 2*3 = 94)
        var inventory = inventoryRepository.findBySku(sku)
        assertThat(inventory!!.physicalStock).isEqualTo(initialStock - (quantityPerReservation * reservationCount))

        // When: 만료된 예약 일괄 처리
        val expiredCount = reservationService.expireReservations()

        // Then: 만료된 예약 개수 확인
        assertThat(expiredCount).isGreaterThanOrEqualTo(reservationCount)

        // 모든 예약이 EXPIRED 상태로 변경됨을 확인
        for (orderId in 3100L until (3100L + reservationCount)) {
            val result = reservationRepository.findByOrderId(orderId)
            assertThat(result?.status).isEqualTo(ReservationStatusJpa.EXPIRED)
        }

        // 재고 복구 확인: 모두 복구됨 (94 + 2*3 = 100)
        inventory = inventoryRepository.findBySku(sku)
        assertThat(inventory!!.physicalStock).isEqualTo(initialStock)
    }

    @Test
    @DisplayName("시나리오 3-3: 아직 만료되지 않은 예약은 처리되지 않는다")
    fun saga_notExpiredReservations_shouldNotBeProcessed() {
        // Given
        val orderId = 3200L
        val sku = "SKU-001"
        val quantity = 3
        val initialStock = 100

        // 만료되지 않은 예약 생성 (15분 뒤에 만료)
        val reservation = reservationService.createReservation(
            orderId = orderId,
            sku = sku,
            quantity = quantity
        )

        // 초기 상태
        assertThat(reservation.status).isEqualTo(ReservationStatusJpa.ACTIVE)
        var inventory = inventoryRepository.findBySku(sku)
        assertThat(inventory!!.physicalStock).isEqualTo(initialStock - quantity)

        // When: 아직 만료되지 않은 예약을 처리 시도
        val expiredCountBefore = reservationRepository.findByStatus(ReservationStatusJpa.EXPIRED).size
        reservationService.expireReservations()

        // Then: 처리되는 예약이 없어야 함 (예약이 만료되지 않았으므로)
        val expiredCountAfter = reservationRepository.findByStatus(ReservationStatusJpa.EXPIRED).size
        assertThat(expiredCountAfter).isEqualTo(expiredCountBefore) // 변화 없음

        // 예약 상태 그대로 유지
        val stillActiveReservation = reservationRepository.findByOrderId(orderId)
        assertThat(stillActiveReservation!!.status).isEqualTo(ReservationStatusJpa.ACTIVE)

        // 재고는 여전히 차감된 상태
        inventory = inventoryRepository.findBySku(sku)
        assertThat(inventory!!.physicalStock).isEqualTo(initialStock - quantity)
    }

    @Test
    @DisplayName("시나리오 3-4: 확정된 예약은 만료 처리에서 제외된다")
    fun saga_confirmedReservations_shouldNotBeExpired() {
        // Given
        val orderId = 3300L
        val sku = "SKU-001"
        val quantity = 2

        // 예약 생성 및 확정
        val reservation = reservationService.createReservation(
            orderId = orderId,
            sku = sku,
            quantity = quantity
        )

        // 예약 확정
        reservationService.confirmReservation(orderId)

        // 확정된 예약의 expiresAt을 과거로 변경해도 CONFIRMED 상태이므로 처리 안 됨
        val confirmedReservation = reservationRepository.findByOrderId(orderId)!!
        confirmedReservation.expiresAt = LocalDateTime.now().minusMinutes(1)
        reservationRepository.save(confirmedReservation)

        // When: 만료 처리 실행
        reservationService.expireReservations()

        // Then: CONFIRMED 상태의 예약은 처리되지 않음 (findExpiredReservations는 ACTIVE만 조회)
        val result = reservationRepository.findByOrderId(orderId)
        // 확정된 예약은 여전히 CONFIRMED 상태여야 함
        assertThat(result!!.status).isEqualTo(ReservationStatusJpa.CONFIRMED)
    }
}
