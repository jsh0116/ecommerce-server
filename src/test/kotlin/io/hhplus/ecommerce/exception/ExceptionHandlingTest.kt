package io.hhplus.ecommerce.exception

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("예외 처리 테스트")
class ExceptionHandlingTest {

    @Nested
    @DisplayName("OrderException 테스트")
    inner class OrderExceptionTest {
        @Test
        fun `CannotPayOrder 예외가 올바른 메시지를 반환한다`() {
            // When
            val exception = OrderException.CannotPayOrder()

            // Then
            assertThat(exception.message).isEqualTo("결제할 수 없는 주문입니다")
            assertThat(exception.errorCode).isEqualTo("CANNOT_PAY_ORDER")
        }

        @Test
        fun `CannotCancelOrder 예외가 상태 정보를 포함한다`() {
            // When
            val exception = OrderException.CannotCancelOrder("SHIPPED")

            // Then
            assertThat(exception.message).contains("SHIPPED")
            assertThat(exception.errorCode).isEqualTo("CANNOT_CANCEL_ORDER")
        }

        @Test
        fun `UnauthorizedOrderAccess 예외가 올바른 에러 코드를 반환한다`() {
            // When
            val exception = OrderException.UnauthorizedOrderAccess()

            // Then
            assertThat(exception.errorCode).isEqualTo("UNAUTHORIZED_ORDER_ACCESS")
        }
    }

    @Nested
    @DisplayName("CouponException 테스트")
    inner class CouponExceptionTest {
        @Test
        fun `CouponNotFound 예외가 쿠폰 ID를 포함한다`() {
            // When
            val exception = CouponException.CouponNotFound("COUPON-001")

            // Then
            assertThat(exception.errorCode).isEqualTo("COUPON_NOT_FOUND")
            assertThat(exception.message).contains("COUPON-001")
        }

        @Test
        fun `AlreadyIssuedCoupon 예외가 메시지를 반환한다`() {
            // When
            val exception = CouponException.AlreadyIssuedCoupon()

            // Then
            assertThat(exception.errorCode).isEqualTo("ALREADY_ISSUED_COUPON")
        }

        @Test
        fun `CouponExhausted 예외가 메시지를 반환한다`() {
            // When
            val exception = CouponException.CouponExhausted()

            // Then
            assertThat(exception.errorCode).isEqualTo("COUPON_EXHAUSTED")
        }
    }

    @Nested
    @DisplayName("InventoryException 테스트")
    inner class InventoryExceptionTest {
        @Test
        fun `InsufficientStock 예외가 상품 정보를 포함한다`() {
            // When
            val exception = InventoryException.InsufficientStock("청바지", 10, 20)

            // Then
            assertThat(exception.errorCode).isEqualTo("INSUFFICIENT_STOCK")
            assertThat(exception.message).contains("청바지")
            assertThat(exception.message).contains("10")
            assertThat(exception.message).contains("20")
        }

        @Test
        fun `InventoryNotFound 예외가 SKU를 포함한다`() {
            // When
            val exception = InventoryException.InventoryNotFound("SKU-001")

            // Then
            assertThat(exception.errorCode).isEqualTo("INVENTORY_NOT_FOUND")
            assertThat(exception.message).contains("SKU-001")
        }

        @Test
        fun `CannotReserveStock 예외가 상품 정보를 포함한다`() {
            // When
            val exception = InventoryException.CannotReserveStock("셔츠")

            // Then
            assertThat(exception.errorCode).isEqualTo("CANNOT_RESERVE_STOCK")
            assertThat(exception.message).contains("셔츠")
        }
    }

    @Nested
    @DisplayName("BusinessRuleViolationException 테스트")
    inner class BusinessRuleViolationExceptionTest {
        @Test
        fun `예외가 올바른 상태를 유지한다`() {
            // When
            val exception = BusinessRuleViolationException(
                errorCode = "TEST_ERROR",
                message = "테스트 에러"
            )

            // Then
            assertThat(exception.errorCode).isEqualTo("TEST_ERROR")
            assertThat(exception.message).isEqualTo("테스트 에러")
        }
    }
}
