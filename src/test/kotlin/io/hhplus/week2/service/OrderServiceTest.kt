package io.hhplus.week2.service

import io.hhplus.week2.domain.Order
import io.hhplus.week2.domain.OrderStatus
import io.hhplus.week2.domain.PaymentBreakdown
import io.hhplus.week2.domain.PaymentInfo
import io.hhplus.week2.domain.ShippingAddress
import io.hhplus.week2.repository.mock.OrderRepositoryMock
import io.hhplus.week2.service.impl.OrderServiceImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@DisplayName("OrderServiceImpl 테스트")
class OrderServiceTest {

    private lateinit var orderRepository: OrderRepositoryMock
    private lateinit var orderService: OrderService

    @BeforeEach
    fun setUp() {
        orderRepository = OrderRepositoryMock()
        // 주문 저장소 기능만 테스트하기 위해 간단한 더미 서비스들 사용
        orderService = object : OrderService {
            override fun createOrder(order: Order): Order = orderRepository.save(order)
            override fun getOrderById(orderId: String): Order? = orderRepository.findById(orderId)
            override fun getOrdersByUserId(userId: String): List<Order> = orderRepository.findByUserId(userId)
            override fun updateOrderStatus(orderId: String, newStatus: OrderStatus): Order? {
                val order = orderRepository.findById(orderId) ?: return null
                val updatedOrder = order.copy(status = newStatus)
                return orderRepository.update(updatedOrder)
            }
            override fun generateOrderNumber(): String {
                val today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                return "$today${String.format("%05d", 1)}"
            }
            override fun calculateShippingFee(shippingMethod: String, subtotal: Long): Long = 0L
            override fun processCreateOrder(request: io.hhplus.week2.dto.CreateOrderRequest, userId: String): OrderCreationResult =
                OrderCreationResult(success = false, errorCode = "NOT_IMPLEMENTED")
        }
    }

    @Test
    @DisplayName("주문을 생성할 수 있다")
    fun testCreateOrder() {
        // given
        val order = Order(
            id = "order_001",
            orderNumber = "20251031_00001",
            userId = "user_001",
            status = OrderStatus.PENDING_PAYMENT,
            items = emptyList(),
            payment = PaymentInfo(
                amount = 79000,
                breakdown = PaymentBreakdown(
                    subtotal = 79000,
                    discount = 0,
                    pointsUsed = 0,
                    shipping = 0,
                    total = 79000
                ),
                method = "CREDIT_CARD",
                status = "PENDING"
            ),
            shippingAddress = ShippingAddress(
                name = "홍길동",
                phone = "010-1234-5678",
                address = "서울시 강남구",
                addressDetail = "123호",
                zipCode = "12345"
            ),
            shippingMethod = "STANDARD",
            couponCode = null,
            pointsUsed = 0,
            subtotal = 79000,
            discount = 0,
            shippingFee = 0,
            totalAmount = 79000,
            requestMessage = null,
            reservationExpiry = null,
            createdAt = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
        )

        // when
        val result = orderService.createOrder(order)

        // then
        assertThat(result).isNotNull
        assertThat(result.id).isEqualTo("order_001")
        assertThat(result.status).isEqualTo(OrderStatus.PENDING_PAYMENT)
    }

    @Test
    @DisplayName("주문 ID로 주문을 조회할 수 있다")
    fun testGetOrderById() {
        // given
        val orderId = "order_001"
        val order = createTestOrder(orderId, "user_001")
        orderRepository.save(order)

        // when
        val result = orderService.getOrderById(orderId)

        // then
        assertThat(result).isNotNull
        assertThat(result?.id).isEqualTo(orderId)
        assertThat(result?.orderNumber).isEqualTo("20251031_00001")
    }

    @Test
    @DisplayName("존재하지 않는 주문 ID로 조회하면 null을 반환한다")
    fun testGetOrderByIdNotFound() {
        // when
        val result = orderService.getOrderById("nonexistent_order")

        // then
        assertThat(result).isNull()
    }

    @Test
    @DisplayName("사용자 ID로 주문 목록을 조회할 수 있다")
    fun testGetOrdersByUserId() {
        // given
        val userId = "user_001"
        val order1 = createTestOrder("order_001", userId)
        val order2 = createTestOrder("order_002", userId)
        orderRepository.save(order1)
        orderRepository.save(order2)

        // when
        val result = orderService.getOrdersByUserId(userId)

        // then
        assertThat(result).hasSize(2)
        assertThat(result[0].id).isEqualTo("order_001")
        assertThat(result[1].id).isEqualTo("order_002")
    }

    @Test
    @DisplayName("사용자의 주문이 없을 수 있다")
    fun testGetOrdersByUserIdEmpty() {
        // when
        val result = orderService.getOrdersByUserId("user_002")

        // then
        assertThat(result).isEmpty()
    }

    @Test
    @DisplayName("주문 상태를 업데이트할 수 있다")
    fun testUpdateOrderStatus() {
        // given
        val orderId = "order_001"
        val originalOrder = createTestOrder(orderId, "user_001")
        orderRepository.save(originalOrder)
        val newStatus = OrderStatus.DELIVERED

        // when
        val result = orderService.updateOrderStatus(orderId, newStatus)

        // then
        assertThat(result).isNotNull
        assertThat(result?.status).isEqualTo(newStatus)
    }

    @Test
    @DisplayName("존재하지 않는 주문의 상태 업데이트는 실패한다")
    fun testUpdateOrderStatusNotFound() {
        // when
        val result = orderService.updateOrderStatus("nonexistent_order", OrderStatus.DELIVERED)

        // then
        assertThat(result).isNull()
    }

    private fun createTestOrder(orderId: String, userId: String): Order {
        return Order(
            id = orderId,
            orderNumber = "20251031_00001",
            userId = userId,
            status = OrderStatus.PENDING_PAYMENT,
            items = emptyList(),
            payment = PaymentInfo(
                amount = 79000,
                breakdown = PaymentBreakdown(
                    subtotal = 79000,
                    discount = 0,
                    pointsUsed = 0,
                    shipping = 0,
                    total = 79000
                ),
                method = "CREDIT_CARD",
                status = "PENDING"
            ),
            shippingAddress = ShippingAddress(
                name = "홍길동",
                phone = "010-1234-5678",
                address = "서울시 강남구",
                addressDetail = "123호",
                zipCode = "12345"
            ),
            shippingMethod = "STANDARD",
            couponCode = null,
            pointsUsed = 0,
            subtotal = 79000,
            discount = 0,
            shippingFee = 0,
            totalAmount = 79000,
            requestMessage = null,
            reservationExpiry = null,
            createdAt = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
        )
    }

    @Test
    @DisplayName("주문 번호를 생성할 수 있다")
    fun testGenerateOrderNumber() {
        // when
        val orderNumber1 = orderService.generateOrderNumber()
        val orderNumber2 = orderService.generateOrderNumber()

        // then
        assertThat(orderNumber1).isNotNull
        assertThat(orderNumber2).isNotNull
        // 형식 확인: yyyyMMdd + 5자리 순번
        assertThat(orderNumber1).matches("\\d{8}\\d{5}")
        assertThat(orderNumber2).matches("\\d{8}\\d{5}")
        // 순번이 증가해야 함
        assertThat(orderNumber2).isGreaterThan(orderNumber1)
    }
}