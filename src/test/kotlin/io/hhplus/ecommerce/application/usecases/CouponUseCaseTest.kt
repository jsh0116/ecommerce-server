package io.hhplus.ecommerce.application.usecases

import io.hhplus.ecommerce.application.services.CouponIssuanceService
import io.hhplus.ecommerce.application.services.CouponIssuanceQueueService
import io.hhplus.ecommerce.application.services.CouponService
import io.hhplus.ecommerce.application.services.UserService
import io.hhplus.ecommerce.domain.Coupon
import io.hhplus.ecommerce.domain.CouponType
import io.hhplus.ecommerce.domain.User
import io.hhplus.ecommerce.domain.UserCoupon
import io.hhplus.ecommerce.exception.CouponException
import io.hhplus.ecommerce.exception.UserException
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

@DisplayName("CouponUseCase 테스트 (비동기 방식)")
class CouponUseCaseTest {

    private val couponService = mockk<CouponService>()
    private val userService = mockk<UserService>()
    private val couponIssuanceService = mockk<CouponIssuanceService>(relaxed = true)
    private val couponIssuanceQueueService = mockk<CouponIssuanceQueueService>(relaxed = true)
    private val useCase = CouponUseCase(couponService, userService, couponIssuanceService, couponIssuanceQueueService)

    @Nested
    @DisplayName("쿠폰 발급 요청 테스트 (비동기)")
    inner class IssueCouponTest {

        @Test
        fun `쿠폰 발급 요청을 대기열에 추가할 수 있다`() {
            // Given
            val coupon = Coupon(
                id = 1L, code = "COUPON-001", name = "테스트 쿠폰",
                type = CouponType.FIXED_AMOUNT, discount = 5000L, discountRate = 10,
                totalQuantity = 100, issuedQuantity = 0,
                startDate = LocalDateTime.now().minusDays(1),
                endDate = LocalDateTime.now().plusDays(7)
            )
            val status = CouponIssuanceService.CouponStatus(
                couponId = 1L,
                totalQuantity = 100,
                issuedCount = 50,
                remainingQuantity = 50
            )

            every { couponIssuanceService.checkIssuanceEligibility(1L, 1L) } just runs
            every { couponService.getById(1L) } returns coupon
            every { couponIssuanceService.getCouponStatus(1L) } returns status
            every { couponIssuanceQueueService.enqueue(any()) } returns true

            // When
            val result = useCase.issueCoupon(1L, 1L)

            // Then
            assertThat(result).isNotNull
            assertThat(result.couponName).isEqualTo("테스트 쿠폰")
            assertThat(result.discountRate).isEqualTo(10)
            assertThat(result.status).isEqualTo("PENDING")
            assertThat(result.requestId).isNotNull
            assertThat(result.remainingQuantity).isEqualTo(50)
            verify { couponIssuanceQueueService.enqueue(any()) }
        }

        @Test
        fun `이미 발급받은 쿠폰은 예외를 발생시킨다`() {
            // Given
            every { couponIssuanceService.checkIssuanceEligibility(1L, 1L) } throws CouponException.AlreadyIssuedCoupon()

            // When/Then
            assertThatThrownBy {
                useCase.issueCoupon(1L, 1L)
            }.isInstanceOf(CouponException.AlreadyIssuedCoupon::class.java)
        }

        @Test
        fun `쿠폰이 모두 소진되면 예외를 발생시킨다`() {
            // Given
            every { couponIssuanceService.checkIssuanceEligibility(1L, 1L) } throws CouponException.CouponExhausted()

            // When/Then
            assertThatThrownBy {
                useCase.issueCoupon(1L, 1L)
            }.isInstanceOf(CouponException.CouponExhausted::class.java)
        }

        @Test
        fun `존재하지 않는 쿠폰은 예외를 발생시킨다`() {
            // Given
            every { couponIssuanceService.checkIssuanceEligibility(999L, 1L) } throws CouponException.CouponNotFound("999")

            // When/Then
            assertThatThrownBy {
                useCase.issueCoupon(999L, 1L)
            }.isInstanceOf(CouponException.CouponNotFound::class.java)
        }

        @Test
        fun `대기열 추가 실패 시 예외를 발생시킨다`() {
            // Given
            val coupon = Coupon(
                id = 1L, code = "COUPON-001", name = "테스트 쿠폰",
                type = CouponType.FIXED_AMOUNT, discount = 5000L, discountRate = 10,
                totalQuantity = 100, issuedQuantity = 0,
                startDate = LocalDateTime.now().minusDays(1),
                endDate = LocalDateTime.now().plusDays(7)
            )
            val status = CouponIssuanceService.CouponStatus(
                couponId = 1L, totalQuantity = 100, issuedCount = 50, remainingQuantity = 50
            )

            every { couponIssuanceService.checkIssuanceEligibility(1L, 1L) } just runs
            every { couponService.getById(1L) } returns coupon
            every { couponIssuanceService.getCouponStatus(1L) } returns status
            every { couponIssuanceQueueService.enqueue(any()) } returns false // 대기열 추가 실패

            // When/Then
            assertThatThrownBy {
                useCase.issueCoupon(1L, 1L)
            }.isInstanceOf(CouponException.CouponIssuanceFailed::class.java)
                .hasMessageContaining("대기열 추가 오류")
        }
    }

    @Nested
    @DisplayName("보유 쿠폰 조회 테스트")
    inner class GetUserCouponsTest {
        @Test
        fun `사용자의 보유 쿠폰을 조회할 수 있다`() {
            // Given
            val userCoupons = listOf(
                UserCoupon(userId = 1L, couponId = 1L, couponName = "쿠폰1", discountRate = 10),
                UserCoupon(userId = 1L, couponId = 2L, couponName = "쿠폰2", discountRate = 20)
            )
            every { couponService.findUserCoupons(1L) } returns userCoupons

            // When
            val result = useCase.getUserCoupons(1L)

            // Then
            assertThat(result).hasSize(2)
            assertThat(result).extracting<String> { it.couponName }.contains("쿠폰1", "쿠폰2")
        }

        @Test
        fun `보유 쿠폰이 없으면 빈 목록을 반환한다`() {
            // Given
            every { couponService.findUserCoupons(999L) } returns emptyList()

            // When
            val result = useCase.getUserCoupons(999L)

            // Then
            assertThat(result).isEmpty()
        }

        @Test
        fun `만료된 쿠폰의 상태를 업데이트한다`() {
            // Given
            val expiredCoupon = UserCoupon(
                userId = 1L, couponId = 1L, couponName = "만료된 쿠폰",
                discountRate = 10, status = "AVAILABLE",
                expiresAt = LocalDateTime.now().minusDays(1) // 만료됨
            )
            val validCoupon = UserCoupon(
                userId = 1L, couponId = 2L, couponName = "유효한 쿠폰",
                discountRate = 20, status = "AVAILABLE",
                expiresAt = LocalDateTime.now().plusDays(7)
            )
            val userCoupons = mutableListOf(expiredCoupon, validCoupon)
            every { couponService.findUserCoupons(1L) } returns userCoupons
            every { couponService.saveUserCoupon(any()) } just runs

            // When
            val result = useCase.getUserCoupons(1L)

            // Then
            assertThat(result).hasSize(2)
            assertThat(result[0].status).isEqualTo("EXPIRED")
            assertThat(result[1].status).isEqualTo("AVAILABLE")
            verify { couponService.saveUserCoupon(expiredCoupon) }
        }
    }

    @Nested
    @DisplayName("쿠폰 검증 테스트")
    inner class ValidateCouponTest {
        @Test
        fun `쿠폰 검증을 수행할 수 있다`() {
            // When
            val result = useCase.validateCoupon("COUPON-001", 50000L)

            // Then
            assertThat(result).isNotNull
            assertThat(result.valid).isFalse
        }
    }
}
