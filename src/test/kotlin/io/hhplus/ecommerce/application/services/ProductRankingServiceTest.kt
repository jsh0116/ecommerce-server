package io.hhplus.ecommerce.application.services

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ZSetOperations
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@DisplayName("ProductRankingService 테스트")
class ProductRankingServiceTest {

    private val redisTemplate = mockk<RedisTemplate<String, String>>(relaxed = true)
    private val service = ProductRankingService(redisTemplate)

    private val zSetOps = mockk<ZSetOperations<String, String>>(relaxed = true)

    @Nested
    @DisplayName("판매량 증가 테스트")
    inner class IncrementSalesTest {

        @Test
        fun `상품 판매 시 랭킹을 증가시킬 수 있다`() {
            // Given
            val productId = 1L
            val quantity = 5
            val date = LocalDate.of(2024, 12, 6)

            every { redisTemplate.opsForZSet() } returns zSetOps
            every { zSetOps.incrementScore(any(), any(), any()) } returns 5.0
            every { redisTemplate.getExpire(any(), any()) } returns -1L
            every { redisTemplate.expire(any(), any(), any<TimeUnit>()) } returns true

            // When
            service.incrementSales(productId, quantity, date)

            // Then
            verify { zSetOps.incrementScore(match { it.startsWith("ranking:products:daily:") }, "1", 5.0) }
            verify { zSetOps.incrementScore(match { it.startsWith("ranking:products:weekly:") }, "1", 5.0) }
        }

        @Test
        fun `Redis 장애 시 예외를 전파하지 않는다`() {
            // Given
            every { redisTemplate.opsForZSet() } throws RuntimeException("Redis error")

            // When & Then (예외가 발생하지 않음)
            service.incrementSales(1L, 5)
        }
    }

    @Nested
    @DisplayName("TOP N 상품 ID 조회 테스트")
    inner class GetTopProductIdsTest {

        @Test
        fun `일간 TOP N 상품 ID를 조회할 수 있다`() {
            // Given
            val productIds = setOf("1", "2", "3")
            val date = LocalDate.of(2024, 12, 6)

            every { redisTemplate.opsForZSet() } returns zSetOps
            every { zSetOps.reverseRange(any(), 0L, 9L) } returns productIds
            every { zSetOps.score(any(), "1") } returns 100.0
            every { zSetOps.score(any(), "2") } returns 80.0
            every { zSetOps.score(any(), "3") } returns 60.0

            // When
            val result = service.getTopProductIdsDaily(10, date)

            // Then
            assertThat(result).hasSize(3)
            assertThat(result[0].rank).isEqualTo(1)
            assertThat(result[0].productId).isEqualTo(1L)
            assertThat(result[0].salesCount).isEqualTo(100L)
            assertThat(result[1].productId).isEqualTo(2L)
            assertThat(result[2].productId).isEqualTo(3L)
        }

        @Test
        fun `주간 TOP N 상품 ID를 조회할 수 있다`() {
            // Given
            val productIds = setOf("5", "10")
            val date = LocalDate.of(2024, 12, 6)

            every { redisTemplate.opsForZSet() } returns zSetOps
            every { zSetOps.reverseRange(any(), 0L, 9L) } returns productIds
            every { zSetOps.score(any(), "5") } returns 200.0
            every { zSetOps.score(any(), "10") } returns 150.0

            // When
            val result = service.getTopProductIdsWeekly(10, date)

            // Then
            assertThat(result).hasSize(2)
            assertThat(result[0].productId).isEqualTo(5L)
            assertThat(result[0].salesCount).isEqualTo(200L)
            assertThat(result[1].productId).isEqualTo(10L)
        }

        @Test
        fun `랭킹이 비어있으면 빈 목록을 반환한다`() {
            // Given
            every { redisTemplate.opsForZSet() } returns zSetOps
            every { zSetOps.reverseRange(any(), any(), any()) } returns emptySet()

            // When
            val result = service.getTopProductIdsDaily(10)

            // Then
            assertThat(result).isEmpty()
        }

        @Test
        fun `Redis 장애 시 빈 목록을 반환한다`() {
            // Given
            every { redisTemplate.opsForZSet() } throws RuntimeException("Redis error")

            // When
            val result = service.getTopProductIdsDaily(10)

            // Then
            assertThat(result).isEmpty()
        }

        @Test
        fun `잘못된 형식의 Product ID는 제외된다`() {
            // Given
            val productIds = setOf("1", "invalid", "3")

            every { redisTemplate.opsForZSet() } returns zSetOps
            every { zSetOps.reverseRange(any(), 0L, 9L) } returns productIds
            every { zSetOps.score(any(), "1") } returns 100.0
            every { zSetOps.score(any(), "3") } returns 60.0

            // When
            val result = service.getTopProductIdsDaily(10)

            // Then
            assertThat(result).hasSize(2)
            assertThat(result.map { it.productId }).containsExactly(1L, 3L)
        }
    }

    @Nested
    @DisplayName("상품 순위 조회 테스트")
    inner class GetProductRankTest {

        @Test
        fun `특정 상품의 일간 순위를 조회할 수 있다`() {
            // Given
            val productId = 1L

            every { redisTemplate.opsForZSet() } returns zSetOps
            every { zSetOps.reverseRank(any(), "1") } returns 4L // 0-based index

            // When
            val rank = service.getProductRankDaily(productId)

            // Then
            assertThat(rank).isEqualTo(5) // 1-based rank
        }

        @Test
        fun `특정 상품의 주간 순위를 조회할 수 있다`() {
            // Given
            val productId = 2L

            every { redisTemplate.opsForZSet() } returns zSetOps
            every { zSetOps.reverseRank(any(), "2") } returns 0L // 1등

            // When
            val rank = service.getProductRankWeekly(productId)

            // Then
            assertThat(rank).isEqualTo(1)
        }

        @Test
        fun `랭킹에 없는 상품은 null을 반환한다`() {
            // Given
            every { redisTemplate.opsForZSet() } returns zSetOps
            every { zSetOps.reverseRank(any(), any()) } returns null

            // When
            val rank = service.getProductRankDaily(999L)

            // Then
            assertThat(rank).isNull()
        }
    }

    @Nested
    @DisplayName("판매량 조회 테스트")
    inner class GetProductSalesCountTest {

        @Test
        fun `특정 상품의 판매량을 조회할 수 있다`() {
            // Given
            val productId = 1L

            every { redisTemplate.opsForZSet() } returns zSetOps
            every { zSetOps.score(any(), "1") } returns 150.0

            // When
            val salesCount = service.getProductSalesCount(productId)

            // Then
            assertThat(salesCount).isEqualTo(150L)
        }

        @Test
        fun `판매 기록이 없으면 0을 반환한다`() {
            // Given
            every { redisTemplate.opsForZSet() } returns zSetOps
            every { zSetOps.score(any(), any()) } returns null

            // When
            val salesCount = service.getProductSalesCount(999L)

            // Then
            assertThat(salesCount).isEqualTo(0L)
        }

        @Test
        fun `Redis 장애 시 0을 반환한다`() {
            // Given
            every { redisTemplate.opsForZSet() } throws RuntimeException("Redis error")

            // When
            val salesCount = service.getProductSalesCount(1L)

            // Then
            assertThat(salesCount).isEqualTo(0L)
        }
    }
}
