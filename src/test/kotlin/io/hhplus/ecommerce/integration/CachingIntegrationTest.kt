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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
@DisplayName("캐싱 통합 테스트 (Redis 실제 사용)")
class CachingIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var inventoryService: InventoryService

    @Autowired
    private lateinit var inventoryRepository: InventoryJpaRepository

    @Autowired
    private lateinit var redisTemplate: RedisTemplate<String, String>

    private val testSku = "TEST-SKU-001"
    private val cacheKey = "inventory:$testSku"

    @BeforeEach
    fun setUp() {
        // 캐시 및 DB 초기화
        redisTemplate.delete(cacheKey)
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
    @DisplayName("Cache-Aside 패턴 통합 테스트")
    inner class CacheAsideIntegrationTest {

        @Test
        fun `첫 번째 조회는 DB에서 데이터를 가져오고 캐시에 저장한다`() {
            // Given
            assertThat(redisTemplate.hasKey(cacheKey)).isFalse

            // When
            val inventory = inventoryService.getInventory(testSku)

            // Then
            assertThat(inventory).isNotNull
            assertThat(inventory?.sku).isEqualTo(testSku)

            // 캐시에 저장되었는지 확인
            val cachedValue = redisTemplate.opsForValue().get(cacheKey)
            assertThat(cachedValue).isNotNull
            assertThat(cachedValue).contains(testSku)
        }

        @Test
        fun `두 번째 조회는 캐시에서 데이터를 가져온다`() {
            // Given
            // 첫 번째 조회로 캐시 워밍업
            inventoryService.getInventory(testSku)

            // 캐시의 TTL 확인
            val ttl = redisTemplate.getExpire(cacheKey, TimeUnit.SECONDS)
            assertThat(ttl).isGreaterThan(0)

            // When
            val inventory = inventoryService.getInventory(testSku)

            // Then
            assertThat(inventory).isNotNull
            assertThat(inventory?.sku).isEqualTo(testSku)
        }

        @Test
        fun `캐시 TTL이 60초로 설정된다`() {
            // Given
            inventoryService.getInventory(testSku)

            // When
            val ttl = redisTemplate.getExpire(cacheKey, TimeUnit.SECONDS)

            // Then
            assertThat(ttl).isGreaterThan(50)  // 60초 이상 (네트워크 지연 고려)
            assertThat(ttl).isLessThanOrEqualTo(60)
        }

        @Test
        fun `캐시 데이터는 유효한 JSON 형식이다`() {
            // Given
            inventoryService.getInventory(testSku)

            // When
            val cachedValue = redisTemplate.opsForValue().get(cacheKey)

            // Then
            assertThat(cachedValue).isNotNull
            assertThat(cachedValue).contains("""{"id":""")
            assertThat(cachedValue).contains(""""sku":"$testSku"""")
        }
    }

    @Nested
    @DisplayName("캐시 무효화 통합 테스트")
    inner class CacheInvalidationIntegrationTest {

        @Test
        fun `재고 예약 후 캐시가 무효화된다`() {
            // Given
            // 캐시 워밍업
            inventoryService.getInventory(testSku)
            assertThat(redisTemplate.hasKey(cacheKey)).isTrue

            // When
            inventoryService.reserveStock(testSku, 10)

            // Then
            // 캐시가 삭제되어야 함
            assertThat(redisTemplate.hasKey(cacheKey)).isFalse
        }

        @Test
        fun `예약 확정 후 캐시가 무효화된다`() {
            // Given
            inventoryService.getInventory(testSku)
            inventoryService.reserveStock(testSku, 10)

            assertThat(redisTemplate.hasKey(cacheKey)).isFalse

            // 캐시 재설정
            inventoryService.getInventory(testSku)
            assertThat(redisTemplate.hasKey(cacheKey)).isTrue

            // When
            inventoryService.confirmReservation(testSku, 10)

            // Then
            assertThat(redisTemplate.hasKey(cacheKey)).isFalse
        }

        @Test
        fun `예약 취소 후 캐시가 무효화된다`() {
            // Given
            inventoryService.getInventory(testSku)
            inventoryService.reserveStock(testSku, 10)

            // 캐시 재설정
            inventoryService.getInventory(testSku)
            assertThat(redisTemplate.hasKey(cacheKey)).isTrue

            // When
            inventoryService.cancelReservation(testSku, 10)

            // Then
            assertThat(redisTemplate.hasKey(cacheKey)).isFalse
        }

        @Test
        fun `재고 복구 후 캐시가 무효화된다`() {
            // Given
            inventoryService.getInventory(testSku)
            inventoryService.reserveStock(testSku, 10)
            inventoryService.confirmReservation(testSku, 10)

            // 캐시 재설정
            inventoryService.getInventory(testSku)
            assertThat(redisTemplate.hasKey(cacheKey)).isTrue

            // When
            inventoryService.restoreStock(testSku, 10)

            // Then
            assertThat(redisTemplate.hasKey(cacheKey)).isFalse
        }
    }

    @Nested
    @DisplayName("캐시 일관성 테스트")
    inner class CacheConsistencyTest {

        @Test
        fun `캐시 무효화 후 다시 조회하면 최신 데이터를 가져온다`() {
            // Given
            val firstResult = inventoryService.getInventory(testSku)
            assertThat(firstResult?.physicalStock).isEqualTo(100)

            // 캐시 삭제
            redisTemplate.delete(cacheKey)

            // DB 데이터 수정
            val inventory = inventoryRepository.findAll().first()
            inventory.physicalStock = 50
            inventoryRepository.save(inventory)

            // When
            val secondResult = inventoryService.getInventory(testSku)

            // Then
            // DB에서 최신 데이터를 조회해야 함
            assertThat(secondResult?.physicalStock).isEqualTo(50)
        }

        @Test
        fun `캐시가 있으면 이전 값을 반환한다 (TTL 만료 전)`() {
            // Given
            val firstResult = inventoryService.getInventory(testSku)
            val initialStock = firstResult?.physicalStock

            // DB 데이터 수정 (하지만 캐시는 유지)
            val inventory = inventoryRepository.findAll().first()
            inventory.physicalStock = 30
            inventoryRepository.save(inventory)

            // When
            val secondResult = inventoryService.getInventory(testSku)

            // Then
            // 캐시에서 조회했으므로 이전 값을 반환
            assertThat(secondResult?.physicalStock).isEqualTo(initialStock)
        }

        @Test
        fun `여러 SKU의 캐시가 독립적으로 관리된다`() {
            // Given
            val sku1 = "SKU-001"
            val sku2 = "SKU-002"
            val cacheKey1 = "inventory:$sku1"
            val cacheKey2 = "inventory:$sku2"

            val inv1 = InventoryJpaEntity(sku = sku1, physicalStock = 100)
            val inv2 = InventoryJpaEntity(sku = sku2, physicalStock = 200)
            inventoryRepository.saveAll(listOf(inv1, inv2))

            // When
            inventoryService.getInventory(sku1)
            inventoryService.getInventory(sku2)

            // Then
            assertThat(redisTemplate.hasKey(cacheKey1)).isTrue
            assertThat(redisTemplate.hasKey(cacheKey2)).isTrue

            val cached1 = redisTemplate.opsForValue().get(cacheKey1)
            val cached2 = redisTemplate.opsForValue().get(cacheKey2)

            assertThat(cached1).contains(sku1)
            assertThat(cached2).contains(sku2)
        }
    }

    @Nested
    @DisplayName("캐시 오류 처리 테스트")
    inner class CacheErrorHandlingTest {

        @Test
        fun `캐시 역직렬화 오류 시 DB에서 조회한다`() {
            // Given
            // 잘못된 JSON을 캐시에 저장
            val invalidJson = "{invalid json"
            redisTemplate.opsForValue().set(cacheKey, invalidJson, 60, TimeUnit.SECONDS)

            // When
            val result = inventoryService.getInventory(testSku)

            // Then
            assertThat(result).isNotNull
            assertThat(result?.sku).isEqualTo(testSku)

            // 캐시가 삭제되었는지 확인
            assertThat(redisTemplate.hasKey(cacheKey)).isFalse
        }

        @Test
        fun `존재하지 않는 SKU 조회는 null을 반환한다`() {
            // Given
            val nonExistentSku = "NONEXISTENT-SKU"

            // When
            val result = inventoryService.getInventory(nonExistentSku)

            // Then
            assertThat(result).isNull()

            // 캐시에 저장되지 않아야 함
            assertThat(redisTemplate.hasKey("inventory:$nonExistentSku")).isFalse
        }
    }

    @Nested
    @DisplayName("캐시 성능 테스트")
    inner class CachePerformanceTest {

        @Test
        fun `캐시 히트는 DB 조회보다 빠르다`() {
            // Given
            // 워밍업
            inventoryService.getInventory(testSku)

            // When - 캐시 히트
            val startCache = System.nanoTime()
            repeat(100) {
                inventoryService.getInventory(testSku)
            }
            val cacheDuration = System.nanoTime() - startCache

            // Then
            // 캐시 100회 조회는 매우 빨라야 함 (1ms 이내)
            val cacheDurationMs = cacheDuration / 1_000_000
            assertThat(cacheDurationMs).isLessThan(100)  // 100회에 100ms 이내
        }
    }
}
