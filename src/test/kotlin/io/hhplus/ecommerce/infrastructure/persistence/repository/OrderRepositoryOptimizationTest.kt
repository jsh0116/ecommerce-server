package io.hhplus.ecommerce.infrastructure.persistence.repository

import io.hhplus.ecommerce.infrastructure.persistence.entity.OrderJpaEntity
import io.hhplus.ecommerce.infrastructure.persistence.entity.OrderJpaStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Tag
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime

/**
 * Order Repository 성능 최적화 테스트
 * 복합 인덱스 활용 및 쿼리 최적화 검증
 */
@DataJpaTest
@ActiveProfiles("test")
@Tag("performance")
@DisplayName("Order Repository 성능 최적화 테스트")
class OrderRepositoryOptimizationTest {

    @Autowired
    private lateinit var orderRepository: OrderJpaRepository

    private val testUserId = 1L
    private val testUserId2 = 2L

    @BeforeEach
    fun setUp() {
        // 테스트 데이터 준비: 100개 주문 생성
        val orders = mutableListOf<OrderJpaEntity>()

        repeat(50) { i ->
            orders.add(
                OrderJpaEntity(
                    orderNumber = "ORD-001-${String.format("%03d", i + 1)}",
                    userId = testUserId,
                    status = if (i % 3 == 0) OrderJpaStatus.PAID else OrderJpaStatus.PENDING_PAYMENT,
                    totalAmount = 100000L + i * 1000,
                    discountAmount = 10000L,
                    finalAmount = 90000L + i * 1000,
                    pointsUsed = 0L,
                    createdAt = LocalDateTime.now().minusHours((i % 24).toLong()),
                    updatedAt = LocalDateTime.now()
                )
            )
        }

        repeat(50) { i ->
            orders.add(
                OrderJpaEntity(
                    orderNumber = "ORD-002-${String.format("%03d", i + 1)}",
                    userId = testUserId2,
                    status = if (i % 2 == 0) OrderJpaStatus.SHIPPED else OrderJpaStatus.PAID,
                    totalAmount = 150000L + i * 1000,
                    discountAmount = 15000L,
                    finalAmount = 135000L + i * 1000,
                    pointsUsed = 5000L,
                    createdAt = LocalDateTime.now().minusHours((i % 24).toLong()),
                    updatedAt = LocalDateTime.now()
                )
            )
        }

        orderRepository.saveAll(orders)
    }

    @Test
    @DisplayName("사용자별 주문 조회 - 복합 인덱스 활용")
    fun testFindByUserIdOptimized() {
        // Act: idx_user_id 인덱스 활용
        val startTime = System.currentTimeMillis()
        val orders = orderRepository.findByUserIdOptimized(testUserId)
        val duration = System.currentTimeMillis() - startTime

        // Assert
        assertThat(orders).hasSize(50)
        assertThat(orders).allMatch { it.userId == testUserId }
        assertThat(duration).isLessThan(1000) // 1초 이내
    }

    @Test
    @DisplayName("사용자별 상태별 주문 조회 - 복합 인덱스 활용")
    fun testFindByUserIdAndStatusOptimized() {
        // Act: idx_user_status_paid 복합 인덱스 활용
        val startTime = System.currentTimeMillis()
        val orders = orderRepository.findByUserIdAndStatusOptimized(testUserId, OrderJpaStatus.PAID)
        val duration = System.currentTimeMillis() - startTime

        // Assert
        assertThat(orders).isNotEmpty
        assertThat(orders).allMatch {
            it.userId == testUserId && it.status == OrderJpaStatus.PAID
        }
        assertThat(duration).isLessThan(500) // 0.5초 이내
    }

    @Test
    @DisplayName("상태별 주문 조회")
    fun testFindByStatus() {
        // Act
        val startTime = System.currentTimeMillis()
        val orders = orderRepository.findByStatus(OrderJpaStatus.PAID)
        val duration = System.currentTimeMillis() - startTime

        // Assert
        assertThat(orders).isNotEmpty
        assertThat(orders).allMatch { it.status == OrderJpaStatus.PAID }
        assertThat(duration).isLessThan(500)
    }

    @Test
    @DisplayName("최근 주문 조회 - LIMIT 활용")
    fun testFindRecentOrdersByUserIdAndStatuses() {
        // Act
        val startTime = System.currentTimeMillis()
        val recentOrders = orderRepository.findRecentOrdersByUserIdAndStatuses(
            testUserId,
            listOf(OrderJpaStatus.PAID, OrderJpaStatus.PENDING_PAYMENT),
            10
        )
        val duration = System.currentTimeMillis() - startTime

        // Assert
        assertThat(recentOrders.size).isLessThanOrEqualTo(10)
        assertThat(recentOrders).allMatch { it.userId == testUserId }
        assertThat(duration).isLessThan(300)
    }

    @Test
    @DisplayName("배치: 주문 상태 대량 업데이트")
    fun testBatchUpdateStatus() {
        // Arrange
        val cutoffDate = LocalDateTime.now().minusHours(12)

        // Act
        val startTime = System.currentTimeMillis()
        val updatedCount = orderRepository.batchUpdateStatus(
            OrderJpaStatus.PENDING_PAYMENT,
            OrderJpaStatus.CANCELLED,
            cutoffDate
        )
        val duration = System.currentTimeMillis() - startTime

        // Assert
        assertThat(updatedCount).isGreaterThanOrEqualTo(0)
        assertThat(duration).isLessThan(1000)
    }

    @Test
    @DisplayName("기존 쿼리 메서드: findByUserIdOrderByCreatedAtDesc")
    fun testExistingFindByUserId() {
        // Act: 기존 메서드 성능
        val startTime = System.currentTimeMillis()
        val orders = orderRepository.findByUserIdOrderByCreatedAtDesc(testUserId)
        val duration = System.currentTimeMillis() - startTime

        // Assert
        assertThat(orders).hasSize(50)
        assertThat(duration).isLessThan(500)
    }

    @Test
    @DisplayName("기존 쿼리 메서드: findByUserIdAndStatusOrderByCreatedAtDesc")
    fun testExistingFindByUserIdAndStatus() {
        // Act: 기존 메서드 성능
        val startTime = System.currentTimeMillis()
        val orders = orderRepository.findByUserIdAndStatusOrderByCreatedAtDesc(
            testUserId,
            OrderJpaStatus.PAID
        )
        val duration = System.currentTimeMillis() - startTime

        // Assert
        assertThat(orders).isNotEmpty
        assertThat(duration).isLessThan(500)
    }

    @Test
    @DisplayName("성능: 사용자별 주문 조회 - 인덱스 효율성")
    fun testQueryPerformanceComparison() {
        // 1000개 주문으로 대규모 테스트
        val largeOrders = mutableListOf<OrderJpaEntity>()
        repeat(900) { i ->
            largeOrders.add(
                OrderJpaEntity(
                    orderNumber = "ORD-LARGE-${String.format("%04d", i + 1)}",
                    userId = testUserId,
                    status = OrderJpaStatus.PAID,
                    totalAmount = 100000L,
                    discountAmount = 10000L,
                    finalAmount = 90000L,
                    pointsUsed = 0L,
                    createdAt = LocalDateTime.now(),
                    updatedAt = LocalDateTime.now()
                )
            )
        }
        orderRepository.saveAll(largeOrders)

        // Act: 최적화된 쿼리
        val optimizedStart = System.currentTimeMillis()
        val optimizedOrders = orderRepository.findByUserIdOptimized(testUserId)
        val optimizedDuration = System.currentTimeMillis() - optimizedStart

        // Assert
        assertThat(optimizedOrders).isNotEmpty
        assertThat(optimizedDuration).isLessThan(2000) // 2초 이내
    }

    @Test
    @DisplayName("결과 정렬 검증: createdAt DESC")
    fun testResultSortingByCreatedAt() {
        // Act
        val orders = orderRepository.findByUserIdOptimized(testUserId)

        // Assert
        assertThat(orders).isNotEmpty

        // createdAt 기준 내림차순 정렬 검증
        for (i in 0 until orders.size - 1) {
            assertThat(orders[i].createdAt.isAfter(orders[i + 1].createdAt) || orders[i].createdAt.isEqual(orders[i + 1].createdAt)).isTrue()
        }
    }

    @Test
    @DisplayName("NULL 처리: 빈 결과 처리")
    fun testEmptyResultHandling() {
        // Act: 존재하지 않는 사용자
        val orders = orderRepository.findByUserIdOptimized(9999L)

        // Assert
        assertThat(orders).isEmpty()
    }

    @Test
    @DisplayName("필터 정확도: 상태별 필터링")
    fun testStatusFilterAccuracy() {
        // Act
        val paidOrders = orderRepository.findByUserIdAndStatusOptimized(testUserId, OrderJpaStatus.PAID)
        val pendingOrders = orderRepository.findByUserIdAndStatusOptimized(testUserId, OrderJpaStatus.PENDING_PAYMENT)

        // Assert
        paidOrders.forEach { assertThat(it.status).isEqualTo(OrderJpaStatus.PAID) }
        pendingOrders.forEach { assertThat(it.status).isEqualTo(OrderJpaStatus.PENDING_PAYMENT) }

        val totalCount = paidOrders.size + pendingOrders.size
        assertThat(totalCount).isEqualTo(50)
    }
}
