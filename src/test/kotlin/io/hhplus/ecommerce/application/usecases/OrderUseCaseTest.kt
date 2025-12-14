package io.hhplus.ecommerce.application.usecases

import io.hhplus.ecommerce.application.services.OrderService
import io.hhplus.ecommerce.application.services.OrderCreationService
import io.hhplus.ecommerce.application.services.PaymentProcessingService
import io.hhplus.ecommerce.domain.Order
import io.hhplus.ecommerce.domain.OrderItem
import io.hhplus.ecommerce.domain.Product
import io.hhplus.ecommerce.domain.User
import io.hhplus.ecommerce.domain.UserCoupon
import io.hhplus.ecommerce.exception.InventoryException
import io.hhplus.ecommerce.exception.OrderException
import io.hhplus.ecommerce.exception.ProductException
import io.hhplus.ecommerce.exception.UserException
import io.hhplus.ecommerce.infrastructure.persistence.entity.InventoryJpaEntity
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDateTime

@DisplayName("OrderUseCase 테스트")
class OrderUseCaseTest {

    private val orderService = mockk<OrderService>(relaxed = true)
    private val orderCreationService = mockk<OrderCreationService>(relaxed = true)
    private val paymentProcessingService = mockk<PaymentProcessingService>(relaxed = true)

    private val useCase = OrderUseCase(
        orderService,
        orderCreationService,
        paymentProcessingService
    )

    @Nested
    @DisplayName("주문 생성 테스트")
    inner class CreateOrderTest {
        @Test
        fun `주문을 생성할 수 있다`() {
            // Given
            val orderItems = listOf(OrderItem(productId = 1L, productName = "상품1", quantity = 1, unitPrice = 50000L, subtotal = 50000L))
            val order = Order(id = 1L, userId = 1L, items = orderItems, totalAmount = 50000L, discountAmount = 0L, finalAmount = 50000L)

            every { orderCreationService.createOrder(1L, any(), null) } returns order

            // When
            val result = useCase.createOrder(1L, listOf(OrderUseCase.OrderItemRequest(1L, 1)), null)

            // Then
            assertThat(result).isNotNull
            assertThat(result.userId).isEqualTo(1L)
            verify { orderCreationService.createOrder(1L, any(), null) }
        }

        @Test
        fun `존재하지 않는 사용자는 예외를 발생시킨다`() {
            // Given
            every { orderCreationService.createOrder(999L, any(), null) } throws UserException.UserNotFound("999")

            // When/Then
            assertThatThrownBy {
                useCase.createOrder(999L, listOf(OrderUseCase.OrderItemRequest(1L, 1)), null)
            }.isInstanceOf(UserException.UserNotFound::class.java)
        }

        @Test
        fun `존재하지 않는 상품은 예외를 발생시킨다`() {
            // Given
            every { orderCreationService.createOrder(1L, any(), null) } throws ProductException.ProductNotFound("999")

            // When/Then
            assertThatThrownBy {
                useCase.createOrder(1L, listOf(OrderUseCase.OrderItemRequest(999L, 1)), null)
            }.isInstanceOf(ProductException.ProductNotFound::class.java)
        }

        @Test
        fun `재고가 부족하면 예외를 발생시킨다`() {
            // Given
            every { orderCreationService.createOrder(1L, any(), null) } throws InventoryException.InsufficientStock(
                productName = "상품1",
                available = 5,
                required = 10
            )

            // When/Then
            assertThatThrownBy {
                useCase.createOrder(1L, listOf(OrderUseCase.OrderItemRequest(1L, 10)), null)
            }.isInstanceOf(InventoryException.InsufficientStock::class.java)
        }
    }

    @Nested
    @DisplayName("주문 조회 테스트")
    inner class GetOrderTest {
        @Test
        fun `주문을 조회할 수 있다`() {
            // Given
            val order = Order(id = 1L, userId = 1L, items = emptyList(), totalAmount = 50000L, discountAmount = 0L, finalAmount = 50000L)
            every { orderService.getById(1L) } returns order

            // When
            val result = useCase.getOrderById(1L)

            // Then
            assertThat(result).isNotNull
            assertThat(result?.id).isEqualTo(1L)
        }

        @Test
        fun `존재하지 않는 주문은 null을 반환한다`() {
            // Given
            every { orderService.getById(999L) } throws OrderException.OrderNotFound("999")

            // When
            val result = useCase.getOrderById(999L)

            // Then
            assertThat(result).isNull()
        }
    }

    @Nested
    @DisplayName("주문 취소 테스트")
    inner class CancelOrderTest {
        @Test
        fun `주문을 취소하고 재고를 복구할 수 있다`() {
            // Given
            val orderItems = listOf(OrderItem(productId = 1L, productName = "상품1", quantity = 2, unitPrice = 50000L, subtotal = 100000L))
            val order = Order(id = 1L, userId = 1L, items = orderItems, totalAmount = 100000L, discountAmount = 0L, finalAmount = 100000L, status = "CANCELLED")

            every { orderCreationService.cancelOrder(1L, 1L) } returns order

            // When
            val result = useCase.cancelOrder(1L, 1L)

            // Then
            assertThat(result).isNotNull
            assertThat(result.status).isEqualTo("CANCELLED")
            verify { orderCreationService.cancelOrder(1L, 1L) }
        }
    }

    @Nested
    @DisplayName("결제 처리 테스트")
    inner class ProcessPaymentTest {
        @Test
        fun `결제를 성공적으로 처리할 수 있다`() {
            // Given
            val paymentResult = PaymentProcessingService.PaymentResult(
                orderId = 1L,
                paidAmount = 100000L,
                remainingBalance = 400000L,
                status = "SUCCESS"
            )

            every { paymentProcessingService.processPayment(1L, 1L) } returns paymentResult

            // When
            val result = useCase.processPayment(1L, 1L)

            // Then
            assertThat(result.orderId).isEqualTo(1L)
            assertThat(result.paidAmount).isEqualTo(100000L)
            assertThat(result.status).isEqualTo("SUCCESS")
            verify { paymentProcessingService.processPayment(1L, 1L) }
        }

        @Test
        fun `권한이 없는 사용자는 결제할 수 없다`() {
            // Given
            every { paymentProcessingService.processPayment(1L, 999L) } throws OrderException.UnauthorizedOrderAccess()

            // When/Then
            assertThatThrownBy {
                useCase.processPayment(1L, 999L)
            }.isInstanceOf(OrderException.UnauthorizedOrderAccess::class.java)
        }

        @Test
        fun `잔액이 부족하면 결제할 수 없다`() {
            // Given
            every { paymentProcessingService.processPayment(1L, 1L) } throws UserException.InsufficientBalance(100000L, 50000L)

            // When/Then
            assertThatThrownBy {
                useCase.processPayment(1L, 1L)
            }.isInstanceOf(UserException.InsufficientBalance::class.java)
        }
    }
}
