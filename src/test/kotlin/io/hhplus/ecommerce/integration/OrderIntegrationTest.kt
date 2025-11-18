package io.hhplus.ecommerce.integration

import io.hhplus.ecommerce.infrastructure.persistence.entity.OrderJpaEntity
import io.hhplus.ecommerce.infrastructure.persistence.entity.OrderJpaStatus
import io.hhplus.ecommerce.infrastructure.persistence.repository.OrderJpaRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime

/**
 * Order 통합 테스트 (실제 MySQL 데이터베이스 사용)
 */
@DisplayName("Order 통합 테스트")
class OrderIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var orderRepository: OrderJpaRepository

    @Test
    @DisplayName("주문을 생성하고 조회할 수 있다")
    fun testCreateAndRetrieveOrder() {
        // Arrange: 테스트 데이터 준비
        val order = OrderJpaEntity(
            id = 0L,  // AUTO_INCREMENT이므로 0으로 설정
            orderNumber = "ORD-2025-001",
            userId = 1L,
            status = OrderJpaStatus.PENDING_PAYMENT,
            totalAmount = 100000L,
            discountAmount = 10000L,
            finalAmount = 90000L,
            pointsUsed = 0L,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        // Act: 주문 저장
        val savedOrder = orderRepository.save(order)

        // Assert: 저장된 주문 검증
        assertThat(savedOrder.id).isGreaterThan(0)  // ID가 생성되었는지 확인
        assertThat(savedOrder.orderNumber).isEqualTo("ORD-2025-001")
        assertThat(savedOrder.userId).isEqualTo(1L)
        assertThat(savedOrder.status).isEqualTo(OrderJpaStatus.PENDING_PAYMENT)
        assertThat(savedOrder.finalAmount).isEqualTo(90000L)
    }

    @Test
    @DisplayName("주문 번호로 주문을 조회할 수 있다")
    fun testFindOrderByOrderNumber() {
        // Arrange
        val order = OrderJpaEntity(
            id = 0L,
            orderNumber = "ORD-2025-002",
            userId = 2L,
            status = OrderJpaStatus.PENDING_PAYMENT,
            totalAmount = 50000L,
            discountAmount = 5000L,
            finalAmount = 45000L,
            pointsUsed = 0L,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        orderRepository.save(order)

        // Act
        val foundOrder = orderRepository.findByOrderNumber("ORD-2025-002")

        // Assert
        assertThat(foundOrder).isNotNull
        assertThat(foundOrder?.orderNumber).isEqualTo("ORD-2025-002")
        assertThat(foundOrder?.userId).isEqualTo(2L)
    }

    @Test
    @DisplayName("사용자별 주문을 조회할 수 있다")
    fun testFindOrdersByUserId() {
        // Arrange: 같은 사용자의 여러 주문 저장
        val userId = 3L
        val orders = listOf(
            OrderJpaEntity(
                id = 0L,
                orderNumber = "ORD-2025-003",
                userId = userId,
                status = OrderJpaStatus.PENDING_PAYMENT,
                totalAmount = 100000L,
                discountAmount = 10000L,
                finalAmount = 90000L,
                pointsUsed = 0L,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            ),
            OrderJpaEntity(
                id = 0L,
                orderNumber = "ORD-2025-004",
                userId = userId,
                status = OrderJpaStatus.PAID,
                totalAmount = 50000L,
                discountAmount = 5000L,
                finalAmount = 45000L,
                pointsUsed = 0L,
                createdAt = LocalDateTime.now().minusHours(1),
                updatedAt = LocalDateTime.now().minusHours(1)
            )
        )
        orderRepository.saveAll(orders)

        // Act
        val foundOrders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId)

        // Assert
        assertThat(foundOrders).hasSize(2)
        assertThat(foundOrders[0].orderNumber).isEqualTo("ORD-2025-003")  // 최신순
        assertThat(foundOrders[1].orderNumber).isEqualTo("ORD-2025-004")
    }

    @Test
    @DisplayName("사용자별, 상태별 주문을 조회할 수 있다")
    fun testFindOrdersByUserIdAndStatus() {
        // Arrange
        val userId = 4L
        val paidOrders = listOf(
            OrderJpaEntity(
                id = 0L,
                orderNumber = "ORD-2025-005",
                userId = userId,
                status = OrderJpaStatus.PAID,
                totalAmount = 100000L,
                discountAmount = 10000L,
                finalAmount = 90000L,
                pointsUsed = 0L,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            ),
            OrderJpaEntity(
                id = 0L,
                orderNumber = "ORD-2025-006",
                userId = userId,
                status = OrderJpaStatus.PENDING_PAYMENT,
                totalAmount = 50000L,
                discountAmount = 5000L,
                finalAmount = 45000L,
                pointsUsed = 0L,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )
        )
        orderRepository.saveAll(paidOrders)

        // Act
        val foundPaidOrders = orderRepository.findByUserIdAndStatusOrderByCreatedAtDesc(
            userId,
            OrderJpaStatus.PAID
        )

        // Assert
        assertThat(foundPaidOrders).hasSize(1)
        assertThat(foundPaidOrders[0].status).isEqualTo(OrderJpaStatus.PAID)
        assertThat(foundPaidOrders[0].orderNumber).isEqualTo("ORD-2025-005")
    }

    @Test
    @DisplayName("주문 상태를 변경할 수 있다")
    fun testUpdateOrderStatus() {
        // Arrange
        val order = OrderJpaEntity(
            id = 0L,
            orderNumber = "ORD-2025-007",
            userId = 5L,
            status = OrderJpaStatus.PENDING_PAYMENT,
            totalAmount = 100000L,
            discountAmount = 10000L,
            finalAmount = 90000L,
            pointsUsed = 0L,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        val savedOrder = orderRepository.save(order)

        // Act
        savedOrder.markAsPaid()
        val updatedOrder = orderRepository.save(savedOrder)

        // Assert
        assertThat(updatedOrder.status).isEqualTo(OrderJpaStatus.PAID)
        assertThat(updatedOrder.paidAt).isNotNull
    }

    @Test
    @DisplayName("UUID 변환 메서드가 정상 작동한다")
    fun testUuidConversion() {
        // Arrange
        val order = OrderJpaEntity(
            id = 0L,
            orderNumber = "ORD-2025-008",
            userId = 6L,
            status = OrderJpaStatus.PENDING_PAYMENT,
            totalAmount = 100000L,
            discountAmount = 10000L,
            finalAmount = 90000L,
            pointsUsed = 0L,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        val savedOrder = orderRepository.save(order)

        // Act
        val orderUuid = savedOrder.getUuid()
        val userUuid = savedOrder.getUserUuid()

        // Assert
        assertThat(orderUuid).isNotEmpty
        assertThat(userUuid).isNotEmpty
        assertThat(orderUuid).matches("[a-f0-9-]{36}")  // UUID 형식 검증
        assertThat(userUuid).matches("[a-f0-9-]{36}")
    }
}
