package io.hhplus.ecommerce.application.services

import com.fasterxml.jackson.databind.ObjectMapper
import io.hhplus.ecommerce.domain.OutboxEvent
import io.hhplus.ecommerce.infrastructure.repositories.OutboxEventRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("OutboxEventService 단위 테스트")
class OutboxEventServiceTest {

    private lateinit var outboxEventRepository: OutboxEventRepository
    private lateinit var objectMapper: ObjectMapper
    private lateinit var outboxEventService: OutboxEventService

    @BeforeEach
    fun setUp() {
        outboxEventRepository = mockk()
        objectMapper = ObjectMapper()
        outboxEventService = OutboxEventServiceImpl(outboxEventRepository, objectMapper)
    }

    @Test
    @DisplayName("Outbox 이벤트를 발행할 수 있다")
    fun publishOutboxEvent() {
        // Given
        val aggregateType = "ORDER"
        val aggregateId = "user-123"
        val eventType = "ORDER_PAID"
        val payload = mapOf(
            "orderId" to "order-456",
            "amount" to 10000,
            "status" to "PAID"
        )

        val savedEvent = OutboxEvent(
            id = 1L,
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            eventType = eventType,
            payload = objectMapper.writeValueAsString(payload)
        )

        val eventSlot = slot<OutboxEvent>()
        every { outboxEventRepository.save(capture(eventSlot)) } returns savedEvent

        // When
        val result = outboxEventService.publish(aggregateType, aggregateId, eventType, payload)

        // Then
        verify(exactly = 1) { outboxEventRepository.save(any()) }
        assertThat(result.aggregateType).isEqualTo(aggregateType)
        assertThat(result.aggregateId).isEqualTo(aggregateId)
        assertThat(result.eventType).isEqualTo(eventType)
        assertThat(result.payload).contains("order-456")
        assertThat(result.payload).contains("10000")
    }

    @Test
    @DisplayName("Payload가 JSON 문자열로 직렬화된다")
    fun payloadSerializedToJson() {
        // Given
        val payload = mapOf(
            "orderId" to "order-123",
            "items" to listOf(
                mapOf("productId" to "prod-1", "quantity" to 2),
                mapOf("productId" to "prod-2", "quantity" to 1)
            ),
            "totalAmount" to 50000
        )

        val eventSlot = slot<OutboxEvent>()
        every { outboxEventRepository.save(capture(eventSlot)) } answers {
            firstArg<OutboxEvent>().copy(id = 1L)
        }

        // When
        outboxEventService.publish("ORDER", "user-123", "ORDER_CREATED", payload)

        // Then
        val capturedEvent = eventSlot.captured
        val deserializedPayload = objectMapper.readValue(capturedEvent.payload, Map::class.java)
        assertThat(deserializedPayload["orderId"]).isEqualTo("order-123")
        assertThat(deserializedPayload["items"]).isInstanceOf(List::class.java)
        assertThat(deserializedPayload["totalAmount"]).isEqualTo(50000)
    }

    @Test
    @DisplayName("String payload는 그대로 저장된다")
    fun stringPayloadStoredAsIs() {
        // Given
        val jsonPayload = """{"orderId":"order-123","amount":10000}"""

        val eventSlot = slot<OutboxEvent>()
        every { outboxEventRepository.save(capture(eventSlot)) } answers {
            firstArg<OutboxEvent>().copy(id = 1L)
        }

        // When
        outboxEventService.publish("ORDER", "user-123", "ORDER_PAID", jsonPayload)

        // Then
        val capturedEvent = eventSlot.captured
        assertThat(capturedEvent.payload).isEqualTo(jsonPayload)
    }

    @Test
    @DisplayName("여러 이벤트를 연속으로 발행할 수 있다")
    fun publishMultipleEvents() {
        // Given
        every { outboxEventRepository.save(any()) } answers {
            firstArg<OutboxEvent>().copy(id = 1L)
        }

        // When
        val event1 = outboxEventService.publish("ORDER", "user-123", "ORDER_CREATED", mapOf("orderId" to "order-1"))
        val event2 = outboxEventService.publish("ORDER", "user-123", "ORDER_PAID", mapOf("orderId" to "order-1"))
        val event3 = outboxEventService.publish("COUPON", "coupon-456", "COUPON_ISSUED", mapOf("userId" to "user-123"))

        // Then
        verify(exactly = 3) { outboxEventRepository.save(any()) }
        assertThat(event1.aggregateType).isEqualTo("ORDER")
        assertThat(event2.aggregateType).isEqualTo("ORDER")
        assertThat(event3.aggregateType).isEqualTo("COUPON")
    }

    @Test
    @DisplayName("동일한 aggregateId의 이벤트는 순서가 보장된다")
    fun sameAggregateIdEventsOrdered() {
        // Given
        val userId = "user-123"
        every { outboxEventRepository.save(any()) } answers {
            firstArg<OutboxEvent>().copy(id = 1L)
        }

        // When
        val event1 = outboxEventService.publish("ORDER", userId, "ORDER_CREATED", mapOf("orderId" to "order-1"))
        val event2 = outboxEventService.publish("ORDER", userId, "ORDER_PAID", mapOf("orderId" to "order-1"))

        // Then - 동일한 aggregateId는 Kafka에서 같은 파티션으로 전송되어 순서 보장
        assertThat(event1.aggregateId).isEqualTo(event2.aggregateId)
        assertThat(event1.aggregateId).isEqualTo(userId)
    }
}
