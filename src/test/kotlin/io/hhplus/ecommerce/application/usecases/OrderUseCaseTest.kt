package io.hhplus.ecommerce.application.usecases

import io.hhplus.ecommerce.domain.Inventory
import io.hhplus.ecommerce.domain.Order
import io.hhplus.ecommerce.domain.OrderItem
import io.hhplus.ecommerce.domain.Product
import io.hhplus.ecommerce.domain.User
import io.hhplus.ecommerce.domain.UserCoupon
import io.hhplus.ecommerce.application.services.DataTransmissionService
import io.hhplus.ecommerce.exception.InventoryException
import io.hhplus.ecommerce.exception.OrderException
import io.hhplus.ecommerce.exception.ProductException
import io.hhplus.ecommerce.exception.UserException
import io.hhplus.ecommerce.infrastructure.repositories.CouponRepository
import io.hhplus.ecommerce.infrastructure.repositories.InventoryRepository
import io.hhplus.ecommerce.infrastructure.repositories.OrderRepository
import io.hhplus.ecommerce.infrastructure.repositories.ProductRepository
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

@DisplayName("OrderUseCase 테스트")
class OrderUseCaseTest {

    private val orderRepository = mockk<OrderRepository>()
    private val productRepository = mockk<ProductRepository>()
    private val userRepository = mockk<UserRepository>()
    private val couponRepository = mockk<CouponRepository>()
    private val inventoryRepository = mockk<InventoryRepository>()
    private val dataTransmissionService = mockk<DataTransmissionService>(relaxed = true)
    private val productUseCase = mockk<ProductUseCase>()

    private val useCase = OrderUseCase(
        orderRepository, productRepository, userRepository,
        couponRepository, inventoryRepository, dataTransmissionService, productUseCase
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
            val order = Order(id = 1L, userId = 1L, items = emptyList(), totalAmount = 50000L, discountAmount = 0L, finalAmount = 50000L)

            every { userRepository.findById(1L) } returns user
            every { productRepository.findById(1L) } returns product
            every { inventoryRepository.findBySku("1") } returns inventory
            every { orderRepository.save(any()) } returns order

            // When
            val result = useCase.createOrder(1L, listOf(OrderUseCase.OrderItemRequest(1L, 1)), null)

            // Then
            assertThat(result).isNotNull
            assertThat(result.userId).isEqualTo(1L)
            verify { orderRepository.save(any()) }
        }

        @Test
        fun `존재하지 않는 사용자는 예외를 발생시킨다`() {
            // Given
            every { userRepository.findById(999L) } returns null

            // When/Then
            assertThatThrownBy {
                useCase.createOrder(999L, listOf(OrderUseCase.OrderItemRequest(1L, 1)), null)
            }.isInstanceOf(UserException.UserNotFound::class.java)
        }

        @Test
        fun `존재하지 않는 상품은 예외를 발생시킨다`() {
            // Given
            val user = User(id = 1L, balance = 500000L, createdAt = "2024-01-01")
            every { userRepository.findById(1L) } returns user
            every { productRepository.findById(999L) } returns null

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

            every { userRepository.findById(1L) } returns user
            every { productRepository.findById(1L) } returns product
            every { inventoryRepository.findBySku("1") } returns inventory

            // When/Then
            assertThatThrownBy {
                useCase.createOrder(1L, listOf(OrderUseCase.OrderItemRequest(1L, 10)), null)
            }.isInstanceOf(InventoryException.InsufficientStock::class.java)
        }

        @Test
        fun `유효하지 않은 쿠폰은 예외를 발생시킨다`() {
            // Given
            val user = User(id = 1L, balance = 500000L, createdAt = "2024-01-01")
            val product = Product(id = 1L, name = "상품1", description = null, price = 50000L, category = "의류")
            val inventory = Inventory(sku = "1", physicalStock = 100, reservedStock = 0)

            every { userRepository.findById(1L) } returns user
            every { productRepository.findById(1L) } returns product
            every { inventoryRepository.findBySku("1") } returns inventory
            every { couponRepository.findUserCoupon(1L, 1L) } returns null

            // When/Then
            assertThatThrownBy {
                useCase.createOrder(1L, listOf(OrderUseCase.OrderItemRequest(1L, 1)), 1L)
            }.isInstanceOf(Exception::class.java)
        }
    }

    @Nested
    @DisplayName("주문 조회 테스트")
    inner class GetOrderTest {
        @Test
        fun `주문을 ID로 조회할 수 있다`() {
            // Given
            val order = Order(id = 1L, userId = 1L, items = emptyList(), totalAmount = 50000L, discountAmount = 0L, finalAmount = 50000L)
            every { orderRepository.findById(1L) } returns order

            // When
            val result = useCase.getOrderById(1L)

            // Then
            assertThat(result).isNotNull
            assertThat(result?.id).isEqualTo(1L)
        }

        @Test
        fun `존재하지 않는 주문은 null을 반환한다`() {
            // Given
            every { orderRepository.findById(999L) } returns null

            // When
            val result = useCase.getOrderById(999L)

            // Then
            assertThat(result).isNull()
        }
    }

    @Nested
    @DisplayName("사용자 주문 목록 조회 테스트")
    inner class GetOrdersByUserIdTest {
        @Test
        fun `사용자의 주문 목록을 조회할 수 있다`() {
            // Given
            val orders = listOf(
                Order(id = 1L, userId = 1L, items = emptyList(), totalAmount = 50000L, discountAmount = 0L, finalAmount = 50000L),
                Order(id = 2L, userId = 1L, items = emptyList(), totalAmount = 30000L, discountAmount = 0L, finalAmount = 30000L)
            )
            every { orderRepository.findByUserId(1L) } returns orders

            // When
            val result = useCase.getOrdersByUserId(1L)

            // Then
            assertThat(result).hasSize(2)
            assertThat(result).extracting<Long> { it.id }.contains(1L, 2L)
        }

        @Test
        fun `사용자의 주문이 없으면 빈 목록을 반환한다`() {
            // Given
            every { orderRepository.findByUserId(999L) } returns emptyList()

            // When
            val result = useCase.getOrdersByUserId(999L)

            // Then
            assertThat(result).isEmpty()
        }
    }

    @Nested
    @DisplayName("주문 상태 업데이트 테스트")
    inner class UpdateOrderStatusTest {
        @Test
        fun `주문 상태를 업데이트할 수 있다`() {
            // Given
            val order = Order(id = 1L, userId = 1L, items = emptyList(), totalAmount = 50000L, discountAmount = 0L, finalAmount = 50000L, status = "PENDING")
            every { orderRepository.findById(1L) } returns order
            every { orderRepository.save(order) } returns order

            // When
            val result = useCase.updateOrderStatus(1L, "PAID")

            // Then
            assertThat(result).isNotNull
            assertThat(result?.status).isEqualTo("PAID")
        }

        @Test
        fun `존재하지 않는 주문은 null을 반환한다`() {
            // Given
            every { orderRepository.findById(999L) } returns null

            // When
            val result = useCase.updateOrderStatus(999L, "PAID")

            // Then
            assertThat(result).isNull()
        }

        @Test
        fun `취소할 수 없는 주문은 예외를 발생시킨다`() {
            // Given
            val order = Order(id = 1L, userId = 1L, items = emptyList(), totalAmount = 50000L, discountAmount = 0L, finalAmount = 50000L, status = "SHIPPED")
            every { orderRepository.findById(1L) } returns order

            // When/Then
            assertThatThrownBy {
                useCase.updateOrderStatus(1L, "CANCELLED")
            }.isInstanceOf(OrderException.CannotCancelOrder::class.java)
        }
    }

    @Nested
    @DisplayName("주문 취소 테스트")
    inner class CancelOrderTest {
        @Test
        fun `주문을 취소할 수 있다`() {
            // Given
            val order = Order(id = 1L, userId = 1L, items = listOf(OrderItem(productId = 1L, productName = "상품", quantity = 5, unitPrice = 10000L, subtotal = 50000L)), totalAmount = 50000L, discountAmount = 0L, finalAmount = 50000L, status = "PENDING")
            val inventory = Inventory(sku = "1", physicalStock = 95, reservedStock = 5)

            every { orderRepository.findById(1L) } returns order
            every { inventoryRepository.findBySku("1") } returns inventory
            every { inventoryRepository.save(inventory) } just runs
            every { orderRepository.save(order) } returns order

            // When
            val result = useCase.cancelOrder(1L, 1L)

            // Then
            assertThat(result).isNotNull
            assertThat(result.status).isEqualTo("CANCELLED")
            verify { inventoryRepository.save(inventory) }
        }

        @Test
        fun `다른 사용자의 주문은 취소할 수 없다`() {
            // Given
            val order = Order(id = 1L, userId = 1L, items = emptyList(), totalAmount = 50000L, discountAmount = 0L, finalAmount = 50000L)
            every { orderRepository.findById(1L) } returns order

            // When/Then
            assertThatThrownBy {
                useCase.cancelOrder(1L, 2L)
            }.isInstanceOf(OrderException.UnauthorizedOrderAccess::class.java)
        }

        @Test
        fun `존재하지 않는 주문은 예외를 발생시킨다`() {
            // Given
            every { orderRepository.findById(999L) } returns null

            // When/Then
            assertThatThrownBy {
                useCase.cancelOrder(999L, 1L)
            }.isInstanceOf(OrderException.OrderNotFound::class.java)
        }
    }

    @Nested
    @DisplayName("결제 처리 테스트")
    inner class ProcessPaymentTest {
        @Test
        fun `결제를 처리할 수 있다`() {
            // Given
            val order = Order(id = 1L, userId = 1L, items = listOf(OrderItem(productId = 1L, productName = "상품", quantity = 1, unitPrice = 50000L, subtotal = 50000L)), totalAmount = 50000L, discountAmount = 0L, finalAmount = 50000L, status = "PENDING")
            val user = User(id = 1L, balance = 100000L, createdAt = "2024-01-01")
            val inventory = Inventory(sku = "1", physicalStock = 99, reservedStock = 1)

            every { orderRepository.findById(1L) } returns order
            every { userRepository.findById(1L) } returns user
            every { userRepository.save(user) } just runs
            every { inventoryRepository.findBySku("1") } returns inventory
            every { inventoryRepository.save(inventory) } just runs
            every { couponRepository.findUserCoupon(any(), any()) } returns null
            every { productUseCase.recordSale(1L, 1) } just runs
            every { orderRepository.save(order) } returns order
            every { dataTransmissionService.send(any()) } just runs

            // When
            val result = useCase.processPayment(1L, 1L)

            // Then
            assertThat(result.status).isEqualTo("SUCCESS")
            assertThat(result.remainingBalance).isEqualTo(50000L)
            verify { userRepository.save(user) }
            verify { orderRepository.save(order) }
        }

        @Test
        fun `잔액이 부족하면 예외를 발생시킨다`() {
            // Given
            val order = Order(id = 1L, userId = 1L, items = emptyList(), totalAmount = 100000L, discountAmount = 0L, finalAmount = 100000L, status = "PENDING")
            val user = User(id = 1L, balance = 50000L, createdAt = "2024-01-01")

            every { orderRepository.findById(1L) } returns order
            every { userRepository.findById(1L) } returns user

            // When/Then
            assertThatThrownBy {
                useCase.processPayment(1L, 1L)
            }.isInstanceOf(UserException.InsufficientBalance::class.java)
        }

        @Test
        fun `결제할 수 없는 주문은 예외를 발생시킨다`() {
            // Given
            val order = Order(id = 1L, userId = 1L, items = emptyList(), totalAmount = 50000L, discountAmount = 0L, finalAmount = 50000L, status = "SHIPPED")

            every { orderRepository.findById(1L) } returns order

            // When/Then
            assertThatThrownBy {
                useCase.processPayment(1L, 1L)
            }.isInstanceOf(OrderException.CannotPayOrder::class.java)
        }

        @Test
        fun `다른 사용자의 결제는 처리할 수 없다`() {
            // Given
            val order = Order(id = 1L, userId = 1L, items = emptyList(), totalAmount = 50000L, discountAmount = 0L, finalAmount = 50000L, status = "PENDING")

            every { orderRepository.findById(1L) } returns order

            // When/Then
            assertThatThrownBy {
                useCase.processPayment(1L, 2L)
            }.isInstanceOf(OrderException.UnauthorizedOrderAccess::class.java)
        }
    }
}
