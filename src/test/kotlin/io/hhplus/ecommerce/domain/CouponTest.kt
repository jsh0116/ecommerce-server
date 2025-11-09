package io.hhplus.ecommerce.domain

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

@DisplayName("Coupon 도메인 테스트")
class CouponTest {

    @Test
    @DisplayName("쿠폰을 발급할 수 있다")
    fun testIssueCoupon() {
        // Given
        val now = LocalDateTime.now()
        val coupon = Coupon(
            id = "C001",
            code = "COUPON2024",
            name = "할인 쿠폰",
            type = CouponType.PERCENTAGE,
            discount = 0L,
            discountRate = 10,
            minOrderAmount = 10_000L,
            maxDiscountAmount = 50_000L,
            totalQuantity = 100,
            issuedQuantity = 0,
            startDate = now.minusDays(1),
            endDate = now.plusDays(30)
        )

        // When
        val remaining = coupon.issue()

        // Then
        assert(coupon.issuedQuantity == 1)
        assert(remaining == 99)
    }

    @Test
    @DisplayName("발급 수량 초과시 예외를 발생시킨다")
    fun testIssueCouponWhenExceedQuantity() {
        // Given
        val now = LocalDateTime.now()
        val coupon = Coupon(
            id = "C001",
            code = "COUPON2024",
            name = "할인 쿠폰",
            type = CouponType.PERCENTAGE,
            discount = 0L,
            discountRate = 10,
            minOrderAmount = 10_000L,
            maxDiscountAmount = 50_000L,
            totalQuantity = 1,
            issuedQuantity = 1,
            startDate = now.minusDays(1),
            endDate = now.plusDays(30)
        )

        // When & Then
        val exception = assertThrows<IllegalStateException> {
            coupon.issue()
        }
        assert(exception.message?.contains("쿠폰을 발급할 수 없습니다") ?: false)
    }

    @Test
    @DisplayName("유효 기간 지난 쿠폰은 발급할 수 없다")
    fun testIssueCouponWhenExpired() {
        // Given
        val now = LocalDateTime.now()
        val coupon = Coupon(
            id = "C001",
            code = "COUPON2024",
            name = "할인 쿠폰",
            type = CouponType.PERCENTAGE,
            discount = 0L,
            discountRate = 10,
            minOrderAmount = 10_000L,
            maxDiscountAmount = 50_000L,
            totalQuantity = 100,
            issuedQuantity = 0,
            startDate = now.minusDays(30),
            endDate = now.minusDays(1)
        )

        // When & Then
        val exception = assertThrows<IllegalStateException> {
            coupon.issue()
        }
        assert(exception.message?.contains("쿠폰을 발급할 수 없습니다") ?: false)
    }

    @Test
    @DisplayName("쿠폰 발급 가능 여부를 확인할 수 있다")
    fun testCanIssueCoupon() {
        // Given
        val now = LocalDateTime.now()
        val coupon = Coupon(
            id = "C001",
            code = "COUPON2024",
            name = "할인 쿠폰",
            type = CouponType.PERCENTAGE,
            discount = 0L,
            discountRate = 10,
            minOrderAmount = 10_000L,
            maxDiscountAmount = 50_000L,
            totalQuantity = 100,
            issuedQuantity = 0,
            startDate = now.minusDays(1),
            endDate = now.plusDays(30)
        )

        // When & Then
        assert(coupon.canIssue())
    }

    @Test
    @DisplayName("사용자 쿠폰을 생성할 수 있다")
    fun testCreateUserCoupon() {
        // Given
        val now = LocalDateTime.now()
        val coupon = Coupon(
            id = "C001",
            code = "COUPON2024",
            name = "할인 쿠폰",
            type = CouponType.PERCENTAGE,
            discount = 0L,
            discountRate = 10,
            minOrderAmount = 10_000L,
            maxDiscountAmount = 50_000L,
            totalQuantity = 100,
            issuedQuantity = 0,
            startDate = now.minusDays(1),
            endDate = now.plusDays(30)
        )

        // When
        val userCoupon = UserCoupon(
            userId = "USER1",
            couponId = coupon.id,
            couponName = coupon.name,
            discountRate = coupon.discountRate
        )

        // Then
        assert(userCoupon.userId == "USER1")
        assert(userCoupon.couponId == "C001")
        assert(userCoupon.couponName == "할인 쿠폰")
        assert(userCoupon.discountRate == 10)
        assert(userCoupon.status == "AVAILABLE")
    }

    @Test
    @DisplayName("사용자 쿠폰 유효성을 확인할 수 있다")
    fun testIsValidUserCoupon() {
        // Given
        val userCoupon = UserCoupon(
            userId = "USER1",
            couponId = "C001",
            couponName = "할인 쿠폰",
            discountRate = 10
        )

        // When & Then
        assert(userCoupon.isValid())
    }

    @Test
    @DisplayName("사용된 쿠폰은 유효하지 않다")
    fun testUsedUserCouponIsNotValid() {
        // Given
        val userCoupon = UserCoupon(
            userId = "USER1",
            couponId = "C001",
            couponName = "할인 쿠폰",
            discountRate = 10,
            status = "USED"
        )

        // When & Then
        assert(!userCoupon.isValid())
    }

    @Test
    @DisplayName("사용자 쿠폰을 사용할 수 있다")
    fun testUseUserCoupon() {
        // Given
        val userCoupon = UserCoupon(
            userId = "USER1",
            couponId = "C001",
            couponName = "할인 쿠폰",
            discountRate = 10
        )

        // When
        userCoupon.use()

        // Then
        assert(userCoupon.status == "USED")
        assert(userCoupon.usedAt != null)
    }

    @Test
    @DisplayName("유효하지 않은 쿠폰은 사용할 수 없다")
    fun testUseInvalidUserCoupon() {
        // Given
        val userCoupon = UserCoupon(
            userId = "USER1",
            couponId = "C001",
            couponName = "할인 쿠폰",
            discountRate = 10,
            status = "USED"
        )

        // When & Then
        val exception = assertThrows<IllegalStateException> {
            userCoupon.use()
        }
        assert(exception.message?.contains("사용할 수 없는 쿠폰") ?: false)
    }

    @Test
    @DisplayName("사용자 쿠폰을 만료시킬 수 있다")
    fun testExpireUserCoupon() {
        // Given
        val userCoupon = UserCoupon(
            userId = "USER1",
            couponId = "C001",
            couponName = "할인 쿠폰",
            discountRate = 10
        )

        // When
        userCoupon.expire()

        // Then
        assert(userCoupon.status == "EXPIRED")
    }
}
