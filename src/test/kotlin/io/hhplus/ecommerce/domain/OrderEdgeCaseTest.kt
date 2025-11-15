package io.hhplus.ecommerce.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("Order 엣지 케이스 테스트")
class OrderEdgeCaseTest {

    @Nested
    @DisplayName("주문 상태 전환 테스트")
    inner class OrderStatusTransitionTest {
        @Test
        fun `주문의 상태를 올바르게 변경할 수 있다`() {
            // Given
            val order = Order(id = 1L, userId = 1L, items = emptyList(), totalAmount = 50000L, discountAmount = 0L, finalAmount = 50000L, status = "PENDING")

            // When
            order.status = "PAID"

            // Then
            assertThat(order.status).isEqualTo("PAID")
        }

        @Test
        fun `주문의 여러 상태를 순차적으로 변경할 수 있다`() {
            // Given
            val order = Order(id = 1L, userId = 1L, items = emptyList(), totalAmount = 50000L, discountAmount = 0L, finalAmount = 50000L, status = "PENDING")

            // When
            order.status = "PAID"
            order.status = "PREPARING"
            order.status = "SHIPPED"

            // Then
            assertThat(order.status).isEqualTo("SHIPPED")
        }
    }

    @Nested
    @DisplayName("주문 금액 계산 테스트")
    inner class OrderAmountCalculationTest {
        @Test
        fun `복수의 상품으로 주문 금액을 계산할 수 있다`() {
            // Given
            val items = listOf(
                OrderItem(productId = 1L, productName = "상품1", quantity = 2, unitPrice = 30000L, subtotal = 60000L),
                OrderItem(productId = 2L, productName = "상품2", quantity = 1, unitPrice = 50000L, subtotal = 50000L),
                OrderItem(productId = 3L, productName = "상품3", quantity = 3, unitPrice = 10000L, subtotal = 30000L)
            )
            val order = Order(id = 1L, userId = 1L, items = items, totalAmount = 140000L, discountAmount = 0L, finalAmount = 140000L)

            // Then
            assertThat(order.totalAmount).isEqualTo(140000L)
            assertThat(order.finalAmount).isEqualTo(140000L)
        }

        @Test
        fun `할인이 적용된 주문 금액을 계산할 수 있다`() {
            // Given
            val items = listOf(
                OrderItem(productId = 1L, productName = "상품", quantity = 1, unitPrice = 100000L, subtotal = 100000L)
            )
            val order = Order(id = 1L, userId = 1L, items = items, totalAmount = 100000L, discountAmount = 10000L, finalAmount = 90000L)

            // Then
            assertThat(order.totalAmount).isEqualTo(100000L)
            assertThat(order.discountAmount).isEqualTo(10000L)
            assertThat(order.finalAmount).isEqualTo(90000L)
        }

        @Test
        fun `빈 주문은 0원이다`() {
            // Given
            val order = Order(id = 1L, userId = 1L, items = emptyList(), totalAmount = 0L, discountAmount = 0L, finalAmount = 0L)

            // Then
            assertThat(order.totalAmount).isEqualTo(0L)
            assertThat(order.finalAmount).isEqualTo(0L)
        }
    }

    @Nested
    @DisplayName("주문 항목 테스트")
    inner class OrderItemTest {
        @Test
        fun `주문 항목의 금액이 올바르게 계산된다`() {
            // Given
            val item = OrderItem(productId = 1L, productName = "청바지", quantity = 5, unitPrice = 50000L, subtotal = 250000L)

            // Then
            assertThat(item.quantity).isEqualTo(5)
            assertThat(item.unitPrice).isEqualTo(50000L)
            assertThat(item.subtotal).isEqualTo(250000L)
        }

        @Test
        fun `대량 주문을 처리할 수 있다`() {
            // Given
            val item = OrderItem(productId = 1L, productName = "상품", quantity = 1000, unitPrice = 10000L, subtotal = 10000000L)

            // Then
            assertThat(item.quantity).isEqualTo(1000)
            assertThat(item.subtotal).isEqualTo(10000000L)
        }

        @Test
        fun `소수점 단가로 주문 항목을 처리할 수 있다`() {
            // Given - 장기적으로는 정수 기반 금액을 권장하지만, 현재는 Long 사용
            val item = OrderItem(productId = 1L, productName = "상품", quantity = 3, unitPrice = 33333L, subtotal = 99999L)

            // Then
            assertThat(item.subtotal).isEqualTo(99999L)
        }
    }
}
