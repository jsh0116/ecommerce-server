package io.hhplus.ecommerce.infrastructure.repositories

import io.hhplus.ecommerce.domain.OutboxEvent
import io.hhplus.ecommerce.test.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime

@Tag("integration")
@DisplayName("OutboxEventRepository 통합 테스트")
class OutboxEventRepositoryTest : IntegrationTestBase() {

    @Autowired
    private lateinit var outboxEventRepository: OutboxEventRepository

    @Test
    @DisplayName("Outbox 이벤트를 저장하고 조회할 수 있다")
    fun saveAndFind() {
        // Given
        val outboxEvent = OutboxEvent(
            aggregateType = "ORDER",
            aggregateId = "order-123",
            eventType = "ORDER_PAID",
            payload = """{"orderId": "order-123", "amount": 10000}"""
        )

        // When
        val saved = outboxEventRepository.save(outboxEvent)

        // Then
        val found = outboxEventRepository.findById(saved.id).get()
        assertThat(found.aggregateType).isEqualTo("ORDER")
        assertThat(found.aggregateId).isEqualTo("order-123")
        assertThat(found.eventType).isEqualTo("ORDER_PAID")
        assertThat(found.payload).contains("order-123")
        assertThat(found.createdAt).isNotNull
    }

    @Test
    @DisplayName("집계 타입별로 Outbox 이벤트를 구분할 수 있다")
    fun findByAggregateType() {
        // Given
        outboxEventRepository.save(
            OutboxEvent(
                aggregateType = "ORDER",
                aggregateId = "order-1",
                eventType = "ORDER_PAID",
                payload = "{}"
            )
        )

        outboxEventRepository.save(
            OutboxEvent(
                aggregateType = "COUPON",
                aggregateId = "coupon-1",
                eventType = "COUPON_ISSUED",
                payload = "{}"
            )
        )

        // When
        val allEvents = outboxEventRepository.findAll()
        val orderEvents = allEvents.filter { it.aggregateType == "ORDER" }
        val couponEvents = allEvents.filter { it.aggregateType == "COUPON" }

        // Then
        assertThat(orderEvents).hasSizeGreaterThanOrEqualTo(1)
        assertThat(couponEvents).hasSizeGreaterThanOrEqualTo(1)
    }

    @Test
    @DisplayName("동일한 aggregateId의 이벤트들은 순서대로 저장된다")
    fun sameAggregateIdOrderPreserved() {
        // Given
        val userId = "user-999"
        val event1 = outboxEventRepository.save(
            OutboxEvent(
                aggregateType = "ORDER",
                aggregateId = userId,
                eventType = "ORDER_CREATED",
                payload = """{"orderId": "order-1"}"""
            )
        )

        Thread.sleep(10) // 시간 차이를 두기 위함

        val event2 = outboxEventRepository.save(
            OutboxEvent(
                aggregateType = "ORDER",
                aggregateId = userId,
                eventType = "ORDER_PAID",
                payload = """{"orderId": "order-2"}"""
            )
        )

        // When
        val events = outboxEventRepository.findAll()
            .filter { it.aggregateId == userId }
            .sortedBy { it.createdAt }

        // Then
        assertThat(events).hasSizeGreaterThanOrEqualTo(2)
        assertThat(events[0].id).isEqualTo(event1.id)
        assertThat(events[1].id).isEqualTo(event2.id)
        assertThat(events[0].createdAt).isBefore(events[1].createdAt)
    }

    @Test
    @DisplayName("생성 시간 기준으로 오래된 이벤트를 조회할 수 있다")
    fun findOldEvents() {
        // Given
        val oldEvent = outboxEventRepository.save(
            OutboxEvent(
                aggregateType = "ORDER",
                aggregateId = "order-old",
                eventType = "ORDER_PAID",
                payload = "{}"
            )
        )

        Thread.sleep(100)

        outboxEventRepository.save(
            OutboxEvent(
                aggregateType = "ORDER",
                aggregateId = "order-recent",
                eventType = "ORDER_PAID",
                payload = "{}"
            )
        )

        // When - 모든 이벤트를 포함하는 미래 시간으로 조회
        val cutoffTime = LocalDateTime.now().plusSeconds(1)
        val oldEvents = outboxEventRepository.findAll()
            .filter { it.createdAt.isBefore(cutoffTime) }

        // Then
        assertThat(oldEvents).hasSizeGreaterThanOrEqualTo(1)
        assertThat(oldEvents.map { it.id }).contains(oldEvent.id)
    }

    @Test
    @DisplayName("payload에 JSON 데이터를 저장할 수 있다")
    fun saveJsonPayload() {
        // Given
        val complexPayload = """
            {
                "orderId": "order-123",
                "userId": "user-456",
                "items": [
                    {"productId": "prod-1", "quantity": 2},
                    {"productId": "prod-2", "quantity": 1}
                ],
                "totalAmount": 50000
            }
        """.trimIndent()

        // When
        val saved = outboxEventRepository.save(
            OutboxEvent(
                aggregateType = "ORDER",
                aggregateId = "order-123",
                eventType = "ORDER_PAID",
                payload = complexPayload
            )
        )

        // Then
        val found = outboxEventRepository.findById(saved.id).get()
        assertThat(found.payload).contains("orderId")
        assertThat(found.payload).contains("items")
        assertThat(found.payload).contains("totalAmount")
    }
}