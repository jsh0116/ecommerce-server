package io.hhplus.ecommerce.application.services

import io.hhplus.ecommerce.domain.Coupon
import io.hhplus.ecommerce.domain.CouponType
import io.hhplus.ecommerce.domain.User
import io.hhplus.ecommerce.exception.CouponException
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

@DisplayName("CouponIssuanceProcessor 테스트")
class CouponIssuanceProcessorTest {

    private val couponIssuanceQueueService = mockk<CouponIssuanceQueueService>(relaxed = true)
    private val couponIssuanceService = mockk<CouponIssuanceService>(relaxed = true)
    private val couponService = mockk<CouponService>(relaxed = true)
    private val userService = mockk<UserService>(relaxed = true)
    private val couponEventPublisher = mockk<CouponEventPublisher>(relaxed = true)

    private val processor = CouponIssuanceProcessor(
        couponIssuanceQueueService,
        couponIssuanceService,
        couponService,
        userService,
        couponEventPublisher
    )

    @Nested
    @DisplayName("배치 처리 테스트")
    inner class ProcessBatchTest {

        @Test
        fun `대기열에서 요청을 배치로 처리할 수 있다`() {
            // Given
            val request = CouponIssuanceQueueService.CouponIssuanceRequest(
                requestId = "req-001",
                couponId = 1L,
                userId = 100L
            )
            val user = User(id = 100L, balance = 100000L, createdAt = "2024-01-01")
            val coupon = Coupon(
                id = 1L, code = "COUPON-001", name = "테스트 쿠폰",
                type = CouponType.FIXED_AMOUNT, discount = 5000L, discountRate = 10,
                totalQuantity = 100, issuedQuantity = 0,
                startDate = LocalDateTime.now().minusDays(1),
                endDate = LocalDateTime.now().plusDays(7)
            )

            every { couponIssuanceQueueService.dequeueBatch(100) } returns listOf(request)
            every { couponIssuanceService.checkIssuanceEligibility(1L, 100L) } just runs
            every { userService.getById(100L) } returns user
            every { couponService.getById(1L) } returns coupon
            every { couponService.save(any()) } just runs
            every { couponService.saveUserCoupon(any()) } just runs
            every { couponIssuanceService.recordIssuance(1L, 100L) } returns 99L
            every { couponIssuanceQueueService.markAsCompleted(any()) } just runs

            // When
            val processedCount = processor.processBatch(100)

            // Then
            assertThat(processedCount).isEqualTo(1)
            verify { couponService.save(any()) }
            verify { couponService.saveUserCoupon(any()) }
            verify { couponIssuanceService.recordIssuance(1L, 100L) }
            verify { couponIssuanceQueueService.markAsCompleted("req-001") }
        }

        @Test
        fun `대기열이 비어있으면 0을 반환한다`() {
            // Given
            every { couponIssuanceQueueService.dequeueBatch(100) } returns emptyList()

            // When
            val processedCount = processor.processBatch(100)

            // Then
            assertThat(processedCount).isEqualTo(0)
        }

        @Test
        fun `비즈니스 룰 위반 시 실패 큐로 이동한다`() {
            // Given
            val request = CouponIssuanceQueueService.CouponIssuanceRequest(
                requestId = "req-002",
                couponId = 2L,
                userId = 200L
            )

            every { couponIssuanceQueueService.dequeueBatch(100) } returns listOf(request)
            every { couponIssuanceService.checkIssuanceEligibility(2L, 200L) } throws CouponException.AlreadyIssuedCoupon()
            every { couponIssuanceQueueService.markAsFailed(any(), any()) } just runs

            // When
            val processedCount = processor.processBatch(100)

            // Then
            assertThat(processedCount).isEqualTo(0)
            verify { couponIssuanceQueueService.markAsFailed(request, "이미 발급받은 쿠폰입니다") }
        }

        @Test
        fun `시스템 오류 시 실패 큐로 이동한다`() {
            // Given
            val request = CouponIssuanceQueueService.CouponIssuanceRequest(
                requestId = "req-003",
                couponId = 3L,
                userId = 300L
            )

            every { couponIssuanceQueueService.dequeueBatch(100) } returns listOf(request)
            every { couponIssuanceService.checkIssuanceEligibility(3L, 300L) } throws RuntimeException("DB 연결 오류")
            every { couponIssuanceQueueService.markAsFailed(any(), any()) } just runs

            // When
            val processedCount = processor.processBatch(100)

            // Then
            assertThat(processedCount).isEqualTo(0)
            verify { couponIssuanceQueueService.markAsFailed(request, "DB 연결 오류") }
        }

        @Test
        fun `여러 요청을 배치로 처리할 수 있다`() {
            // Given
            val requests = listOf(
                CouponIssuanceQueueService.CouponIssuanceRequest("req-1", 1L, 101L),
                CouponIssuanceQueueService.CouponIssuanceRequest("req-2", 1L, 102L),
                CouponIssuanceQueueService.CouponIssuanceRequest("req-3", 1L, 103L)
            )

            val user1 = User(id = 101L, balance = 100000L, createdAt = "2024-01-01")
            val user2 = User(id = 102L, balance = 100000L, createdAt = "2024-01-01")
            val user3 = User(id = 103L, balance = 100000L, createdAt = "2024-01-01")

            val coupon = Coupon(
                id = 1L, code = "COUPON-001", name = "테스트 쿠폰",
                type = CouponType.FIXED_AMOUNT, discount = 5000L, discountRate = 10,
                totalQuantity = 100, issuedQuantity = 0,
                startDate = LocalDateTime.now().minusDays(1),
                endDate = LocalDateTime.now().plusDays(7)
            )

            every { couponIssuanceQueueService.dequeueBatch(100) } returns requests
            every { couponIssuanceService.checkIssuanceEligibility(any(), any()) } just runs
            every { userService.getById(101L) } returns user1
            every { userService.getById(102L) } returns user2
            every { userService.getById(103L) } returns user3
            every { couponService.getById(1L) } returns coupon
            every { couponService.save(any()) } just runs
            every { couponService.saveUserCoupon(any()) } just runs
            every { couponIssuanceService.recordIssuance(any(), any()) } returns 97L
            every { couponIssuanceQueueService.markAsCompleted(any()) } just runs

            // When
            val processedCount = processor.processBatch(100)

            // Then
            assertThat(processedCount).isEqualTo(3)
            verify(exactly = 3) { couponService.saveUserCoupon(any()) }
            verify(exactly = 3) { couponIssuanceQueueService.markAsCompleted(any()) }
        }

        @Test
        fun `일부 요청 성공, 일부 실패 시 각각 처리된다`() {
            // Given
            val requests = listOf(
                CouponIssuanceQueueService.CouponIssuanceRequest("req-success", 1L, 101L),
                CouponIssuanceQueueService.CouponIssuanceRequest("req-fail", 1L, 102L)
            )

            val user = User(id = 101L, balance = 100000L, createdAt = "2024-01-01")
            val coupon = Coupon(
                id = 1L, code = "COUPON-001", name = "테스트 쿠폰",
                type = CouponType.FIXED_AMOUNT, discount = 5000L, discountRate = 10,
                totalQuantity = 100, issuedQuantity = 0,
                startDate = LocalDateTime.now().minusDays(1),
                endDate = LocalDateTime.now().plusDays(7)
            )

            every { couponIssuanceQueueService.dequeueBatch(100) } returns requests

            // 첫 번째 요청: 성공
            every { couponIssuanceService.checkIssuanceEligibility(1L, 101L) } just runs
            every { userService.getById(101L) } returns user
            every { couponService.getById(1L) } returns coupon
            every { couponService.save(any()) } just runs
            every { couponService.saveUserCoupon(any()) } just runs
            every { couponIssuanceService.recordIssuance(1L, 101L) } returns 99L
            every { couponIssuanceQueueService.markAsCompleted("req-success") } just runs

            // 두 번째 요청: 실패
            every { couponIssuanceService.checkIssuanceEligibility(1L, 102L) } throws CouponException.AlreadyIssuedCoupon()
            every { couponIssuanceQueueService.markAsFailed(requests[1], any()) } just runs

            // When
            val processedCount = processor.processBatch(100)

            // Then
            assertThat(processedCount).isEqualTo(1)
            verify { couponIssuanceQueueService.markAsCompleted("req-success") }
            verify { couponIssuanceQueueService.markAsFailed(requests[1], any()) }
        }
    }
}
