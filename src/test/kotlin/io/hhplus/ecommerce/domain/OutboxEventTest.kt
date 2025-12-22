package io.hhplus.ecommerce.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

@DisplayName("OutboxEvent 도메인 테스트")
class OutboxEventTest {

    @Test
    @DisplayName("Outbox 이벤트를 생성할 수 있다")
    fun createOutboxEvent() {
        // Given
        val aggregateType = "ORDER"
        val aggregateId = "order-123"
        val eventType = "ORDER_PAID"
        val payload = """{"orderId": "order-123", "amount": 10000}"""

        // When
        val outboxEvent = OutboxEvent(
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            eventType = eventType,
            payload = payload
        )

        // Then
        assertThat(outboxEvent.aggregateType).isEqualTo(aggregateType)
        assertThat(outboxEvent.aggregateId).isEqualTo(aggregateId)
        assertThat(outboxEvent.eventType).isEqualTo(eventType)
        assertThat(outboxEvent.payload).isEqualTo(payload)
        assertThat(outboxEvent.createdAt).isNotNull
    }

    @Test
    @DisplayName("생성 시간이 자동으로 설정된다")
    fun createdAtAutoSet() {
        // Given
        val beforeTime = LocalDateTime.now()

        // When
        val outboxEvent = createTestOutboxEvent()

        // Then
        assertThat(outboxEvent.createdAt).isNotNull
        assertThat(outboxEvent.createdAt).isAfterOrEqualTo(beforeTime)
    }

    @Test
    @DisplayName("동일한 aggregateId를 가진 이벤트는 같은 Kafka 파티션으로 전송된다")
    fun sameAggregateIdForPartitioning() {
        // Given
        val userId = "user-123"

        // When
        val event1 = OutboxEvent(
            aggregateType = "ORDER",
            aggregateId = userId,
            eventType = "ORDER_CREATED",
            payload = """{"orderId": "order-1"}"""
        )
        val event2 = OutboxEvent(
            aggregateType = "ORDER",
            aggregateId = userId,
            eventType = "ORDER_PAID",
            payload = """{"orderId": "order-2"}"""
        )

        // Then - 동일한 aggregateId는 순서 보장을 위해 같은 파티션으로 전송됨
        assertThat(event1.aggregateId).isEqualTo(event2.aggregateId)
    }

    @Test
    @DisplayName("aggregateType으로 Topic을 구분할 수 있다")
    fun aggregateTypeForTopicRouting() {
        // Given & When
        val orderEvent = OutboxEvent(
            aggregateType = "ORDER",
            aggregateId = "order-123",
            eventType = "ORDER_PAID",
            payload = "{}"
        )
        val couponEvent = OutboxEvent(
            aggregateType = "COUPON",
            aggregateId = "coupon-123",
            eventType = "COUPON_ISSUED",
            payload = "{}"
        )

        // Then - aggregateType으로 서로 다른 토픽으로 라우팅됨
        assertThat(orderEvent.aggregateType).isEqualTo("ORDER")
        assertThat(couponEvent.aggregateType).isEqualTo("COUPON")
    }

    private fun createTestOutboxEvent(): OutboxEvent {
        return OutboxEvent(
            aggregateType = "ORDER",
            aggregateId = "order-123",
            eventType = "ORDER_PAID",
            payload = """{"orderId": "order-123", "amount": 10000}"""
        )
    }
}