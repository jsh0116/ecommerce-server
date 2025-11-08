package io.hhplus.week2.application

import io.hhplus.week2.domain.*
import io.hhplus.week2.repository.*
import io.hhplus.week2.infrastructure.service.DataTransmissionService
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("OrderUseCase 테스트")
class OrderUseCaseTest {

    private lateinit var orderUseCase: OrderUseCase
    private lateinit var orderRepository: OrderRepository
    private lateinit var productRepository: ProductRepository
    private lateinit var userRepository: UserRepository
    private lateinit var couponRepository: CouponRepository
    private lateinit var inventoryRepository: InventoryRepository
    private lateinit var dataTransmissionService: DataTransmissionService

    @BeforeEach
    fun setUp() {
        orderRepository = mockk()
        productRepository = mockk()
        userRepository = mockk()
        couponRepository = mockk()
        inventoryRepository = mockk()
        dataTransmissionService = mockk()

        orderUseCase = OrderUseCase(
            orderRepository,
            productRepository,
            userRepository,
            couponRepository,
            inventoryRepository,
            dataTransmissionService
        )
    }

    @Test
    @DisplayName("주문을 생성할 수 있다")
    fun testCreateOrder() {
        // Given
        val userId = "user1"
        val productId = "prod1"
        val product = Product(
            id = productId,
            name = "노트북",
            description = "고성능 노트북",
            price = 1_000_000L,
            category = "전자제품"
        )
        val inventory = Inventory(
            sku = productId,
            physicalStock = 100,
            reservedStock = 0,
            safetyStock = 10
        )

        every { userRepository.findById(userId) } returns mockk(relaxed = true)
        every { productRepository.findById(productId) } returns product
        every { inventoryRepository.findBySku(productId) } returns inventory
        every { orderRepository.save(any()) } returnsArgument 0

        val items = listOf(
            OrderUseCase.OrderItemRequest(productId = productId, quantity = 2)
        )

        // When
        val order = orderUseCase.createOrder(userId, items, null)

        // Then
        assert(order.userId == userId)
        assert(order.items.size == 1)
        assert(order.items[0].quantity == 2)
        assert(order.totalAmount == 2_000_000L)
        verify { orderRepository.save(any()) }
    }

    @Test
    @DisplayName("재고가 부족하면 주문 생성 시 예외를 발생시킨다")
    fun testCreateOrderWithInsufficientStock() {
        // Given
        val userId = "user1"
        val productId = "prod1"
        val product = Product(
            id = productId,
            name = "노트북",
            description = "고성능 노트북",
            price = 1_000_000L,
            category = "전자제품"
        )
        val inventory = Inventory(
            sku = productId,
            physicalStock = 1,
            reservedStock = 0,
            safetyStock = 0
        )

        every { userRepository.findById(userId) } returns mockk(relaxed = true)
        every { productRepository.findById(productId) } returns product
        every { inventoryRepository.findBySku(productId) } returns inventory

        val items = listOf(
            OrderUseCase.OrderItemRequest(productId = productId, quantity = 10)
        )

        // When & Then
        val exception = assertThrows<IllegalStateException> {
            orderUseCase.createOrder(userId, items, null)
        }
        assert(exception.message?.contains("재고 부족") ?: false)
    }

    @Test
    @DisplayName("주문을 조회할 수 있다")
    fun testGetOrderById() {
        // Given
        val orderId = "order1"
        val order = Order(
            id = orderId,
            userId = "user1",
            items = listOf(),
            totalAmount = 1_000_000L,
            discountAmount = 0L,
            finalAmount = 1_000_000L
        )

        every { orderRepository.findById(orderId) } returns order

        // When
        val result = orderUseCase.getOrderById(orderId)

        // Then
        assert(result != null)
        assert(result?.id == orderId)
    }

    @Test
    @DisplayName("주문을 취소할 수 있다")
    fun testCancelOrder() {
        // Given
        val orderId = "order1"
        val userId = "user1"
        val productId = "prod1"
        val order = Order(
            id = orderId,
            userId = userId,
            items = listOf(
                OrderItem(
                    productId = productId,
                    productName = "노트북",
                    quantity = 2,
                    unitPrice = 1_000_000L,
                    subtotal = 2_000_000L
                )
            ),
            totalAmount = 2_000_000L,
            discountAmount = 0L,
            finalAmount = 2_000_000L
        )
        val inventory = Inventory(
            sku = productId,
            physicalStock = 100,
            reservedStock = 0,
            safetyStock = 10
        )

        every { orderRepository.findById(orderId) } returns order
        every { inventoryRepository.findBySku(productId) } returns inventory
        every { inventoryRepository.save(any()) } returnsArgument 0
        every { orderRepository.save(any()) } returnsArgument 0

        // When
        val cancelledOrder = orderUseCase.cancelOrder(orderId, userId)

        // Then
        assert(cancelledOrder.status == "CANCELLED")
        verify { inventoryRepository.save(any()) }
    }

    @Test
    @DisplayName("결제 처리 - 정상 결제 (쿠폰 없음)")
    fun testProcessPaymentSuccess() {
        // Given
        val orderId = "order1"
        val userId = "user1"
        val productId = "prod1"
        val order = Order(
            id = orderId,
            userId = userId,
            items = listOf(
                OrderItem(
                    productId = productId,
                    productName = "노트북",
                    quantity = 2,
                    unitPrice = 1_000_000L,
                    subtotal = 2_000_000L
                )
            ),
            totalAmount = 2_000_000L,
            discountAmount = 0L,
            finalAmount = 2_000_000L
        )
        val user = User(id = userId, balance = 5_000_000L, createdAt = "2024-01-01")
        val inventory = Inventory(
            sku = productId,
            physicalStock = 100,
            reservedStock = 2,
            safetyStock = 10
        )

        every { orderRepository.findById(orderId) } returns order
        every { userRepository.findById(userId) } returns user
        every { inventoryRepository.findBySku(productId) } returns inventory
        every { orderRepository.save(any()) } returnsArgument 0
        every { userRepository.save(any()) } returnsArgument 0
        every { inventoryRepository.save(any()) } returnsArgument 0
        every { couponRepository.findUserCoupon(any(), any()) } returns null
        every { dataTransmissionService.send(any()) } returns Unit

        // When
        val result = orderUseCase.processPayment(orderId, userId)

        // Then
        assert(result.orderId == orderId)
        assert(result.paidAmount == 2_000_000L)
        assert(result.remainingBalance == 3_000_000L)
        assert(result.status == "SUCCESS")
        assert(order.status == "PAID")
        verify { userRepository.save(any()) }
        verify { inventoryRepository.save(any()) }
    }

    @Test
    @DisplayName("결제 처리 - 주문 미존재")
    fun testProcessPaymentOrderNotFound() {
        // Given
        val orderId = "nonexistent"
        val userId = "user1"

        every { orderRepository.findById(orderId) } returns null

        // When & Then
        val exception = assertThrows<IllegalStateException> {
            orderUseCase.processPayment(orderId, userId)
        }
        assert(exception.message?.contains("주문을 찾을 수 없습니다") ?: false)
    }

    @Test
    @DisplayName("결제 처리 - 사용자 ID 불일치")
    fun testProcessPaymentUserIdMismatch() {
        // Given
        val orderId = "order1"
        val userId = "user1"
        val differentUserId = "user2"
        val order = Order(
            id = orderId,
            userId = userId,
            items = listOf(),
            totalAmount = 1_000_000L,
            discountAmount = 0L,
            finalAmount = 1_000_000L
        )

        every { orderRepository.findById(orderId) } returns order

        // When & Then
        val exception = assertThrows<IllegalStateException> {
            orderUseCase.processPayment(orderId, differentUserId)
        }
        assert(exception.message?.contains("주문을 찾을 수 없습니다") ?: false)
    }

    @Test
    @DisplayName("결제 처리 - 결제 불가 상태 (이미 결제됨)")
    fun testProcessPaymentAlreadyPaid() {
        // Given
        val orderId = "order1"
        val userId = "user1"
        val order = Order(
            id = orderId,
            userId = userId,
            items = listOf(),
            totalAmount = 1_000_000L,
            discountAmount = 0L,
            finalAmount = 1_000_000L
        )
        order.status = "PAID" // 이미 결제됨

        every { orderRepository.findById(orderId) } returns order

        // When & Then
        val exception = assertThrows<IllegalStateException> {
            orderUseCase.processPayment(orderId, userId)
        }
        assert(exception.message?.contains("결제할 수 없는 주문입니다") ?: false)
    }

    @Test
    @DisplayName("결제 처리 - 사용자 미존재")
    fun testProcessPaymentUserNotFound() {
        // Given
        val orderId = "order1"
        val userId = "user1"
        val order = Order(
            id = orderId,
            userId = userId,
            items = listOf(),
            totalAmount = 1_000_000L,
            discountAmount = 0L,
            finalAmount = 1_000_000L
        )

        every { orderRepository.findById(orderId) } returns order
        every { userRepository.findById(userId) } returns null

        // When & Then
        val exception = assertThrows<IllegalStateException> {
            orderUseCase.processPayment(orderId, userId)
        }
        assert(exception.message?.contains("사용자를 찾을 수 없습니다") ?: false)
    }

    @Test
    @DisplayName("결제 처리 - 잔액 부족")
    fun testProcessPaymentInsufficientBalance() {
        // Given
        val orderId = "order1"
        val userId = "user1"
        val order = Order(
            id = orderId,
            userId = userId,
            items = listOf(),
            totalAmount = 5_000_000L,
            discountAmount = 0L,
            finalAmount = 5_000_000L
        )
        val user = User(id = userId, balance = 1_000_000L, createdAt = "2024-01-01") // 잔액 부족

        every { orderRepository.findById(orderId) } returns order
        every { userRepository.findById(userId) } returns user

        // When & Then
        val exception = assertThrows<IllegalStateException> {
            orderUseCase.processPayment(orderId, userId)
        }
        assert(exception.message?.contains("잔액 부족") ?: false)
    }

    @Test
    @DisplayName("결제 처리 - 재고 정보 미존재")
    fun testProcessPaymentInventoryNotFound() {
        // Given
        val orderId = "order1"
        val userId = "user1"
        val productId = "prod1"
        val order = Order(
            id = orderId,
            userId = userId,
            items = listOf(
                OrderItem(
                    productId = productId,
                    productName = "노트북",
                    quantity = 2,
                    unitPrice = 1_000_000L,
                    subtotal = 2_000_000L
                )
            ),
            totalAmount = 2_000_000L,
            discountAmount = 0L,
            finalAmount = 2_000_000L
        )
        val user = User(id = userId, balance = 5_000_000L, createdAt = "2024-01-01")

        every { orderRepository.findById(orderId) } returns order
        every { userRepository.findById(userId) } returns user
        every { userRepository.save(any()) } returnsArgument 0
        every { inventoryRepository.findBySku(productId) } returns null

        // When & Then
        val exception = assertThrows<IllegalStateException> {
            orderUseCase.processPayment(orderId, userId)
        }
        assert(exception.message?.contains("재고 정보를 찾을 수 없습니다") ?: false)
    }

    @Test
    @DisplayName("결제 처리 - 쿠폰 적용된 경우")
    fun testProcessPaymentWithCoupon() {
        // Given
        val orderId = "order1"
        val userId = "user1"
        val couponId = "coupon1"
        val productId = "prod1"
        val order = Order(
            id = orderId,
            userId = userId,
            items = listOf(
                OrderItem(
                    productId = productId,
                    productName = "노트북",
                    quantity = 2,
                    unitPrice = 1_000_000L,
                    subtotal = 2_000_000L
                )
            ),
            totalAmount = 2_000_000L,
            discountAmount = 200_000L, // 10% 할인
            finalAmount = 1_800_000L,
            couponId = couponId
        )
        val user = User(id = userId, balance = 5_000_000L, createdAt = "2024-01-01")
        val inventory = Inventory(
            sku = productId,
            physicalStock = 100,
            reservedStock = 2,
            safetyStock = 10
        )
        val userCoupon = UserCoupon(userId = userId, couponId = couponId, couponName = "10% 할인", discountRate = 10)

        every { orderRepository.findById(orderId) } returns order
        every { userRepository.findById(userId) } returns user
        every { inventoryRepository.findBySku(productId) } returns inventory
        every { couponRepository.findUserCoupon(userId, couponId) } returns userCoupon
        every { orderRepository.save(any()) } returnsArgument 0
        every { userRepository.save(any()) } returnsArgument 0
        every { inventoryRepository.save(any()) } returnsArgument 0
        every { couponRepository.saveUserCoupon(any()) } returnsArgument 0
        every { dataTransmissionService.send(any()) } returns Unit

        // When
        val result = orderUseCase.processPayment(orderId, userId)

        // Then
        assert(result.paidAmount == 1_800_000L)
        assert(result.remainingBalance == 3_200_000L)
        verify { couponRepository.saveUserCoupon(any()) }
    }

    @Test
    @DisplayName("결제 처리 - 외부 데이터 전송 실패 시 재시도 큐에 추가")
    fun testProcessPaymentDataTransmissionFailure() {
        // Given
        val orderId = "order1"
        val userId = "user1"
        val productId = "prod1"
        val order = Order(
            id = orderId,
            userId = userId,
            items = listOf(
                OrderItem(
                    productId = productId,
                    productName = "노트북",
                    quantity = 2,
                    unitPrice = 1_000_000L,
                    subtotal = 2_000_000L
                )
            ),
            totalAmount = 2_000_000L,
            discountAmount = 0L,
            finalAmount = 2_000_000L
        )
        val user = User(id = userId, balance = 5_000_000L, createdAt = "2024-01-01")
        val inventory = Inventory(
            sku = productId,
            physicalStock = 100,
            reservedStock = 2,
            safetyStock = 10
        )

        every { orderRepository.findById(orderId) } returns order
        every { userRepository.findById(userId) } returns user
        every { inventoryRepository.findBySku(productId) } returns inventory
        every { orderRepository.save(any()) } returnsArgument 0
        every { userRepository.save(any()) } returnsArgument 0
        every { inventoryRepository.save(any()) } returnsArgument 0
        every { couponRepository.findUserCoupon(any(), any()) } returns null
        every { dataTransmissionService.send(any()) } throws RuntimeException("전송 실패")
        every { dataTransmissionService.addToRetryQueue(any()) } returns Unit

        // When
        val result = orderUseCase.processPayment(orderId, userId)

        // Then - 결제는 성공하지만 외부 전송은 실패
        assert(result.status == "SUCCESS")
        assert(order.status == "PAID")
        verify { dataTransmissionService.addToRetryQueue(any()) }
    }

    @Test
    @DisplayName("주문 상태 업데이트 - 주문 미존재")
    fun testUpdateOrderStatusNotFound() {
        // Given
        val orderId = "nonexistent"

        every { orderRepository.findById(orderId) } returns null

        // When
        val result = orderUseCase.updateOrderStatus(orderId, "PREPARING")

        // Then
        assert(result == null)
    }

    @Test
    @DisplayName("주문 상태 업데이트 - 주문 취소")
    fun testUpdateOrderStatusToCancelled() {
        // Given
        val orderId = "order1"
        val order = Order(
            id = orderId,
            userId = "user1",
            items = listOf(),
            totalAmount = 1_000_000L,
            discountAmount = 0L,
            finalAmount = 1_000_000L
        )
        order.status = "PENDING" // 취소 가능한 상태

        every { orderRepository.findById(orderId) } returns order
        every { orderRepository.save(any()) } returnsArgument 0

        // When
        val result = orderUseCase.updateOrderStatus(orderId, "CANCELLED")

        // Then
        assert(result != null)
        assert(result?.status == "CANCELLED")
        verify { orderRepository.save(any()) }
    }

    @Test
    @DisplayName("주문 상태 업데이트 - 취소 불가능한 상태")
    fun testUpdateOrderStatusCancelledFailed() {
        // Given
        val orderId = "order1"
        val order = Order(
            id = orderId,
            userId = "user1",
            items = listOf(),
            totalAmount = 1_000_000L,
            discountAmount = 0L,
            finalAmount = 1_000_000L
        )
        order.status = "PAID" // 취소 불가능한 상태

        every { orderRepository.findById(orderId) } returns order

        // When & Then
        val exception = assertThrows<IllegalStateException> {
            orderUseCase.updateOrderStatus(orderId, "CANCELLED")
        }
        assert(exception.message?.contains("취소할 수 없는 주문 상태입니다") ?: false)
    }

    @Test
    @DisplayName("주문 상태 업데이트 - 일반 상태 변경")
    fun testUpdateOrderStatusToOtherStatus() {
        // Given
        val orderId = "order1"
        val order = Order(
            id = orderId,
            userId = "user1",
            items = listOf(),
            totalAmount = 1_000_000L,
            discountAmount = 0L,
            finalAmount = 1_000_000L
        )
        order.status = "PENDING"

        every { orderRepository.findById(orderId) } returns order
        every { orderRepository.save(any()) } returnsArgument 0

        // When
        val result = orderUseCase.updateOrderStatus(orderId, "PREPARING")

        // Then
        assert(result != null)
        assert(result?.status == "PREPARING")
        verify { orderRepository.save(any()) }
    }

    @Test
    @DisplayName("사용자 주문 목록 조회")
    fun testGetOrdersByUserId() {
        // Given
        val userId = "user1"
        val orders = listOf(
            Order(
                id = "order1",
                userId = userId,
                items = listOf(),
                totalAmount = 1_000_000L,
                discountAmount = 0L,
                finalAmount = 1_000_000L
            ),
            Order(
                id = "order2",
                userId = userId,
                items = listOf(),
                totalAmount = 2_000_000L,
                discountAmount = 0L,
                finalAmount = 2_000_000L
            )
        )

        every { orderRepository.findByUserId(userId) } returns orders

        // When
        val result = orderUseCase.getOrdersByUserId(userId)

        // Then
        assert(result.size == 2)
        assert(result[0].id == "order1")
        assert(result[1].id == "order2")
        verify { orderRepository.findByUserId(userId) }
    }

    @Test
    @DisplayName("사용자 주문 목록 조회 - 빈 리스트")
    fun testGetOrdersByUserIdEmpty() {
        // Given
        val userId = "user1"

        every { orderRepository.findByUserId(userId) } returns emptyList()

        // When
        val result = orderUseCase.getOrdersByUserId(userId)

        // Then
        assert(result.isEmpty())
        verify { orderRepository.findByUserId(userId) }
    }
}
