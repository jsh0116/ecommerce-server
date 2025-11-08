package io.hhplus.week2.application

import io.hhplus.week2.domain.Coupon
import io.hhplus.week2.domain.CouponType
import io.hhplus.week2.domain.UserCoupon
import io.hhplus.week2.repository.CouponRepository
import io.hhplus.week2.repository.UserRepository
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

@DisplayName("CouponUseCase 테스트")
class CouponUseCaseTest {

    private lateinit var couponUseCase: CouponUseCase
    private lateinit var couponRepository: CouponRepository
    private lateinit var userRepository: UserRepository

    @BeforeEach
    fun setUp() {
        couponRepository = mockk()
        userRepository = mockk()
        couponUseCase = CouponUseCase(couponRepository, userRepository)
    }

    @Test
    @DisplayName("쿠폰을 발급할 수 있다")
    fun testIssueCoupon() {
        // Given
        val couponId = "coupon1"
        val userId = "user1"
        val coupon = Coupon(
            id = couponId,
            code = "WELCOME10",
            name = "신규 가입 쿠폰",
            type = CouponType.PERCENTAGE,
            discount = 0L,
            discountRate = 10,
            minOrderAmount = 10_000L,
            totalQuantity = 100,
            issuedQuantity = 50,
            startDate = LocalDateTime.now().minusDays(1),
            endDate = LocalDateTime.now().plusDays(30)
        )

        every { userRepository.findById(userId) } returns mockk(relaxed = true)
        every { couponRepository.findUserCouponByCouponId(userId, couponId) } returns null
        every { couponRepository.findById(couponId) } returns coupon
        every { couponRepository.save(any()) } returnsArgument 0
        every { couponRepository.saveUserCoupon(any()) } just Runs

        // When
        val result = couponUseCase.issueCoupon(couponId, userId)

        // Then
        assert(result.userCouponId == couponId)
        assert(result.couponName == "신규 가입 쿠폰")
        assert(result.discountRate == 10)
        assert(result.remainingQuantity == 49) // 100 - 51
        verify { couponRepository.save(any()) }
        verify { couponRepository.saveUserCoupon(any()) }
    }

    @Test
    @DisplayName("이미 발급받은 쿠폰은 재발급할 수 없다")
    fun testIssueCouponAlreadyIssued() {
        // Given
        val couponId = "coupon1"
        val userId = "user1"
        val existingUserCoupon = UserCoupon(
            userId = userId,
            couponId = couponId,
            couponName = "신규 가입 쿠폰",
            discountRate = 10
        )

        every { userRepository.findById(userId) } returns mockk(relaxed = true)
        every { couponRepository.findUserCouponByCouponId(userId, couponId) } returns existingUserCoupon

        // When & Then
        val exception = assertThrows<IllegalStateException> {
            couponUseCase.issueCoupon(couponId, userId)
        }
        assert(exception.message?.contains("이미 발급받은 쿠폰입니다") ?: false)
    }

    @Test
    @DisplayName("쿠폰이 모두 소진되면 발급할 수 없다")
    fun testIssueCouponSoldOut() {
        // Given
        val couponId = "coupon1"
        val userId = "user1"
        val coupon = Coupon(
            id = couponId,
            code = "WELCOME10",
            name = "신규 가입 쿠폰",
            type = CouponType.PERCENTAGE,
            discount = 0L,
            discountRate = 10,
            minOrderAmount = 10_000L,
            totalQuantity = 100,
            issuedQuantity = 100, // 모두 소진
            startDate = LocalDateTime.now().minusDays(1),
            endDate = LocalDateTime.now().plusDays(30)
        )

        every { userRepository.findById(userId) } returns mockk(relaxed = true)
        every { couponRepository.findUserCouponByCouponId(userId, couponId) } returns null
        every { couponRepository.findById(couponId) } returns coupon

        // When & Then
        val exception = assertThrows<IllegalStateException> {
            couponUseCase.issueCoupon(couponId, userId)
        }
        assert(exception.message?.contains("쿠폰이 모두 소진되었습니다") ?: false)
    }

    @Test
    @DisplayName("존재하지 않는 사용자는 쿠폰을 발급받을 수 없다")
    fun testIssueCouponUserNotFound() {
        // Given
        val couponId = "coupon1"
        val userId = "nonexistent"

        every { userRepository.findById(userId) } returns null

        // When & Then
        val exception = assertThrows<IllegalStateException> {
            couponUseCase.issueCoupon(couponId, userId)
        }
        assert(exception.message?.contains("사용자를 찾을 수 없습니다") ?: false)
    }

    @Test
    @DisplayName("존재하지 않는 쿠폰은 발급할 수 없다")
    fun testIssueCouponNotFound() {
        // Given
        val couponId = "nonexistent"
        val userId = "user1"

        every { userRepository.findById(userId) } returns mockk(relaxed = true)
        every { couponRepository.findUserCouponByCouponId(userId, couponId) } returns null
        every { couponRepository.findById(couponId) } returns null

        // When & Then
        val exception = assertThrows<IllegalStateException> {
            couponUseCase.issueCoupon(couponId, userId)
        }
        assert(exception.message?.contains("쿠폰을 찾을 수 없습니다") ?: false)
    }

    @Test
    @DisplayName("발급 기간이 아닌 쿠폰은 발급할 수 없다")
    fun testIssueCouponOutOfPeriod() {
        // Given
        val couponId = "coupon1"
        val userId = "user1"
        val coupon = Coupon(
            id = couponId,
            code = "FUTURE",
            name = "미래 쿠폰",
            type = CouponType.PERCENTAGE,
            discount = 0L,
            discountRate = 10,
            minOrderAmount = 10_000L,
            totalQuantity = 100,
            issuedQuantity = 0,
            startDate = LocalDateTime.now().plusDays(1), // 아직 시작 전
            endDate = LocalDateTime.now().plusDays(30)
        )

        every { userRepository.findById(userId) } returns mockk(relaxed = true)
        every { couponRepository.findUserCouponByCouponId(userId, couponId) } returns null
        every { couponRepository.findById(couponId) } returns coupon

        // When & Then
        val exception = assertThrows<IllegalStateException> {
            couponUseCase.issueCoupon(couponId, userId)
        }
        assert(exception.message?.contains("쿠폰이 모두 소진되었습니다") ?: false)
    }

    @Test
    @DisplayName("보유 쿠폰을 조회할 수 있다")
    fun testGetUserCoupons() {
        // Given
        val userId = "user1"
        val userCoupons = listOf(
            UserCoupon(
                userId = userId,
                couponId = "coupon1",
                couponName = "쿠폰1",
                discountRate = 10,
                status = "AVAILABLE",
                expiresAt = LocalDateTime.now().plusDays(7)
            ),
            UserCoupon(
                userId = userId,
                couponId = "coupon2",
                couponName = "쿠폰2",
                discountRate = 20,
                status = "USED",
                expiresAt = LocalDateTime.now().plusDays(7)
            )
        )

        every { couponRepository.findUserCoupons(userId) } returns userCoupons

        // When
        val result = couponUseCase.getUserCoupons(userId)

        // Then
        assert(result.size == 2)
        assert(result[0].couponName == "쿠폰1")
        assert(result[1].status == "USED")
        verify { couponRepository.findUserCoupons(userId) }
    }

    @Test
    @DisplayName("만료된 쿠폰은 상태가 업데이트된다")
    fun testGetUserCouponsWithExpiredCoupon() {
        // Given
        val userId = "user1"
        val expiredCoupon = UserCoupon(
            userId = userId,
            couponId = "coupon1",
            couponName = "만료된 쿠폰",
            discountRate = 10,
            status = "AVAILABLE",
            expiresAt = LocalDateTime.now().minusDays(1) // 이미 만료
        )

        every { couponRepository.findUserCoupons(userId) } returns listOf(expiredCoupon)
        every { couponRepository.saveUserCoupon(any()) } just Runs

        // When
        val result = couponUseCase.getUserCoupons(userId)

        // Then
        assert(result.size == 1)
        assert(result[0].status == "EXPIRED")
        verify { couponRepository.saveUserCoupon(any()) }
    }

    @Test
    @DisplayName("쿠폰 검증을 호출할 수 있다 (미구현)")
    fun testValidateCoupon() {
        // Given
        val couponCode = "WELCOME10"
        val orderAmount = 50_000L

        // When
        val result = couponUseCase.validateCoupon(couponCode, orderAmount)

        // Then
        assert(result.valid == false)
        assert(result.message.contains("아직 구현되지 않았습니다"))
    }
}
