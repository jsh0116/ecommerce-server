package io.hhplus.ecommerce.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

@DisplayName("Coupon 엣지 케이스 테스트")
class CouponEdgeCaseTest {

    @Nested
    @DisplayName("쿠폰 할인 테스트")
    inner class CouponDiscountTest {
        @Test
        fun `정액 할인 쿠폰이 올바르게 설정된다`() {
            // Given
            val coupon = Coupon(
                id = 1L, code = "DISCOUNT-001", name = "5000원 할인",
                type = CouponType.FIXED_AMOUNT, discount = 5000L, discountRate = 0,
                totalQuantity = 100, issuedQuantity = 0,
                startDate = LocalDateTime.now(), endDate = LocalDateTime.now().plusDays(7)
            )

            // Then
            assertThat(coupon.type).isEqualTo(CouponType.FIXED_AMOUNT)
            assertThat(coupon.discount).isEqualTo(5000L)
        }

        @Test
        fun `비율 할인 쿠폰이 올바르게 설정된다`() {
            // Given
            val coupon = Coupon(
                id = 1L, code = "DISCOUNT-002", name = "10% 할인",
                type = CouponType.PERCENTAGE, discount = 0L, discountRate = 10,
                totalQuantity = 100, issuedQuantity = 0,
                startDate = LocalDateTime.now(), endDate = LocalDateTime.now().plusDays(7)
            )

            // Then
            assertThat(coupon.type).isEqualTo(CouponType.PERCENTAGE)
            assertThat(coupon.discountRate).isEqualTo(10)
        }

        @Test
        fun `큰 할인율을 설정할 수 있다`() {
            // Given
            val coupon = Coupon(
                id = 1L, code = "DISCOUNT-003", name = "50% 할인",
                type = CouponType.PERCENTAGE, discount = 0L, discountRate = 50,
                totalQuantity = 100, issuedQuantity = 0,
                startDate = LocalDateTime.now(), endDate = LocalDateTime.now().plusDays(7)
            )

            // Then
            assertThat(coupon.discountRate).isEqualTo(50)
        }
    }

    @Nested
    @DisplayName("쿠폰 수량 관리 테스트")
    inner class CouponQuantityTest {
        @Test
        fun `쿠폰의 초기 수량이 올바르게 설정된다`() {
            // Given
            val coupon = Coupon(
                id = 1L, code = "COUPON-001", name = "테스트 쿠폰",
                type = CouponType.FIXED_AMOUNT, discount = 5000L, discountRate = 0,
                totalQuantity = 1000, issuedQuantity = 0,
                startDate = LocalDateTime.now(), endDate = LocalDateTime.now().plusDays(7)
            )

            // Then
            assertThat(coupon.totalQuantity).isEqualTo(1000)
            assertThat(coupon.issuedQuantity).isEqualTo(0)
        }

        @Test
        fun `쿠폰의 발행 수량을 증가시킬 수 있다`() {
            // Given
            val coupon = Coupon(
                id = 1L, code = "COUPON-001", name = "테스트 쿠폰",
                type = CouponType.FIXED_AMOUNT, discount = 5000L, discountRate = 0,
                totalQuantity = 100, issuedQuantity = 0,
                startDate = LocalDateTime.now(), endDate = LocalDateTime.now().plusDays(7)
            )

            // When
            coupon.issuedQuantity = 50

            // Then
            assertThat(coupon.issuedQuantity).isEqualTo(50)
        }

        @Test
        fun `많은 수량의 쿠폰을 관리할 수 있다`() {
            // Given
            val coupon = Coupon(
                id = 1L, code = "COUPON-001", name = "대량 쿠폰",
                type = CouponType.FIXED_AMOUNT, discount = 5000L, discountRate = 0,
                totalQuantity = 1000000, issuedQuantity = 999999,
                startDate = LocalDateTime.now(), endDate = LocalDateTime.now().plusDays(7)
            )

            // Then
            assertThat(coupon.totalQuantity).isEqualTo(1000000)
            assertThat(coupon.issuedQuantity).isEqualTo(999999)
        }
    }

    @Nested
    @DisplayName("쿠폰 유효 기간 테스트")
    inner class CouponValidityPeriodTest {
        @Test
        fun `쿠폰의 유효 기간을 올바르게 설정할 수 있다`() {
            // Given
            val now = LocalDateTime.now()
            val coupon = Coupon(
                id = 1L, code = "COUPON-001", name = "테스트 쿠폰",
                type = CouponType.FIXED_AMOUNT, discount = 5000L, discountRate = 0,
                totalQuantity = 100, issuedQuantity = 0,
                startDate = now, endDate = now.plusDays(30)
            )

            // Then
            assertThat(coupon.startDate).isEqualTo(now)
            assertThat(coupon.endDate).isEqualTo(now.plusDays(30))
        }

        @Test
        fun `장기 유효 기간의 쿠폰을 생성할 수 있다`() {
            // Given
            val now = LocalDateTime.now()
            val coupon = Coupon(
                id = 1L, code = "COUPON-001", name = "장기 쿠폰",
                type = CouponType.FIXED_AMOUNT, discount = 5000L, discountRate = 0,
                totalQuantity = 100, issuedQuantity = 0,
                startDate = now, endDate = now.plusYears(1)
            )

            // Then
            assertThat(coupon.endDate).isAfter(now.plusMonths(11))
        }
    }

    @Nested
    @DisplayName("쿠폰 코드 및 이름 테스트")
    inner class CouponCodeAndNameTest {
        @Test
        fun `쿠폰 코드와 이름이 올바르게 설정된다`() {
            // Given
            val coupon = Coupon(
                id = 1L, code = "SUMMER-2024-DISCOUNT", name = "여름 시즌 특가 쿠폰",
                type = CouponType.FIXED_AMOUNT, discount = 5000L, discountRate = 0,
                totalQuantity = 100, issuedQuantity = 0,
                startDate = LocalDateTime.now(), endDate = LocalDateTime.now().plusDays(7)
            )

            // Then
            assertThat(coupon.code).isEqualTo("SUMMER-2024-DISCOUNT")
            assertThat(coupon.name).isEqualTo("여름 시즌 특가 쿠폰")
        }

        @Test
        fun `특수 문자가 포함된 쿠폰 이름을 생성할 수 있다`() {
            // Given
            val coupon = Coupon(
                id = 1L, code = "SPECIAL", name = "특가!! 50% OFF (한정판)",
                type = CouponType.PERCENTAGE, discount = 0L, discountRate = 50,
                totalQuantity = 100, issuedQuantity = 0,
                startDate = LocalDateTime.now(), endDate = LocalDateTime.now().plusDays(7)
            )

            // Then
            assertThat(coupon.name).contains("특가")
            assertThat(coupon.name).contains("50%")
        }
    }
}
