package io.hhplus.ecommerce.application.usecases

import io.hhplus.ecommerce.infrastructure.lock.DistributedLockService
import io.hhplus.ecommerce.domain.Coupon
import io.hhplus.ecommerce.domain.CouponType
import io.hhplus.ecommerce.domain.User
import io.hhplus.ecommerce.domain.UserCoupon
import io.hhplus.ecommerce.exception.CouponException
import io.hhplus.ecommerce.exception.UserException
import io.hhplus.ecommerce.infrastructure.repositories.CouponRepository
import io.hhplus.ecommerce.infrastructure.repositories.UserRepository
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
import java.util.concurrent.TimeUnit

@DisplayName("CouponUseCase 테스트")
class CouponUseCaseTest {

    private val couponRepository = mockk<CouponRepository>()
    private val userRepository = mockk<UserRepository>()
    private val distributedLockService = mockk<DistributedLockService>()
    private val useCase = CouponUseCase(couponRepository, userRepository, distributedLockService)

    @Nested
    @DisplayName("쿠폰 발급 테스트")
    inner class IssueCouponTest {
        private fun setupLockMock() {
            every { distributedLockService.tryLock(key = any(), waitTime = 3L, holdTime = 10L, unit = TimeUnit.SECONDS) } returns true
            every { distributedLockService.unlock(any()) } just runs
        }

        @Test
        fun `쿠폰을 발급할 수 있다`() {
            // Given
            setupLockMock()
            val user = User(id = 1L, balance = 100000L, createdAt = "2024-01-01")
            val coupon = Coupon(
                id = 1L, code = "COUPON-001", name = "테스트 쿠폰",
                type = CouponType.FIXED_AMOUNT, discount = 5000L, discountRate = 10,
                totalQuantity = 100, issuedQuantity = 0,
                startDate = LocalDateTime.now().minusDays(1),
                endDate = LocalDateTime.now().plusDays(7)
            )

            every { userRepository.findById(1L) } returns user
            every { couponRepository.findUserCouponByCouponId(1L, 1L) } returns null
            every { couponRepository.findById(1L) } returns coupon
            every { couponRepository.save(coupon) } just runs
            every { couponRepository.saveUserCoupon(any()) } just runs

            // When
            val result = useCase.issueCoupon(1L, 1L)

            // Then
            assertThat(result).isNotNull
            assertThat(result.couponName).isEqualTo("테스트 쿠폰")
            assertThat(result.discountRate).isEqualTo(10)
            verify { couponRepository.save(coupon) }
            verify { couponRepository.saveUserCoupon(any()) }
        }

        @Test
        fun `존재하지 않는 사용자는 예외를 발생시킨다`() {
            // Given
            setupLockMock()
            every { userRepository.findById(999L) } returns null

            // When/Then
            assertThatThrownBy {
                useCase.issueCoupon(1L, 999L)
            }.isInstanceOf(UserException.UserNotFound::class.java)
        }

        @Test
        fun `이미 발급받은 쿠폰은 예외를 발생시킨다`() {
            // Given
            setupLockMock()
            val user = User(id = 1L, balance = 100000L, createdAt = "2024-01-01")
            val userCoupon = UserCoupon(userId = 1L, couponId = 1L, couponName = "이미 발급됨", discountRate = 10)

            every { userRepository.findById(1L) } returns user
            every { couponRepository.findUserCouponByCouponId(1L, 1L) } returns userCoupon

            // When/Then
            assertThatThrownBy {
                useCase.issueCoupon(1L, 1L)
            }.isInstanceOf(CouponException.AlreadyIssuedCoupon::class.java)
        }

        @Test
        fun `존재하지 않는 쿠폰은 예외를 발생시킨다`() {
            // Given
            setupLockMock()
            val user = User(id = 1L, balance = 100000L, createdAt = "2024-01-01")

            every { userRepository.findById(1L) } returns user
            every { couponRepository.findUserCouponByCouponId(1L, 999L) } returns null
            every { couponRepository.findById(999L) } returns null

            // When/Then
            assertThatThrownBy {
                useCase.issueCoupon(999L, 1L)
            }.isInstanceOf(CouponException.CouponNotFound::class.java)
        }

        @Test
        fun `쿠폰이 모두 소진되면 예외를 발생시킨다`() {
            // Given
            setupLockMock()
            val user = User(id = 1L, balance = 100000L, createdAt = "2024-01-01")
            val coupon = Coupon(
                id = 1L, code = "COUPON-001", name = "소진된 쿠폰",
                type = CouponType.FIXED_AMOUNT, discount = 5000L, discountRate = 10,
                totalQuantity = 100, issuedQuantity = 100, // 모두 소진됨
                startDate = LocalDateTime.now().minusDays(1),
                endDate = LocalDateTime.now().plusDays(7)
            )

            every { userRepository.findById(1L) } returns user
            every { couponRepository.findUserCouponByCouponId(1L, 1L) } returns null
            every { couponRepository.findById(1L) } returns coupon

            // When/Then
            assertThatThrownBy {
                useCase.issueCoupon(1L, 1L)
            }.isInstanceOf(CouponException.CouponExhausted::class.java)
        }

        @Test
        fun `만료된 쿠폰은 발급할 수 없다`() {
            // Given
            setupLockMock()
            val user = User(id = 1L, balance = 100000L, createdAt = "2024-01-01")
            val coupon = Coupon(
                id = 1L, code = "COUPON-001", name = "만료된 쿠폰",
                type = CouponType.FIXED_AMOUNT, discount = 5000L, discountRate = 10,
                totalQuantity = 100, issuedQuantity = 0,
                startDate = LocalDateTime.now().minusDays(10),
                endDate = LocalDateTime.now().minusDays(1) // 이미 만료됨
            )

            every { userRepository.findById(1L) } returns user
            every { couponRepository.findUserCouponByCouponId(1L, 1L) } returns null
            every { couponRepository.findById(1L) } returns coupon

            // When/Then
            assertThatThrownBy {
                useCase.issueCoupon(1L, 1L)
            }.isInstanceOf(CouponException.CouponExhausted::class.java)
        }

        @Test
        fun `아직 시작하지 않은 쿠폰은 발급할 수 없다`() {
            // Given
            setupLockMock()
            val user = User(id = 1L, balance = 100000L, createdAt = "2024-01-01")
            val coupon = Coupon(
                id = 1L, code = "COUPON-001", name = "미시작 쿠폰",
                type = CouponType.FIXED_AMOUNT, discount = 5000L, discountRate = 10,
                totalQuantity = 100, issuedQuantity = 0,
                startDate = LocalDateTime.now().plusDays(1), // 아직 시작 안 됨
                endDate = LocalDateTime.now().plusDays(7)
            )

            every { userRepository.findById(1L) } returns user
            every { couponRepository.findUserCouponByCouponId(1L, 1L) } returns null
            every { couponRepository.findById(1L) } returns coupon

            // When/Then
            assertThatThrownBy {
                useCase.issueCoupon(1L, 1L)
            }.isInstanceOf(CouponException.CouponExhausted::class.java)
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
            every { couponRepository.findUserCoupons(1L) } returns userCoupons

            // When
            val result = useCase.getUserCoupons(1L)

            // Then
            assertThat(result).hasSize(2)
            assertThat(result).extracting<String> { it.couponName }.contains("쿠폰1", "쿠폰2")
        }

        @Test
        fun `보유 쿠폰이 없으면 빈 목록을 반환한다`() {
            // Given
            every { couponRepository.findUserCoupons(999L) } returns emptyList()

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
            every { couponRepository.findUserCoupons(1L) } returns userCoupons
            every { couponRepository.saveUserCoupon(any()) } just runs

            // When
            val result = useCase.getUserCoupons(1L)

            // Then
            assertThat(result).hasSize(2)
            assertThat(result[0].status).isEqualTo("EXPIRED")
            assertThat(result[1].status).isEqualTo("AVAILABLE")
            verify { couponRepository.saveUserCoupon(expiredCoupon) }
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
