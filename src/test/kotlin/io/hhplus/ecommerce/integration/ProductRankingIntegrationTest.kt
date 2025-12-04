package io.hhplus.ecommerce.integration

import io.hhplus.ecommerce.application.services.ProductRankingService
import io.hhplus.ecommerce.config.TestContainersConfig
import io.hhplus.ecommerce.config.TestRedisConfig
import io.hhplus.ecommerce.domain.Product
import io.hhplus.ecommerce.infrastructure.persistence.entity.ProductJpaEntity
import io.hhplus.ecommerce.infrastructure.persistence.repository.ProductJpaRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * ProductRankingService 통합 테스트
 *
 * STEP 13: Redis 기반 실시간 랭킹 시스템 통합 테스트
 * - 실제 Redis와 MySQL을 사용한 통합 테스트
 * - Redis Sorted Set의 ZINCRBY, ZREVRANGE, ZREVRANK 동작 검증
 * - TTL 설정 및 키 네이밍 전략 검증
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Tag("integration")
@DisplayName("[STEP 13] ProductRankingService 통합 테스트")
@ActiveProfiles("test")
@Import(TestContainersConfig::class, TestRedisConfig::class)
class ProductRankingIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var productRankingService: ProductRankingService

    @Autowired
    private lateinit var productRepository: ProductJpaRepository

    @Autowired
    private lateinit var redisTemplate: RedisTemplate<String, String>

    private val testProducts = mutableListOf<Product>()

    @BeforeEach
    fun setUp() {
        // Redis 초기화 (ranking 관련 키만)
        val keys = redisTemplate.keys("ranking:products:*")
        if (keys.isNotEmpty()) {
            redisTemplate.delete(keys)
        }

        // DB 초기화 및 테스트 상품 생성
        productRepository.deleteAll()

        testProducts.clear()
        for (i in 1L..10L) {
            val entity = ProductJpaEntity(
                id = 0L,  // JPA가 자동 생성하도록 0으로 설정
                name = "상품 $i",
                description = "테스트 상품 $i",
                price = 10000L * i,
                category = "test-category",
                viewCount = 0L,
                salesCount = 0L,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )
            // 저장 후 생성된 ID를 사용
            val savedEntity = productRepository.save(entity)

            testProducts.add(
                Product(
                    id = savedEntity.id,  // 생성된 ID 사용
                    name = savedEntity.name,
                    description = savedEntity.description,
                    price = savedEntity.price,
                    category = savedEntity.category,
                    viewCount = savedEntity.viewCount,
                    salesCount = savedEntity.salesCount,
                    createdAt = savedEntity.createdAt
                )
            )
        }
    }

    @Nested
    @DisplayName("판매량 증가 테스트")
    inner class IncrementSalesTest {

        @Test
        fun `판매량 증가 시 Redis Sorted Set에 정상적으로 저장된다`() {
            // Given
            val productId = testProducts[0].id
            val quantity = 10

            // When
            productRankingService.incrementSales(productId, quantity)

            // Then
            val score = redisTemplate.opsForZSet()
                .score("ranking:products:daily:${LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))}", productId.toString())

            assertThat(score).isNotNull
            assertThat(score).isEqualTo(quantity.toDouble())
        }

        @Test
        fun `동일 상품의 판매량을 여러 번 증가시키면 누적된다`() {
            // Given
            val productId = testProducts[0].id

            // When
            productRankingService.incrementSales(productId, 5)
            productRankingService.incrementSales(productId, 3)
            productRankingService.incrementSales(productId, 2)

            // Then
            val salesCount = productRankingService.getProductSalesCount(productId)
            assertThat(salesCount).isEqualTo(10L)
        }

        @Test
        fun `여러 상품의 판매량을 증가시킬 수 있다`() {
            // When
            productRankingService.incrementSales(testProducts[0].id, 10)
            productRankingService.incrementSales(testProducts[1].id, 20)
            productRankingService.incrementSales(testProducts[2].id, 15)

            // Then
            assertThat(productRankingService.getProductSalesCount(testProducts[0].id)).isEqualTo(10L)
            assertThat(productRankingService.getProductSalesCount(testProducts[1].id)).isEqualTo(20L)
            assertThat(productRankingService.getProductSalesCount(testProducts[2].id)).isEqualTo(15L)
        }
    }

    @Nested
    @DisplayName("일간 TOP N 조회 테스트")
    inner class DailyTopProductsTest {

        @Test
        fun `일간 TOP 5 상품을 판매량 순으로 조회한다`() {
            // Given - 10개 상품에 랜덤 판매량 설정
            productRankingService.incrementSales(testProducts[0].id, 100)
            productRankingService.incrementSales(testProducts[1].id, 200)
            productRankingService.incrementSales(testProducts[2].id, 150)
            productRankingService.incrementSales(testProducts[3].id, 50)
            productRankingService.incrementSales(testProducts[4].id, 300)

            // When
            val topProducts = productRankingService.getTopProductsDaily(limit = 5)

            // Then
            assertThat(topProducts).hasSize(5)
            assertThat(topProducts[0].rank).isEqualTo(1)
            assertThat(topProducts[0].productId).isEqualTo(testProducts[4].id)
            assertThat(topProducts[0].salesCount).isEqualTo(300L)

            assertThat(topProducts[1].rank).isEqualTo(2)
            assertThat(topProducts[1].productId).isEqualTo(testProducts[1].id)
            assertThat(topProducts[1].salesCount).isEqualTo(200L)

            assertThat(topProducts[2].rank).isEqualTo(3)
            assertThat(topProducts[2].productId).isEqualTo(testProducts[2].id)
            assertThat(topProducts[2].salesCount).isEqualTo(150L)
        }

        @Test
        fun `판매 이력이 없으면 빈 리스트를 반환한다`() {
            // When
            val topProducts = productRankingService.getTopProductsDaily(limit = 10)

            // Then
            assertThat(topProducts).isEmpty()
        }

        @Test
        fun `요청한 limit보다 적은 상품만 있으면 있는 만큼만 반환한다`() {
            // Given
            productRankingService.incrementSales(testProducts[0].id, 100)
            productRankingService.incrementSales(testProducts[1].id, 200)

            // When
            val topProducts = productRankingService.getTopProductsDaily(limit = 10)

            // Then
            assertThat(topProducts).hasSize(2)
        }
    }

    @Nested
    @DisplayName("상품 순위 조회 테스트")
    inner class ProductRankTest {

        @Test
        fun `특정 상품의 일간 순위를 조회한다`() {
            // Given
            productRankingService.incrementSales(testProducts[0].id, 100)
            productRankingService.incrementSales(testProducts[1].id, 200)
            productRankingService.incrementSales(testProducts[2].id, 150)

            // When
            val rank1 = productRankingService.getProductRankDaily(testProducts[0].id)
            val rank2 = productRankingService.getProductRankDaily(testProducts[1].id)
            val rank3 = productRankingService.getProductRankDaily(testProducts[2].id)

            // Then
            assertThat(rank1).isEqualTo(3)  // 100개 판매 -> 3위
            assertThat(rank2).isEqualTo(1)  // 200개 판매 -> 1위
            assertThat(rank3).isEqualTo(2)  // 150개 판매 -> 2위
        }

        @Test
        fun `판매 이력이 없는 상품은 순위가 null이다`() {
            // When
            val rank = productRankingService.getProductRankDaily(999L)

            // Then
            assertThat(rank).isNull()
        }
    }

    @Nested
    @DisplayName("주간 랭킹 테스트")
    inner class WeeklyRankingTest {

        @Test
        fun `주간 TOP 5 상품을 조회한다`() {
            // Given
            productRankingService.incrementSales(testProducts[0].id, 1000)
            productRankingService.incrementSales(testProducts[1].id, 2000)
            productRankingService.incrementSales(testProducts[2].id, 1500)

            // When
            val topProducts = productRankingService.getTopProductsWeekly(limit = 5)

            // Then
            assertThat(topProducts).hasSize(3)
            assertThat(topProducts[0].productId).isEqualTo(testProducts[1].id)
            assertThat(topProducts[1].productId).isEqualTo(testProducts[2].id)
            assertThat(topProducts[2].productId).isEqualTo(testProducts[0].id)
        }

        @Test
        fun `주간 순위를 조회한다`() {
            // Given
            productRankingService.incrementSales(testProducts[0].id, 100)
            productRankingService.incrementSales(testProducts[1].id, 200)

            // When
            val rank1 = productRankingService.getProductRankWeekly(testProducts[0].id)
            val rank2 = productRankingService.getProductRankWeekly(testProducts[1].id)

            // Then
            assertThat(rank1).isEqualTo(2)
            assertThat(rank2).isEqualTo(1)
        }
    }

    @Nested
    @DisplayName("동시성 테스트")
    inner class ConcurrencyTest {

        @Test
        fun `여러 스레드에서 동시에 판매량을 증가시켜도 정확하게 집계된다`() {
            // Given
            val productId = testProducts[0].id
            val threadCount = 10
            val incrementPerThread = 10

            // When - 10개 스레드가 각각 10번씩 증가 (총 100번)
            val threads = (1..threadCount).map {
                Thread {
                    repeat(incrementPerThread) {
                        productRankingService.incrementSales(productId, 1)
                    }
                }
            }

            threads.forEach { it.start() }
            threads.forEach { it.join() }

            // Then
            val totalSales = productRankingService.getProductSalesCount(productId)
            assertThat(totalSales).isEqualTo((threadCount * incrementPerThread).toLong())
        }
    }

    @Nested
    @DisplayName("Redis Key 전략 테스트")
    inner class KeyStrategyTest {

        @Test
        fun `일간 랭킹 키가 올바른 형식으로 생성된다`() {
            // Given
            val productId = testProducts[0].id
            val today = LocalDate.now()
            val expectedKey = "ranking:products:daily:${today.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))}"

            // When
            productRankingService.incrementSales(productId, 10, today)

            // Then
            val exists = redisTemplate.hasKey(expectedKey)
            assertThat(exists).isTrue
        }

        @Test
        fun `주간 랭킹 키가 올바른 형식으로 생성된다`() {
            // Given
            val productId = testProducts[0].id
            val today = LocalDate.now()
            val expectedKeyPrefix = "ranking:products:weekly:"

            // When
            productRankingService.incrementSales(productId, 10, today)

            // Then
            val keys = redisTemplate.keys("$expectedKeyPrefix*")
            assertThat(keys).isNotEmpty
        }
    }
}