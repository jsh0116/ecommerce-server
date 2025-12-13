package io.hhplus.ecommerce.integration

import io.hhplus.ecommerce.application.services.InventoryService
import io.hhplus.ecommerce.domain.StockStatus
import io.hhplus.ecommerce.infrastructure.persistence.entity.InventoryJpaEntity
import io.hhplus.ecommerce.infrastructure.persistence.repository.InventoryJpaRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import io.hhplus.ecommerce.config.TestRedisConfig
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cache.CacheManager
import org.springframework.context.annotation.Import
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Tag("integration")
@Tag("redis-required")
@DisplayName("Spring Cache 통합 테스트")
@Import(TestRedisConfig::class)  // 실제 Redis 연결 사용
class CachingIntegrationTest {

    @Autowired
    private lateinit var inventoryService: InventoryService

    @Autowired
    private lateinit var inventoryRepository: InventoryJpaRepository

    @Autowired
    private lateinit var cacheManager: CacheManager

    @Autowired
    private lateinit var entityManager: jakarta.persistence.EntityManager

    private val testSku = "TEST-SKU-001"

    @BeforeEach
    fun setUp() {
        // 캐시 및 DB 초기화
        cacheManager.getCache("inventory")?.clear()
        inventoryRepository.deleteAll()

        // 테스트용 재고 생성
        val inventory = InventoryJpaEntity(
            sku = testSku,
            physicalStock = 100,
            safetyStock = 10,
            reservedStock = 0,
            status = StockStatus.IN_STOCK,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        inventoryRepository.save(inventory)
    }

    @Nested
    @DisplayName("@Cacheable 동작 테스트")
    inner class CacheableTest {

        @Test
        fun `재고 조회가 정상적으로 작동한다`() {
            // When
            val result = inventoryService.getInventory(testSku)

            // Then
            assertThat(result).isNotNull
            assertThat(result?.sku).isEqualTo(testSku)
            assertThat(result?.physicalStock).isEqualTo(100)
        }

        @Test
        fun `여러 번 조회해도 일관된 데이터를 반환한다`() {
            // When
            val result1 = inventoryService.getInventory(testSku)
            val result2 = inventoryService.getInventory(testSku)
            val result3 = inventoryService.getInventory(testSku)

            // Then - 모두 동일한 데이터
            assertThat(result1?.physicalStock).isEqualTo(100)
            assertThat(result2?.physicalStock).isEqualTo(100)
            assertThat(result3?.physicalStock).isEqualTo(100)
        }

        @Test
        fun `여러 SKU가 독립적으로 관리된다`() {
            // Given
            val sku1 = "SKU-001"
            val sku2 = "SKU-002"
            inventoryRepository.save(InventoryJpaEntity(sku = sku1, physicalStock = 100))
            inventoryRepository.save(InventoryJpaEntity(sku = sku2, physicalStock = 200))

            // When
            val result1 = inventoryService.getInventory(sku1)
            val result2 = inventoryService.getInventory(sku2)

            // Then
            assertThat(result1?.physicalStock).isEqualTo(100)
            assertThat(result2?.physicalStock).isEqualTo(200)
        }

        @Test
        fun `존재하지 않는 SKU는 null을 반환한다`() {
            // When
            val result = inventoryService.getInventory("NONEXISTENT-SKU")

            // Then
            assertThat(result).isNull()
        }
    }

    @Nested
    @DisplayName("@CacheEvict 동작 테스트")
    inner class CacheEvictTest {

        @Test
        fun `재고 예약 후 최신 데이터를 반환한다`() {
            // Given - 초기 조회
            val before = inventoryService.getInventory(testSku)
            assertThat(before?.physicalStock).isEqualTo(100)

            // When - 재고 예약 (CacheEvict 발동)
            inventoryService.reserveStock(testSku, 10)

            // Then - 변경된 데이터 반환 (reservedStock이 증가, physicalStock은 유지)
            val after = inventoryService.getInventory(testSku)
            assertThat(after?.physicalStock).isEqualTo(100) // 예약만 했으므로 유지
            assertThat(after?.reservedStock).isEqualTo(10) // 10개 예약됨
            assertThat(after?.getAvailableStock()).isEqualTo(80) // 가용 재고 = 100 - 10(reserved) - 10(safety) = 80
        }

        @Test
        fun `예약 확정 후 최신 데이터를 반환한다`() {
            // Given - 재고 예약 (100 -> 90)
            inventoryService.reserveStock(testSku, 10)

            // When - 예약 확정
            inventoryService.confirmReservation(testSku, 10)

            // Then - physicalStock은 이미 reserve에서 감소했으므로 그대로 90
            val after = inventoryService.getInventory(testSku)
            assertThat(after?.physicalStock).isEqualTo(90)
        }

        @Test
        fun `예약 취소 후 최신 데이터를 반환한다`() {
            // Given - 재고 예약 (100 -> 90)
            inventoryService.reserveStock(testSku, 10)

            // When - 예약 취소 (90 -> 100 복구)
            inventoryService.cancelReservation(testSku, 10)

            // Then - physicalStock이 원래대로 복구됨
            val after = inventoryService.getInventory(testSku)
            assertThat(after?.physicalStock).isEqualTo(100)
        }

        @Test
        fun `재고 복구 후 최신 데이터를 반환한다`() {
            // Given
            inventoryService.reserveStock(testSku, 10)
            inventoryService.confirmReservation(testSku, 10)

            // When
            inventoryService.restoreStock(testSku, 10)

            // Then
            val after = inventoryService.getInventory(testSku)
            assertThat(after?.physicalStock).isEqualTo(100)
        }
    }

    @Nested
    @DisplayName("캐시 성능 테스트")
    inner class CachePerformanceTest {

        @Test
        fun `반복 조회 시 빠른 응답 시간을 보인다`() {
            // Given - 첫 조회로 캐시 워밍업
            inventoryService.getInventory(testSku)

            // When - 100회 조회
            val startTime = System.currentTimeMillis()
            repeat(100) {
                inventoryService.getInventory(testSku)
            }
            val duration = System.currentTimeMillis() - startTime

            // Then - 100회 조회가 1초 이내
            assertThat(duration).isLessThan(1000)
        }
    }
}
