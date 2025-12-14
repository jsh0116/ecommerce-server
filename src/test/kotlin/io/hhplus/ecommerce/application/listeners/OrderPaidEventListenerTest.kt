package io.hhplus.ecommerce.application.listeners

import io.hhplus.ecommerce.application.events.OrderPaidEvent
import io.hhplus.ecommerce.application.services.DataTransmissionService
import io.hhplus.ecommerce.application.services.NotificationService
import io.hhplus.ecommerce.application.services.PointService
import io.hhplus.ecommerce.application.services.TransmissionLogService
import io.hhplus.ecommerce.domain.Order
import io.hhplus.ecommerce.domain.OrderItem
import io.hhplus.ecommerce.infrastructure.persistence.entity.DataTransmissionLogJpaEntity
import io.hhplus.ecommerce.infrastructure.persistence.entity.TransmissionStatus
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

@DisplayName("OrderPaidEventListener 테스트")
class OrderPaidEventListenerTest {

    private lateinit var dataTransmissionService: DataTransmissionService
    private lateinit var transmissionLogService: TransmissionLogService
    private lateinit var notificationService: NotificationService
    private lateinit var pointService: PointService
    private lateinit var listener: OrderPaidEventListener

    @BeforeEach
    fun setUp() {
        dataTransmissionService = mockk(relaxed = true)
        transmissionLogService = mockk(relaxed = true)
        notificationService = mockk(relaxed = true)
        pointService = mockk(relaxed = true)
        listener = OrderPaidEventListener(
            dataTransmissionService,
            transmissionLogService,
            notificationService,
            pointService
        )
    }

    @Test
    @DisplayName("리스너 1: 외부 데이터 전송 성공 시 상태가 SUCCESS로 변경된다")
    fun dataTransmission_success_shouldUpdateStatusToSuccess() {
        // Given
        val event = createTestEvent()
        val log = DataTransmissionLogJpaEntity(
            orderId = event.orderId,
            userId = event.userId,
            status = TransmissionStatus.PENDING
        )

        every { transmissionLogService.createPendingLog(event.orderId, event.userId) } returns log
        every { dataTransmissionService.send(any()) } just Runs
        every { transmissionLogService.markAsSuccess(any()) } returns log.apply { markAsSuccess() }

        // When
        listener.handleDataTransmission(event)

        // Then
        verify(exactly = 1) { transmissionLogService.createPendingLog(event.orderId, event.userId) }
        verify(exactly = 1) { transmissionLogService.markAsSuccess(log) }
    }

    @Test
    @DisplayName("리스너 1: 외부 데이터 전송 실패 시 상태가 FAILED로 변경되고 재시도 큐에 추가된다")
    fun dataTransmission_failure_shouldUpdateStatusToFailedAndAddToRetryQueue() {
        // Given
        val event = createTestEvent()
        val errorMessage = "외부 API 타임아웃"
        val log = DataTransmissionLogJpaEntity(
            orderId = event.orderId,
            userId = event.userId,
            status = TransmissionStatus.PENDING
        )

        every { transmissionLogService.createPendingLog(event.orderId, event.userId) } returns log
        every { dataTransmissionService.send(any()) } throws RuntimeException(errorMessage)
        every { transmissionLogService.markAsFailed(any(), any()) } returns log.apply { markAsFailed(errorMessage) }
        every { transmissionLogService.markAsRetrying(any()) } returns log.apply { markAsRetrying() }
        every { dataTransmissionService.addToRetryQueue(event.order) } just Runs

        // When
        listener.handleDataTransmission(event)

        // Then
        verify(exactly = 1) { transmissionLogService.createPendingLog(event.orderId, event.userId) }
        verify(exactly = 1) { transmissionLogService.markAsFailed(log, errorMessage) }
        verify(exactly = 1) { transmissionLogService.markAsRetrying(log) }
        verify(exactly = 1) { dataTransmissionService.addToRetryQueue(event.order) }
    }

    @Test
    @DisplayName("리스너 2: 알림톡 발송이 호출된다")
    fun notification_shouldBeSent() {
        // Given
        val event = createTestEvent()
        every { notificationService.sendOrderCompletedNotification(any(), any(), any(), any()) } just Runs

        // When
        listener.handleOrderNotification(event)

        // Then
        verify(exactly = 1) {
            notificationService.sendOrderCompletedNotification(
                userId = event.userId,
                orderId = event.orderId,
                totalAmount = event.totalAmount,
                itemCount = event.items.size
            )
        }
    }

    @Test
    @DisplayName("리스너 3: 구매 포인트가 정확히 적립된다 (1%)")
    fun pointReward_shouldAccumulateCorrectAmount() {
        // Given
        val event = createTestEvent(totalAmount = 10000L)
        val expectedPoints = 100L // 10000 * 0.01
        every { pointService.addPoints(any(), any(), any()) } returns 100L

        // When
        listener.handlePointReward(event)

        // Then
        verify(exactly = 1) {
            pointService.addPoints(
                userId = event.userId,
                points = expectedPoints,
                reason = "주문 완료 적립 (주문번호: ${event.orderId})"
            )
        }
    }

    private fun createTestEvent(
        orderId: Long = 1L,
        userId: Long = 100L,
        totalAmount: Long = 50000L
    ): OrderPaidEvent {
        val items = listOf(
            OrderItem(
                productId = 1L,
                productName = "테스트 상품",
                quantity = 2,
                unitPrice = 25000L,
                subtotal = 50000L
            )
        )
        val order = Order(
            id = orderId,
            userId = userId,
            items = items,
            totalAmount = totalAmount,
            discountAmount = 0L,
            finalAmount = totalAmount
        )
        return OrderPaidEvent(
            orderId = orderId,
            userId = userId,
            items = items,
            totalAmount = totalAmount,
            discountAmount = 0L,
            paidAt = LocalDateTime.now(),
            order = order
        )
    }
}
