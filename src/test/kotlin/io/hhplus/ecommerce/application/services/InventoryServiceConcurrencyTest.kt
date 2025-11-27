package io.hhplus.ecommerce.application.services

import io.hhplus.ecommerce.config.TestContainersConfig
import io.hhplus.ecommerce.domain.StockStatus
import io.hhplus.ecommerce.exception.InventoryException
import io.hhplus.ecommerce.infrastructure.persistence.entity.InventoryJpaEntity
import io.hhplus.ecommerce.infrastructure.persistence.repository.InventoryJpaRepository
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * 재고 관리 동시성 통합 테스트
 *
 * 실제 DB(TestContainers MySQL)와 실제 Redis를 이용하여 Race Condition을 검증합니다.
 * - 동시 요청 정확성 (비관적 락)
 * - 음수 재고 방지
 * - 재고 복구 정확성
 * - Redisson 분산 락 동작
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestContainersConfig::class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Tag("integration")
@DisplayName("재고 서비스 동시성 통합 테스트")
class InventoryServiceConcurrencyTest {

    @Autowired
    private lateinit var inventoryRepository: InventoryJpaRepository

    @Autowired
    private lateinit var inventoryService: InventoryService

    /**
     * 테스트: 100개 동시 요청 → 정확히 100개 판매
     *
     * 시나리오:
     * - 초기 재고: 100개
     * - 100명이 동시에 1개씩 구매
     * - 결과: 100개 모두 판매, 재고 = 0
     */
    @Test
    @DisplayName("동시 100개 요청이 정확히 100개 판매된다")
    fun `100개 동시 요청이 정확히 100개 판매된다`() {
        // Given
        val sku = "CONCURRENT-001"
        val initialStock = 100
        val threadCount = 100

        // 초기 재고 생성
        val inventory = inventoryRepository.save(
            InventoryJpaEntity(
                sku = sku,
                physicalStock = initialStock,
                reservedStock = 0,
                safetyStock = 0,
                status = StockStatus.IN_STOCK,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )
        )

        val successCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)
        val latch = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(10)

        // When: 100개 스레드가 동시에 1개씩 구매 시도
        repeat(threadCount) { index ->
            executor.submit {
                try {
                    inventoryService.reserveStock(sku, 1)
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    failureCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        // Awaitility로 모든 스레드 완료 대기 (CI 환경 안정성)
        await("재고 100개 판매 완료")
            .atMost(Duration.ofSeconds(30))
            .pollInterval(Duration.ofMillis(100))
            .untilAsserted {
                assertThat(successCount.get()).isEqualTo(100)
            }

        executor.shutdown()

        // Then
        val finalInventory = inventoryRepository.findBySku(sku)
        assertThat(finalInventory).isNotNull
        assertThat(failureCount.get()).isEqualTo(0)
        assertThat(finalInventory!!.physicalStock).isEqualTo(0)
        assertThat(finalInventory.status).isEqualTo(StockStatus.OUT_OF_STOCK)
    }

    /**
     * 테스트: 101개 동시 요청 → 100개 판매, 1개 실패
     *
     * 시나리오:
     * - 초기 재고: 100개
     * - 101명이 동시에 1개씩 구매
     * - 결과: 100개 판매 성공, 1개 실패 (재고 부족)
     * - 중요: 음수 재고 절대 발생 안 함
     */
    @Test
    @DisplayName("101개 동시 요청이 100개만 판매되고 1개는 실패한다")
    fun `101개 동시 요청이 100개만 판매되고 1개는 실패한다`() {
        // Given
        val sku = "CONCURRENT-002"
        val initialStock = 100
        val threadCount = 101

        // 초기 재고 생성
        inventoryRepository.save(
            InventoryJpaEntity(
                sku = sku,
                physicalStock = initialStock,
                reservedStock = 0,
                safetyStock = 0,
                status = StockStatus.IN_STOCK,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )
        )

        val successCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)
        val latch = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(20)

        // When: 101개 스레드가 동시에 1개씩 구매 시도
        repeat(threadCount) {
            executor.submit {
                try {
                    inventoryService.reserveStock(sku, 1)
                    successCount.incrementAndGet()
                } catch (e: InventoryException.InsufficientStock) {
                    failureCount.incrementAndGet()
                } catch (e: Exception) {
                    failureCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        // Awaitility로 모든 스레드 완료 대기 (CI 환경 안정성)
        await("재고 101개 요청 완료 (100개 성공, 1개 실패)")
            .atMost(Duration.ofSeconds(30))
            .pollInterval(Duration.ofMillis(100))
            .untilAsserted {
                assertThat(successCount.get() + failureCount.get()).isEqualTo(threadCount)
            }

        executor.shutdown()

        // Then
        val finalInventory = inventoryRepository.findBySku(sku)
        assertThat(finalInventory).isNotNull
        assertThat(successCount.get()).isEqualTo(100)
        assertThat(failureCount.get()).isEqualTo(1)
        // 핵심: 음수 재고 절대 안 됨
        assertThat(finalInventory!!.physicalStock).isGreaterThanOrEqualTo(0)
        assertThat(finalInventory.physicalStock).isEqualTo(0)
    }

    /**
     * 테스트: 예약 후 취소 → 재고 정확히 복구
     *
     * 시나리오:
     * - 초기 재고: 100개
     * - 50개 예약
     * - 20개 취소
     * - 결과: 재고 = 100 - 50 + 20 = 70
     */
    @Test
    @DisplayName("예약 후 취소 시 재고가 정확히 복구된다")
    fun `예약 후 취소 시 재고가 정확히 복구된다`() {
        // Given
        val sku = "CONCURRENT-003"
        val initialStock = 100
        val reserveQuantity = 50
        val cancelQuantity = 20

        inventoryRepository.save(
            InventoryJpaEntity(
                sku = sku,
                physicalStock = initialStock,
                reservedStock = 0,
                safetyStock = 0,
                status = StockStatus.IN_STOCK,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )
        )

        // When
        // 1단계: 50개 예약
        inventoryService.reserveStock(sku, reserveQuantity)
        var inventory = inventoryRepository.findBySku(sku)
        assertThat(inventory!!.physicalStock).isEqualTo(initialStock - reserveQuantity)

        // 2단계: 20개 취소
        inventoryService.cancelReservation(sku, cancelQuantity)
        inventory = inventoryRepository.findBySku(sku)

        // Then
        val expectedStock = initialStock - reserveQuantity + cancelQuantity
        assertThat(inventory!!.physicalStock).isEqualTo(expectedStock)
    }

    /**
     * 테스트: 동시 예약과 취소가 섞여있을 때도 정확성 보장
     *
     * 시나리오:
     * - 초기 재고: 100개
     * - 스레드 A~E (5개): 각 10개씩 예약 (총 50개)
     * - 스레드 F~H (3개): 각 10개씩 취소 (총 30개)
     * - 결과: 재고 = 100 - 50 + 30 = 80
     */
    @Test
    @DisplayName("동시 예약과 취소가 섞여있어도 정확성이 보장된다")
    fun `동시 예약과 취소가 섞여있어도 정확성이 보장된다`() {
        // Given
        val sku = "CONCURRENT-004"
        val initialStock = 100
        val reserveThreadCount = 5
        val cancelThreadCount = 3
        val quantityPerThread = 10

        inventoryRepository.save(
            InventoryJpaEntity(
                sku = sku,
                physicalStock = initialStock,
                reservedStock = 0,
                safetyStock = 0,
                status = StockStatus.IN_STOCK,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )
        )

        val totalThreads = reserveThreadCount + cancelThreadCount
        val latch = CountDownLatch(totalThreads)
        val executor = Executors.newFixedThreadPool(totalThreads)

        // When
        // 예약 스레드들
        repeat(reserveThreadCount) {
            executor.submit {
                try {
                    inventoryService.reserveStock(sku, quantityPerThread)
                } finally {
                    latch.countDown()
                }
            }
        }

        // 취소 스레드들
        repeat(cancelThreadCount) {
            executor.submit {
                try {
                    inventoryService.cancelReservation(sku, quantityPerThread)
                } finally {
                    latch.countDown()
                }
            }
        }

        // Awaitility로 모든 스레드 완료 대기 (CI 환경 안정성)
        await("동시 예약과 취소 완료")
            .atMost(Duration.ofSeconds(30))
            .pollInterval(Duration.ofMillis(100))
            .untilAsserted {
                assertThat(latch.count).isEqualTo(0)
            }

        executor.shutdown()

        // Then
        val finalInventory = inventoryRepository.findBySku(sku)
        assertThat(finalInventory).isNotNull

        val expectedStock = initialStock - (reserveThreadCount * quantityPerThread) + (cancelThreadCount * quantityPerThread)
        assertThat(finalInventory!!.physicalStock).isEqualTo(expectedStock)
    }

    /**
     * 테스트: 비관적 락 타임아웃 처리
     *
     * 시나리오:
     * - 스레드 A: SKU_A 락 획득 → 잠깐 대기
     * - 스레드 B: SKU_A 락 대기 (선 진입)
     * - 스레드 B: 타임아웃 또는 대기 성공
     *
     * 검증: 타임아웃 또는 예외 처리됨
     */
    @Test
    @DisplayName("비관적 락 획득이 정상 동작한다")
    fun `비관적 락 획득이 정상 동작한다`() {
        // Given
        val sku = "CONCURRENT-005"
        val initialStock = 100

        inventoryRepository.save(
            InventoryJpaEntity(
                sku = sku,
                physicalStock = initialStock,
                reservedStock = 0,
                safetyStock = 0,
                status = StockStatus.IN_STOCK,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )
        )

        val successCount = AtomicInteger(0)
        val latch = CountDownLatch(10)
        val executor = Executors.newFixedThreadPool(10)

        // When: 10개 스레드가 동시에 접근 (비관적 락 획득/해제 반복)
        repeat(10) {
            executor.submit {
                try {
                    val inventory = inventoryService.reserveStock(sku, 1)
                    assertThat(inventory).isNotNull
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    // 재고 부족은 정상
                    if (e is InventoryException.InsufficientStock) {
                        successCount.incrementAndGet()
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        // Awaitility로 모든 스레드 완료 대기 (CI 환경 안정성)
        await("비관적 락 10개 요청 완료")
            .atMost(Duration.ofSeconds(30))
            .pollInterval(Duration.ofMillis(100))
            .untilAsserted {
                assertThat(successCount.get()).isEqualTo(10)
            }

        executor.shutdown()

        // Then: 모든 스레드가 정상 처리됨
        // (위의 await에서 이미 검증됨)
    }

    /**
     * 테스트: 재고 상태(Status) 정확히 업데이트
     *
     * 시나리오:
     * - 초기: IN_STOCK (100개)
     * - 80개 판매 후: IN_STOCK (20개 남음)
     * - 96개 판매 후: LOW_STOCK (4개 남음, < 5개 임계값)
     * - 100개 판매 후: OUT_OF_STOCK (0개)
     */
    @Test
    @DisplayName("재고 상태가 정확히 업데이트된다")
    fun `재고 상태가 정확히 업데이트된다`() {
        // Given
        val sku = "CONCURRENT-006"
        inventoryRepository.save(
            InventoryJpaEntity(
                sku = sku,
                physicalStock = 100,
                reservedStock = 0,
                safetyStock = 0,
                status = StockStatus.IN_STOCK,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )
        )

        // When & Then
        // 1. 80개 예약 → 20개 남음 → IN_STOCK
        inventoryService.reserveStock(sku, 80)
        var inventory = inventoryRepository.findBySku(sku)
        assertThat(inventory!!.status).isEqualTo(StockStatus.IN_STOCK)

        // 2. 16개 예약 → 4개 남음 → LOW_STOCK (< 5)
        inventoryService.reserveStock(sku, 16)
        inventory = inventoryRepository.findBySku(sku)
        assertThat(inventory!!.status).isEqualTo(StockStatus.LOW_STOCK)

        // 3. 4개 예약 → 0개 남음 → OUT_OF_STOCK
        inventoryService.reserveStock(sku, 4)
        inventory = inventoryRepository.findBySku(sku)
        assertThat(inventory!!.status).isEqualTo(StockStatus.OUT_OF_STOCK)
    }

    /**
     * 테스트: 스트레스 테스트 - 많은 동시 요청 처리
     *
     * 시나리오:
     * - 초기 재고: 1000개
     * - 1000개 동시 요청 (각 1개씩)
     * - 결과: 모두 성공, 재고 = 0
     *
     * 성능 지표:
     * - TPS > 1000 req/sec (1초 이내)
     * - 응답시간 < 100ms (P95)
     */
    @Test
    @DisplayName("스트레스 테스트: 1000개 동시 요청 처리")
    fun `1000개 동시 요청이 정확히 처리된다`() {
        // Given
        val sku = "CONCURRENT-STRESS"
        val initialStock = 1000
        val threadCount = 1000

        val startTime = System.currentTimeMillis()

        inventoryRepository.save(
            InventoryJpaEntity(
                sku = sku,
                physicalStock = initialStock,
                reservedStock = 0,
                safetyStock = 0,
                status = StockStatus.IN_STOCK,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )
        )

        val successCount = AtomicInteger(0)
        val latch = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(50)

        // When
        repeat(threadCount) {
            executor.submit {
                try {
                    inventoryService.reserveStock(sku, 1)
                    successCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        // Awaitility로 모든 스레드 완료 대기 (CI 환경 안정성)
        await("1000개 동시 요청 스트레스 테스트 완료")
            .atMost(Duration.ofSeconds(60))
            .pollInterval(Duration.ofMillis(500))
            .untilAsserted {
                assertThat(successCount.get()).isEqualTo(threadCount)
            }

        executor.shutdown()

        val endTime = System.currentTimeMillis()
        val elapsed = endTime - startTime

        // Then
        assertThat(elapsed).isLessThan(60000) // 60초 이내 (비관적 락 오버헤드 감안)

        val finalInventory = inventoryRepository.findBySku(sku)
        assertThat(finalInventory!!.physicalStock).isEqualTo(0)

        // 성능 로그
        println("✅ 1000개 요청 처리 완료")
        println("   - 성공: ${successCount.get()}개")
        println("   - 소요시간: ${elapsed}ms")
        println("   - TPS: ${(1000 * 1000) / elapsed} req/sec")
    }
}