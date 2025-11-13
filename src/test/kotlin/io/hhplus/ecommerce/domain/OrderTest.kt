package io.hhplus.ecommerce.domain

import io.hhplus.ecommerce.exception.OrderException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

@DisplayName("Order 도메인 모델 테스트")
class OrderTest {

    private fun createTestOrder(
        id: Long = 1L,
        userId: Long = 1L,
        items: List<OrderItem> = emptyList(),
        totalAmount: Long = 10000L,
        discountAmount: Long = 0L,
        finalAmount: Long = 10000L,
        status: String = "PENDING",
        couponId: Long? = null,
        paidAt: LocalDateTime? = null
    ): Order = Order(
        id = id,
        userId = userId,
        items = items,
        totalAmount = totalAmount,
        discountAmount = discountAmount,
        finalAmount = finalAmount,
        status = status,
        couponId = couponId,
        paidAt = paidAt
    )

    @Nested
    @DisplayName("canPay 테스트")
    inner class CanPayTest {
        @Test
        fun `상태가 PENDING이고 finalAmount가 0보다 크면 true`() {
            val order = createTestOrder(
                status = "PENDING",
                finalAmount = 10000L
            )

            assertThat(order.canPay()).isTrue
        }

        @Test
        fun `상태가 PENDING이지만 finalAmount가 0이면 false`() {
            val order = createTestOrder(
                status = "PENDING",
                finalAmount = 0L
            )

            assertThat(order.canPay()).isFalse
        }

        @Test
        fun `finalAmount가 0보다 크지만 상태가 PENDING이 아니면 false`() {
            val order = createTestOrder(
                status = "PAID",
                finalAmount = 10000L
            )

            assertThat(order.canPay()).isFalse
        }

        @Test
        fun `상태가 CANCELLED이면 false`() {
            val order = createTestOrder(
                status = "CANCELLED",
                finalAmount = 10000L
            )

            assertThat(order.canPay()).isFalse
        }
    }

    @Nested
    @DisplayName("complete 테스트")
    inner class CompleteTest {
        @Test
        fun `결제 완료 시 상태가 PAID로 변경`() {
            val order = createTestOrder(
                status = "PENDING",
                finalAmount = 10000L
            )

            order.complete()

            assertThat(order.status).isEqualTo("PAID")
        }

        @Test
        fun `결제 완료 시 paidAt이 설정된다`() {
            val order = createTestOrder(
                status = "PENDING",
                finalAmount = 10000L,
                paidAt = null
            )

            val beforeTime = LocalDateTime.now()
            order.complete()
            val afterTime = LocalDateTime.now()

            assertThat(order.paidAt).isNotNull
            assertThat(order.paidAt!! >= beforeTime && order.paidAt!! <= afterTime).isTrue
        }

        @Test
        fun `결제 불가능한 상태에서는 예외 발생`() {
            val order = createTestOrder(
                status = "PAID",
                finalAmount = 10000L
            )

            assertThrows<OrderException.CannotPayOrder> {
                order.complete()
            }
        }

        @Test
        fun `finalAmount가 0이면 예외 발생`() {
            val order = createTestOrder(
                status = "PENDING",
                finalAmount = 0L
            )

            assertThrows<OrderException.CannotPayOrder> {
                order.complete()
            }
        }
    }

    @Nested
    @DisplayName("canCancel 테스트")
    inner class CanCancelTest {
        @Test
        fun `상태가 PENDING이면 취소 가능`() {
            val order = createTestOrder(status = "PENDING")

            assertThat(order.canCancel()).isTrue
        }

        @Test
        fun `상태가 PENDING_PAYMENT이면 취소 가능`() {
            val order = createTestOrder(status = "PENDING_PAYMENT")

            assertThat(order.canCancel()).isTrue
        }

        @Test
        fun `상태가 PAID이면 취소 불가능`() {
            val order = createTestOrder(status = "PAID")

            assertThat(order.canCancel()).isFalse
        }

        @Test
        fun `상태가 CANCELLED이면 취소 불가능`() {
            val order = createTestOrder(status = "CANCELLED")

            assertThat(order.canCancel()).isFalse
        }

        @Test
        fun `상태가 SHIPPED이면 취소 불가능`() {
            val order = createTestOrder(status = "SHIPPED")

            assertThat(order.canCancel()).isFalse
        }
    }

    @Nested
    @DisplayName("cancel 테스트")
    inner class CancelTest {
        @Test
        fun `취소 가능한 상태에서는 상태가 CANCELLED로 변경`() {
            val order = createTestOrder(status = "PENDING")

            order.cancel()

            assertThat(order.status).isEqualTo("CANCELLED")
        }

        @Test
        fun `PENDING_PAYMENT 상태에서도 취소 가능`() {
            val order = createTestOrder(status = "PENDING_PAYMENT")

            order.cancel()

            assertThat(order.status).isEqualTo("CANCELLED")
        }

        @Test
        fun `이미 PAID 상태면 예외 발생`() {
            val order = createTestOrder(status = "PAID")

            assertThrows<OrderException.CannotCancelOrder> {
                order.cancel()
            }
        }

        @Test
        fun `이미 SHIPPED 상태면 예외 발생`() {
            val order = createTestOrder(status = "SHIPPED")

            val exception = assertThrows<OrderException.CannotCancelOrder> {
                order.cancel()
            }

            assertThat(exception.message).isEqualTo("취소할 수 없는 주문 상태입니다: SHIPPED")
        }

        @Test
        fun `이미 CANCELLED 상태면 예외 발생`() {
            val order = createTestOrder(status = "CANCELLED")

            assertThrows<OrderException.CannotCancelOrder> {
                order.cancel()
            }
        }
    }

    @Nested
    @DisplayName("OrderItem.create 팩토리 메서드 테스트")
    inner class OrderItemCreateTest {
        @Test
        fun `상품과 수량으로 주문 항목 생성`() {
            val product = Product(
                id = 1L,
                name = "테스트 상품",
                description = "설명",
                price = 10000L,
                category = "의류"
            )

            val orderItem = OrderItem.create(product, 3)

            assertThat(orderItem.productId).isEqualTo(1L)
            assertThat(orderItem.productName).isEqualTo("테스트 상품")
            assertThat(orderItem.quantity).isEqualTo(3)
            assertThat(orderItem.unitPrice).isEqualTo(10000L)
            assertThat(orderItem.subtotal).isEqualTo(30000L)
        }

        @Test
        fun `수량 1로 주문 항목 생성`() {
            val product = Product(
                id = 2L,
                name = "상품2",
                description = "설명",
                price = 5000L,
                category = "의류"
            )

            val orderItem = OrderItem.create(product, 1)

            assertThat(orderItem.quantity).isEqualTo(1)
            assertThat(orderItem.subtotal).isEqualTo(5000L)
        }

        @Test
        fun `대량 주문`() {
            val product = Product(
                id = 3L,
                name = "대량 상품",
                description = "설명",
                price = 1000L,
                category = "의류"
            )

            val orderItem = OrderItem.create(product, 100)

            assertThat(orderItem.quantity).isEqualTo(100)
            assertThat(orderItem.subtotal).isEqualTo(100000L)
        }
    }
}
