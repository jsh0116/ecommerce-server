package io.hhplus.ecommerce.application.usecases

import io.hhplus.ecommerce.application.services.CouponService
import io.hhplus.ecommerce.application.services.OrderService
import io.hhplus.ecommerce.application.services.ProductRankingService
import io.hhplus.ecommerce.application.services.ProductService
import io.hhplus.ecommerce.application.services.UserService
import io.hhplus.ecommerce.domain.Inventory
import io.hhplus.ecommerce.domain.Order
import io.hhplus.ecommerce.domain.OrderItem
import io.hhplus.ecommerce.domain.Product
import io.hhplus.ecommerce.domain.User
import io.hhplus.ecommerce.domain.UserCoupon
import io.hhplus.ecommerce.exception.InventoryException
import io.hhplus.ecommerce.exception.OrderException
import io.hhplus.ecommerce.exception.ProductException
import io.hhplus.ecommerce.exception.UserException
import io.hhplus.ecommerce.infrastructure.repositories.InventoryRepository
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
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDateTime

@DisplayName("OrderUseCase 테스트")
class OrderUseCaseTest {

    private val orderService = mockk<OrderService>()
    private val productService = mockk<ProductService>()
    private val userService = mockk<UserService>()
    private val couponService = mockk<CouponService>()
    private val inventoryRepository = mockk<InventoryRepository>()
    private val productUseCase = mockk<ProductUseCase>()
    private val productRankingService = mockk<ProductRankingService>(relaxed = true)
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)

    private val useCase = OrderUseCase(
        orderService, productService, userService,
        couponService, inventoryRepository, productUseCase,
        productRankingService, eventPublisher
    )

    @Nested
    @DisplayName("주문 생성 테스트")
    inner class CreateOrderTest {
        @Test
        fun `주문을 생성할 수 있다`() {
            // Given
            val user = User(id = 1L, balance = 500000L, createdAt = "2024-01-01")
            val product = Product(id = 1L, name = "상품1", description = null, price = 50000L, category = "의류")
            val inventory = Inventory(sku = "1", physicalStock = 100, reservedStock = 0)
            val orderItems = listOf(OrderItem.create(product, 1))
            val order = Order(id = 1L, userId = 1L, items = orderItems, totalAmount = 50000L, discountAmount = 0L, finalAmount = 50000L)

            every { userService.getById(1L) } returns user
            every { productService.getById(1L) } returns product
            every { inventoryRepository.findBySku("1") } returns inventory
            every { orderService.createOrder(user, orderItems, null) } returns order

            // When
            val result = useCase.createOrder(1L, listOf(OrderUseCase.OrderItemRequest(1L, 1)), null)

            // Then
            assertThat(result).isNotNull
            assertThat(result.userId).isEqualTo(1L)
            verify { orderService.createOrder(user, orderItems, null) }
        }

        @Test
        fun `존재하지 않는 사용자는 예외를 발생시킨다`() {
            // Given
            every { userService.getById(999L) } throws UserException.UserNotFound("999")

            // When/Then
            assertThatThrownBy {
                useCase.createOrder(999L, listOf(OrderUseCase.OrderItemRequest(1L, 1)), null)
            }.isInstanceOf(UserException.UserNotFound::class.java)
        }

        @Test
        fun `존재하지 않는 상품은 예외를 발생시킨다`() {
            // Given
            val user = User(id = 1L, balance = 500000L, createdAt = "2024-01-01")
            every { userService.getById(1L) } returns user
            every { productService.getById(999L) } throws ProductException.ProductNotFound("999")

            // When/Then
            assertThatThrownBy {
                useCase.createOrder(1L, listOf(OrderUseCase.OrderItemRequest(999L, 1)), null)
            }.isInstanceOf(ProductException.ProductNotFound::class.java)
        }

        @Test
        fun `재고가 부족하면 예외를 발생시킨다`() {
            // Given
            val user = User(id = 1L, balance = 500000L, createdAt = "2024-01-01")
            val product = Product(id = 1L, name = "상품1", description = null, price = 50000L, category = "의류")
            val inventory = Inventory(sku = "1", physicalStock = 5, reservedStock = 0)

            every { userService.getById(1L) } returns user
            every { productService.getById(1L) } returns product
            every { inventoryRepository.findBySku("1") } returns inventory

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
            val order = Order(id = 1L, userId = 1L, items = orderItems, totalAmount = 100000L, discountAmount = 0L, finalAmount = 100000L, status = "PENDING")
            val inventory = Inventory(sku = "1", physicalStock = 100, reservedStock = 2)

            every { orderService.cancelOrder(1L, 1L) } returns order
            every { inventoryRepository.findBySku("1") } returns inventory
            every { inventoryRepository.save(any()) } just runs

            // When
            val result = useCase.cancelOrder(1L, 1L)

            // Then
            assertThat(result).isNotNull
            verify { orderService.cancelOrder(1L, 1L) }
            verify { inventoryRepository.save(any()) }
        }
    }

    @Nested
    @DisplayName("결제 처리 테스트")
    inner class ProcessPaymentTest {
        @Test
        fun `결제를 성공적으로 처리할 수 있다`() {
            // Given
            val orderItems = listOf(OrderItem(productId = 1L, productName = "상품1", quantity = 2, unitPrice = 50000L, subtotal = 100000L))
            val order = Order(id = 1L, userId = 1L, items = orderItems, totalAmount = 100000L, discountAmount = 0L, finalAmount = 100000L, status = "PENDING")
            val user = User(id = 1L, balance = 400000L, createdAt = "2024-01-01")
            val inventory = Inventory(sku = "1", physicalStock = 100, reservedStock = 2)
            val completedOrder = order.copy(status = "PAID", paidAt = LocalDateTime.now())

            every { orderService.getById(1L) } returns order
            every { userService.deductBalance(1L, 100000L) } returns user
            every { inventoryRepository.findBySku("1") } returns inventory
            every { inventoryRepository.save(any()) } just runs
            every { productUseCase.recordSale(any(), any()) } just runs
            every { orderService.completeOrder(1L) } returns completedOrder

            // When
            val result = useCase.processPayment(1L, 1L)

            // Then
            assertThat(result.orderId).isEqualTo(1L)
            assertThat(result.paidAmount).isEqualTo(100000L)
            assertThat(result.status).isEqualTo("SUCCESS")
            verify { userService.deductBalance(1L, 100000L) }
            verify { orderService.completeOrder(1L) }
        }

        @Test
        fun `권한이 없는 사용자는 결제할 수 없다`() {
            // Given
            val order = Order(id = 1L, userId = 1L, items = emptyList(), totalAmount = 100000L, discountAmount = 0L, finalAmount = 100000L)
            every { orderService.getById(1L) } returns order

            // When/Then
            assertThatThrownBy {
                useCase.processPayment(1L, 999L)
            }.isInstanceOf(OrderException.UnauthorizedOrderAccess::class.java)
        }

        @Test
        fun `잔액이 부족하면 결제할 수 없다`() {
            // Given
            val order = Order(id = 1L, userId = 1L, items = emptyList(), totalAmount = 100000L, discountAmount = 0L, finalAmount = 100000L, status = "PENDING")
            every { orderService.getById(1L) } returns order
            every { userService.deductBalance(1L, 100000L) } throws UserException.InsufficientBalance(100000L, 50000L)

            // When/Then
            assertThatThrownBy {
                useCase.processPayment(1L, 1L)
            }.isInstanceOf(UserException.InsufficientBalance::class.java)
        }
    }
}
