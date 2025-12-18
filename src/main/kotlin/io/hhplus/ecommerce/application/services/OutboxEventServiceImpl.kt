package io.hhplus.ecommerce.application.services

import com.fasterxml.jackson.databind.ObjectMapper
import io.hhplus.ecommerce.domain.OutboxEvent
import io.hhplus.ecommerce.infrastructure.repositories.OutboxEventRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * OutboxEventService 구현체
 *
 * CDC 방식에서는 단순히 OutboxEvent를 저장하기만 하면 됩니다.
 * Debezium이 MySQL Binlog를 감시하여 자동으로 Kafka로 발행합니다.
 */
@Service
class OutboxEventServiceImpl(
    private val outboxEventRepository: OutboxEventRepository,
    private val objectMapper: ObjectMapper
) : OutboxEventService {

    /**
     * Outbox 이벤트를 발행합니다.
     *
     * @Transactional을 사용하지 않는 이유:
     * 이 메서드는 비즈니스 로직과 함께 호출되므로,
     * 호출하는 쪽에서 @Transactional을 관리해야 합니다.
     */
    override fun publish(
        aggregateType: String,
        aggregateId: String,
        eventType: String,
        payload: Any
    ): OutboxEvent {
        val payloadJson = when (payload) {
            is String -> payload
            else -> objectMapper.writeValueAsString(payload)
        }

        val outboxEvent = OutboxEvent(
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            eventType = eventType,
            payload = payloadJson
        )

        return outboxEventRepository.save(outboxEvent)
    }
}
