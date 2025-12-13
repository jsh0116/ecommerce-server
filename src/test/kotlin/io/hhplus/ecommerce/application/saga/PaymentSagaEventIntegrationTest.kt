package io.hhplus.ecommerce.application.saga

import io.hhplus.ecommerce.application.events.OrderPaidEvent
import io.hhplus.ecommerce.application.usecases.OrderUseCase
import io.hhplus.ecommerce.infrastructure.persistence.entity.InventoryJpaEntity
import io.hhplus.ecommerce.infrastructure.persistence.entity.ProductJpaEntity
import io.hhplus.ecommerce.infrastructure.persistence.entity.UserJpaEntity
import io.hhplus.ecommerce.infrastructure.persistence.repository.*
import io.hhplus.ecommerce.integration.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.test.context.ActiveProfiles
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * SAGA와 Application Event 통합 테스트
 *
 * SAGA 성공 시 OrderPaidEvent가 발행되어
 * 이벤트 리스너들이 비동기로 실행되는지 검증합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("SAGA + Application Event 통합 테스트")
class PaymentSagaEventIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var paymentSagaOrchestrator: PaymentSagaOrchestrator

    @Autowired
    private lateinit var userRepository: UserJpaRepository

    @Autowired
    private lateinit var productRepository: ProductJpaRepository

    @Autowired
    private lateinit var inventoryRepository: InventoryJpaRepository

    @Autowired
    private lateinit var orderRepository: OrderJpaRepository

    @Autowired
    private lateinit var orderUseCase: OrderUseCase

    @Autowired
    private lateinit var testEventListener: TestOrderPaidEventListener

    private var userId: Long = 0
    private var productId: Long = 0
    private var orderId: Long = 0

    @BeforeEach
    fun setUp() {
        // 테스트 이벤트 리스너 초기화
        testEventListener.reset()

        // 기존 데이터 정리
        orderRepository.deleteAll()
        inventoryRepository.deleteAll()
        productRepository.deleteAll()
        userRepository.deleteAll()

        // 테스트 데이터 생성
        val user = UserJpaEntity(
            id = 0L,
            balance = 1000000L,
            createdAt = java.time.LocalDateTime.now()
        )
        val savedUser = userRepository.save(user)
        userId = savedUser.id

        val product = ProductJpaEntity(
            id = 0L,
            name = "이벤트 테스트 상품",
            description = "SAGA Event 테스트용",
            price = 50000L,
            category = "전자제품",
            viewCount = 0L,
            salesCount = 0L,
            createdAt = java.time.LocalDateTime.now()
        )
        val savedProduct = productRepository.save(product)
        productId = savedProduct.id

        val inventory = InventoryJpaEntity(
            sku = productId.toString(),
            physicalStock = 100,
            reservedStock = 0,
            safetyStock = 0
        )
        inventoryRepository.save(inventory)

        // 주문 생성
        val order = orderUseCase.createOrder(
            userId = userId,
            items = listOf(OrderUseCase.OrderItemRequest(productId = productId, quantity = 2)),
            couponId = null
        )
        orderId = order.id
    }

    @Test
    @DisplayName("SAGA 성공 시 OrderPaidEvent가 발행되어 이벤트 리스너가 실행된다")
    fun sagaSuccessShouldPublishOrderPaidEvent() {
        // Given: 주문과 사용자가 준비됨 (setUp()에서 생성)
        val request = PaymentSagaRequest(
            orderId = orderId,
            userId = userId
        )

        // When: SAGA 실행
        val response = paymentSagaOrchestrator.execute(request)

        // Then: SAGA 성공 확인
        assertThat(response.status).isEqualTo("SUCCESS")

        // OrderPaidEvent가 발행되었는지 확인 (비동기이므로 대기)
        val eventReceived = testEventListener.awaitEvent(5, TimeUnit.SECONDS)
        assertThat(eventReceived).isTrue()

        // 발행된 이벤트 검증
        val events = testEventListener.getReceivedEvents()
        assertThat(events).hasSize(1)

        val event = events[0]
        assertThat(event.orderId).isEqualTo(orderId)
        assertThat(event.userId).isEqualTo(userId)
        assertThat(event.items).isNotEmpty()
    }

    @Test
    @DisplayName("SAGA 실패 시 OrderPaidEvent가 발행되지 않는다")
    fun sagaFailureShouldNotPublishOrderPaidEvent() {
        // Given: 잔액 부족한 사용자로 변경
        val poorUser = userRepository.findById(userId).get()
        poorUser.balance = 1000L  // 부족한 잔액으로 변경
        userRepository.save(poorUser)

        val request = PaymentSagaRequest(
            orderId = orderId,
            userId = userId
        )

        // When: SAGA 실행 (실패 예상)
        try {
            paymentSagaOrchestrator.execute(request)
        } catch (e: SagaExecutionException) {
            // 예상된 실패
        }

        // Then: OrderPaidEvent가 발행되지 않았는지 확인
        val eventReceived = testEventListener.awaitEvent(2, TimeUnit.SECONDS)
        assertThat(eventReceived).isFalse()

        val events = testEventListener.getReceivedEvents()
        assertThat(events).isEmpty()
    }
}

/**
 * 테스트용 이벤트 리스너
 *
 * OrderPaidEvent를 캡처하여 테스트 검증에 사용합니다.
 */
@Component
class TestOrderPaidEventListener {

    private val receivedEvents = CopyOnWriteArrayList<OrderPaidEvent>()
    private var latch: CountDownLatch? = null

    @EventListener
    fun handleOrderPaidEvent(event: OrderPaidEvent) {
        receivedEvents.add(event)
        latch?.countDown()
    }

    fun reset() {
        receivedEvents.clear()
        latch = CountDownLatch(1)
    }

    fun awaitEvent(timeout: Long, unit: TimeUnit): Boolean {
        return latch?.await(timeout, unit) ?: false
    }

    fun getReceivedEvents(): List<OrderPaidEvent> = receivedEvents.toList()
}
