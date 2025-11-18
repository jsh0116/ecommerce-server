package io.hhplus.ecommerce.domain

import io.hhplus.ecommerce.exception.CouponException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

@DisplayName("Coupon 도메인 모델 테스트")
class CouponTest {

    @Nested
    @DisplayName("canIssue 테스트")
    inner class CanIssueTest {
        @Test
        fun `발급 가능한 쿠폰은 true 반환`() {
            val coupon = Coupon(
                id = 1L, code = "TEST", name = "테스트",
                discountRate = 10, totalQuantity = 100, issuedQuantity = 50,
                startDate = LocalDateTime.now().minusHours(1),
                endDate = LocalDateTime.now().plusHours(1)
            )
            assertThat(coupon.canIssue()).isTrue
        }

        @Test
        fun `발급 수량 초과 시 false`() {
            val coupon = Coupon(
                id = 1L, code = "TEST", name = "테스트",
                discountRate = 10, totalQuantity = 100, issuedQuantity = 100,
                startDate = LocalDateTime.now().minusHours(1),
                endDate = LocalDateTime.now().plusHours(1)
            )
            assertThat(coupon.canIssue()).isFalse
        }

        @Test
        fun `시작일 이전이면 발급 불가`() {
            val coupon = Coupon(
                id = 1L, code = "TEST", name = "테스트",
                discountRate = 10, totalQuantity = 100, issuedQuantity = 50,
                startDate = LocalDateTime.now().plusDays(1),
                endDate = LocalDateTime.now().plusDays(8)
            )
            assertThat(coupon.canIssue()).isFalse
        }

        @Test
        fun `종료일 이후이면 발급 불가`() {
            val coupon = Coupon(
                id = 1L, code = "TEST", name = "테스트",
                discountRate = 10, totalQuantity = 100, issuedQuantity = 50,
                startDate = LocalDateTime.now().minusDays(8),
                endDate = LocalDateTime.now().minusDays(1)
            )
            assertThat(coupon.canIssue()).isFalse
        }
    }

    @Nested
    @DisplayName("issue 테스트")
    inner class IssueTest {
        @Test
        fun `쿠폰 발급 시 issuedQuantity 증가`() {
            val coupon = Coupon(
                id = 1L, code = "TEST", name = "테스트",
                discountRate = 10, totalQuantity = 100, issuedQuantity = 0,
                startDate = LocalDateTime.now().minusHours(1),
                endDate = LocalDateTime.now().plusHours(1)
            )
            coupon.issue()
            assertThat(coupon.issuedQuantity).isEqualTo(1)
        }

        @Test
        fun `남은 쿠폰 수량 반환`() {
            val coupon = Coupon(
                id = 1L, code = "TEST", name = "테스트",
                discountRate = 10, totalQuantity = 100, issuedQuantity = 30,
                startDate = LocalDateTime.now().minusHours(1),
                endDate = LocalDateTime.now().plusHours(1)
            )
            val remaining = coupon.issue()
            assertThat(remaining).isEqualTo(69)
        }

        @Test
        fun `발급 불가능한 상태에서는 예외 발생`() {
            val coupon = Coupon(
                id = 1L, code = "TEST", name = "테스트",
                discountRate = 10, totalQuantity = 100, issuedQuantity = 100,
                startDate = LocalDateTime.now().minusHours(1),
                endDate = LocalDateTime.now().plusHours(1)
            )
            assertThrows<CouponException.CannotIssueCoupon> { coupon.issue() }
        }
    }
}

@DisplayName("UserCoupon 도메인 모델 테스트")
class UserCouponTest {

    @Nested
    @DisplayName("isValid 테스트")
    inner class IsValidTest {
        @Test
        fun `상태가 AVAILABLE이고 유효기간 내이면 true`() {
            val userCoupon = UserCoupon(
                userId = 1L, couponId = 1L, couponName = "테스트",
                discountRate = 10, status = "AVAILABLE",
                expiresAt = LocalDateTime.now().plusDays(1)
            )
            assertThat(userCoupon.isValid()).isTrue
        }

        @Test
        fun `상태가 USED이면 false`() {
            val userCoupon = UserCoupon(
                userId = 1L, couponId = 1L, couponName = "테스트",
                discountRate = 10, status = "USED"
            )
            assertThat(userCoupon.isValid()).isFalse
        }

        @Test
        fun `상태가 EXPIRED이면 false`() {
            val userCoupon = UserCoupon(
                userId = 1L, couponId = 1L, couponName = "테스트",
                discountRate = 10, status = "EXPIRED"
            )
            assertThat(userCoupon.isValid()).isFalse
        }

        @Test
        fun `유효기간 만료되면 false`() {
            val userCoupon = UserCoupon(
                userId = 1L, couponId = 1L, couponName = "테스트",
                discountRate = 10, status = "AVAILABLE",
                expiresAt = LocalDateTime.now().minusDays(1)
            )
            assertThat(userCoupon.isValid()).isFalse
        }
    }

    @Nested
    @DisplayName("use 테스트")
    inner class UseTest {
        @Test
        fun `쿠폰 사용 시 상태가 USED로 변경`() {
            val userCoupon = UserCoupon(
                userId = 1L, couponId = 1L, couponName = "테스트",
                discountRate = 10, status = "AVAILABLE",
                expiresAt = LocalDateTime.now().plusDays(1)
            )
            userCoupon.use()
            assertThat(userCoupon.status).isEqualTo("USED")
        }

        @Test
        fun `쿠폰 사용 시 usedAt이 설정된다`() {
            val userCoupon = UserCoupon(
                userId = 1L, couponId = 1L, couponName = "테스트",
                discountRate = 10, status = "AVAILABLE",
                usedAt = null,
                expiresAt = LocalDateTime.now().plusDays(1)
            )
            userCoupon.use()
            assertThat(userCoupon.usedAt).isNotNull
        }

        @Test
        fun `유효하지 않은 쿠폰 사용 시 예외 발생`() {
            val userCoupon = UserCoupon(
                userId = 1L, couponId = 1L, couponName = "테스트",
                discountRate = 10, status = "USED"
            )
            assertThrows<CouponException.CannotUseCoupon> { userCoupon.use() }
        }

        @Test
        fun `만료된 쿠폰 사용 시 예외 발생`() {
            val userCoupon = UserCoupon(
                userId = 1L, couponId = 1L, couponName = "테스트",
                discountRate = 10, status = "AVAILABLE",
                expiresAt = LocalDateTime.now().minusDays(1)
            )
            assertThrows<CouponException.CannotUseCoupon> { userCoupon.use() }
        }
    }

    @Nested
    @DisplayName("expire 테스트")
    inner class ExpireTest {
        @Test
        fun `쿠폰 만료 시 상태가 EXPIRED로 변경`() {
            val userCoupon = UserCoupon(
                userId = 1L, couponId = 1L, couponName = "테스트",
                discountRate = 10, status = "AVAILABLE"
            )
            userCoupon.expire()
            assertThat(userCoupon.status).isEqualTo("EXPIRED")
        }

        @Test
        fun `이미 USED인 쿠폰도 EXPIRED로 변경 가능`() {
            val userCoupon = UserCoupon(
                userId = 1L, couponId = 1L, couponName = "테스트",
                discountRate = 10, status = "USED"
            )
            userCoupon.expire()
            assertThat(userCoupon.status).isEqualTo("EXPIRED")
        }
    }
}
