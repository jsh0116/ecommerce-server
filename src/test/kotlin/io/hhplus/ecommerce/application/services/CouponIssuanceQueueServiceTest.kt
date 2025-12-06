package io.hhplus.ecommerce.application.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.ListOperations
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.util.concurrent.TimeUnit

@DisplayName("CouponIssuanceQueueService 테스트")
class CouponIssuanceQueueServiceTest {

    private val redisTemplate = mockk<RedisTemplate<String, String>>(relaxed = true)
    private val objectMapper = ObjectMapper().registerKotlinModule()
    private val service = CouponIssuanceQueueService(redisTemplate, objectMapper)

    private val listOps = mockk<ListOperations<String, String>>(relaxed = true)
    private val valueOps = mockk<ValueOperations<String, String>>(relaxed = true)

    @Nested
    @DisplayName("대기열 추가 테스트")
    inner class EnqueueTest {

        @Test
        fun `쿠폰 발급 요청을 대기열에 추가할 수 있다`() {
            // Given
            val request = CouponIssuanceQueueService.CouponIssuanceRequest(
                requestId = "req-001",
                couponId = 1L,
                userId = 100L
            )

            every { redisTemplate.opsForList() } returns listOps
            every { listOps.leftPush(any(), any()) } returns 1L

            // When
            val result = service.enqueue(request)

            // Then
            assertThat(result).isTrue
            verify { listOps.leftPush("coupon:queue:pending", any()) }
        }

        @Test
        fun `Redis 장애 시 false를 반환한다`() {
            // Given
            val request = CouponIssuanceQueueService.CouponIssuanceRequest(
                requestId = "req-002",
                couponId = 2L,
                userId = 200L
            )

            every { redisTemplate.opsForList() } returns listOps
            every { listOps.leftPush(any(), any()) } throws RuntimeException("Redis connection failed")

            // When
            val result = service.enqueue(request)

            // Then
            assertThat(result).isFalse
        }
    }

    @Nested
    @DisplayName("대기열 꺼내기 테스트")
    inner class DequeueTest {

        @Test
        fun `대기열에서 요청을 꺼낼 수 있다`() {
            // Given
            val request = CouponIssuanceQueueService.CouponIssuanceRequest(
                requestId = "req-001",
                couponId = 1L,
                userId = 100L
            )
            val requestJson = objectMapper.writeValueAsString(request)

            every { redisTemplate.opsForList() } returns listOps
            every { redisTemplate.opsForValue() } returns valueOps
            every { listOps.rightPop("coupon:queue:pending") } returns requestJson
            every { valueOps.set(any(), any(), any(), any<TimeUnit>()) } just runs

            // When
            val result = service.dequeue()

            // Then
            assertThat(result).isNotNull
            assertThat(result?.requestId).isEqualTo("req-001")
            assertThat(result?.couponId).isEqualTo(1L)
            assertThat(result?.userId).isEqualTo(100L)
            verify { listOps.rightPop("coupon:queue:pending") }
            verify { valueOps.set(match { it.startsWith("coupon:queue:processing:") }, any(), 5L, TimeUnit.MINUTES) }
        }

        @Test
        fun `대기열이 비어있으면 null을 반환한다`() {
            // Given
            every { redisTemplate.opsForList() } returns listOps
            every { listOps.rightPop("coupon:queue:pending") } returns null

            // When
            val result = service.dequeue()

            // Then
            assertThat(result).isNull()
        }

        @Test
        fun `Redis 장애 시 null을 반환한다`() {
            // Given
            every { redisTemplate.opsForList() } returns listOps
            every { listOps.rightPop(any()) } throws RuntimeException("Redis error")

            // When
            val result = service.dequeue()

            // Then
            assertThat(result).isNull()
        }
    }

    @Nested
    @DisplayName("배치 꺼내기 테스트")
    inner class DequeueBatchTest {

        @Test
        fun `배치 크기만큼 요청을 꺼낼 수 있다`() {
            // Given
            val requests = (1..3).map {
                CouponIssuanceQueueService.CouponIssuanceRequest(
                    requestId = "req-$it",
                    couponId = it.toLong(),
                    userId = it * 100L
                )
            }

            every { redisTemplate.opsForList() } returns listOps
            every { redisTemplate.opsForValue() } returns valueOps

            requests.forEach { request ->
                val requestJson = objectMapper.writeValueAsString(request)
                every { listOps.rightPop("coupon:queue:pending") } returnsMany listOf(requestJson, null)
                every { valueOps.set(any(), any(), any(), any<TimeUnit>()) } just runs
            }

            // When
            val result = service.dequeueBatch(3)

            // Then
            assertThat(result).hasSize(1)
        }
    }

    @Nested
    @DisplayName("처리 완료 마킹 테스트")
    inner class MarkAsCompletedTest {

        @Test
        fun `처리 완료 마킹을 할 수 있다`() {
            // Given
            val requestId = "req-001"

            every { redisTemplate.delete(any<String>()) } returns true

            // When
            service.markAsCompleted(requestId)

            // Then
            verify { redisTemplate.delete("coupon:queue:processing:$requestId") }
        }
    }

    @Nested
    @DisplayName("처리 실패 마킹 테스트")
    inner class MarkAsFailedTest {

        @Test
        fun `처리 실패 마킹을 할 수 있다`() {
            // Given
            val request = CouponIssuanceQueueService.CouponIssuanceRequest(
                requestId = "req-001",
                couponId = 1L,
                userId = 100L
            )

            every { redisTemplate.delete(any<String>()) } returns true
            every { redisTemplate.opsForList() } returns listOps
            every { listOps.leftPush(any(), any()) } returns 1L

            // When
            service.markAsFailed(request, "테스트 실패")

            // Then
            verify { redisTemplate.delete("coupon:queue:processing:${request.requestId}") }
            verify { listOps.leftPush("coupon:queue:failed", any()) }
        }
    }

    @Nested
    @DisplayName("대기열 크기 조회 테스트")
    inner class GetQueueSizeTest {

        @Test
        fun `대기 중인 요청 수를 조회할 수 있다`() {
            // Given
            every { redisTemplate.opsForList() } returns listOps
            every { listOps.size("coupon:queue:pending") } returns 10L

            // When
            val result = service.getPendingCount()

            // Then
            assertThat(result).isEqualTo(10L)
        }

        @Test
        fun `실패한 요청 수를 조회할 수 있다`() {
            // Given
            every { redisTemplate.opsForList() } returns listOps
            every { listOps.size("coupon:queue:failed") } returns 5L

            // When
            val result = service.getFailedCount()

            // Then
            assertThat(result).isEqualTo(5L)
        }
    }
}
