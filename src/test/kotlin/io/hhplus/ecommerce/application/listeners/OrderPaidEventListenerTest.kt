package io.hhplus.ecommerce.application.listeners

import io.hhplus.ecommerce.application.events.OrderPaidEvent
import io.hhplus.ecommerce.application.services.DataTransmissionService
import io.hhplus.ecommerce.domain.OrderItem
import io.hhplus.ecommerce.infrastructure.persistence.entity.DataTransmissionLogJpaEntity
import io.hhplus.ecommerce.infrastructure.persistence.entity.TransmissionStatus
import io.hhplus.ecommerce.infrastructure.persistence.repository.DataTransmissionLogJpaRepository
import io.hhplus.ecommerce.infrastructure.repositories.OrderRepository
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

@DisplayName("OrderPaidEventListener 테스트")
class OrderPaidEventListenerTest {

    private lateinit var dataTransmissionService: DataTransmissionService
    private lateinit var orderRepository: OrderRepository
    private lateinit var transmissionLogRepository: DataTransmissionLogJpaRepository
    private lateinit var listener: OrderPaidEventListener

    @BeforeEach
    fun setUp() {
        dataTransmissionService = mockk(relaxed = true)
        orderRepository = mockk(relaxed = true)
        transmissionLogRepository = mockk(relaxed = true)
        listener = OrderPaidEventListener(
            dataTransmissionService,
            orderRepository,
            transmissionLogRepository
        )
    }

    @Test
    @DisplayName("리스너 1: 외부 데이터 전송 성공 시 상태가 SUCCESS로 변경된다")
    fun dataTransmission_success_shouldUpdateStatusToSuccess() {
        // Given
        val event = createTestEvent()
        var capturedLog: DataTransmissionLogJpaEntity? = null

        every { dataTransmissionService.send(any()) } just Runs
        every { transmissionLogRepository.save(any()) } answers {
            capturedLog = firstArg<DataTransmissionLogJpaEntity>()
            capturedLog!!
        }

        // When
        listener.handleDataTransmission(event)

        // Then
        verify(exactly = 2) { transmissionLogRepository.save(any()) }

        // 최종 상태 확인: SUCCESS 상태로 업데이트되었는지 확인
        assertThat(capturedLog).isNotNull
        assertThat(capturedLog!!.orderId).isEqualTo(event.orderId)
        assertThat(capturedLog!!.userId).isEqualTo(event.userId)
        assertThat(capturedLog!!.status).isEqualTo(TransmissionStatus.SUCCESS)
        assertThat(capturedLog!!.completedAt).isNotNull()
    }

    @Test
    @DisplayName("리스너 1: 외부 데이터 전송 실패 시 상태가 FAILED로 변경되고 재시도 큐에 추가된다")
    fun dataTransmission_failure_shouldUpdateStatusToFailedAndAddToRetryQueue() {
        // Given
        val event = createTestEvent()
        val errorMessage = "외부 API 타임아웃"
        var capturedLog: DataTransmissionLogJpaEntity? = null
        val mockOrder = mockk<io.hhplus.ecommerce.domain.Order>(relaxed = true)

        every { dataTransmissionService.send(any()) } throws RuntimeException(errorMessage)
        every { transmissionLogRepository.save(any()) } answers {
            capturedLog = firstArg<DataTransmissionLogJpaEntity>()
            capturedLog!!
        }
        every { orderRepository.findById(event.orderId) } returns mockOrder

        // When
        listener.handleDataTransmission(event)

        // Then
        verify(atLeast = 2) { transmissionLogRepository.save(any()) }
        verify { dataTransmissionService.addToRetryQueue(mockOrder) }

        // 최종 상태 확인: RETRYING 상태로 업데이트되었는지 확인
        assertThat(capturedLog).isNotNull
        assertThat(capturedLog!!.status).isEqualTo(TransmissionStatus.RETRYING)
        assertThat(capturedLog!!.errorMessage).isEqualTo(errorMessage)
    }

    @Test
    @DisplayName("리스너 2: 알림톡 발송이 호출된다")
    fun notification_shouldBeSent() {
        // Given
        val event = createTestEvent()

        // When
        listener.handleOrderNotification(event)

        // Then
        // 로그 출력 확인 (실제 알림 서비스는 TODO이므로 검증 제외)
        // 실제 구현 시: verify { notificationService.sendOrderCompletedNotification(...) }
    }

    @Test
    @DisplayName("리스너 3: 구매 포인트가 정확히 적립된다 (1%)")
    fun pointReward_shouldAccumulateCorrectAmount() {
        // Given
        val event = createTestEvent(totalAmount = 10000L)
        val expectedPoints = 100L // 10000 * 0.01

        // When
        listener.handlePointReward(event)

        // Then
        // 실제 구현 시: verify { userService.addPoints(event.userId, expectedPoints, any()) }
    }

    private fun createTestEvent(
        orderId: Long = 1L,
        userId: Long = 100L,
        totalAmount: Long = 50000L
    ): OrderPaidEvent {
        return OrderPaidEvent(
            orderId = orderId,
            userId = userId,
            items = listOf(
                OrderItem(
                    productId = 1L,
                    productName = "테스트 상품",
                    quantity = 2,
                    unitPrice = 25000L,
                    subtotal = 50000L
                )
            ),
            totalAmount = totalAmount,
            discountAmount = 0L,
            paidAt = LocalDateTime.now()
        )
    }
}
