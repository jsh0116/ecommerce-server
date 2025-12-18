package io.hhplus.ecommerce.application.services

import io.hhplus.ecommerce.domain.OutboxEvent

/**
 * Transactional Outbox Pattern을 위한 이벤트 발행 서비스
 *
 * CDC(Debezium) 방식에서는 이 서비스가 Outbox 테이블에 이벤트를 저장하면,
 * Debezium이 자동으로 Kafka로 발행합니다.
 *
 * 사용 예시:
 * ```
 * @Transactional
 * fun processOrder(order: Order) {
 *     // 1. 비즈니스 로직 수행
 *     orderRepository.save(order)
 *
 *     // 2. Outbox 이벤트 발행 (같은 트랜잭션 내)
 *     outboxEventService.publish(
 *         aggregateType = "ORDER",
 *         aggregateId = order.userId,
 *         eventType = "ORDER_PAID",
 *         payload = mapOf("orderId" to order.id, "amount" to order.amount)
 *     )
 *     // 3. 트랜잭션 커밋 시 order와 outbox_event가 함께 저장됨
 *     // 4. Debezium이 outbox_event INSERT를 감지하여 Kafka로 발행
 * }
 * ```
 */
interface OutboxEventService {

    /**
     * Outbox 이벤트를 발행합니다.
     *
     * @param aggregateType 집계 타입 (예: ORDER, COUPON) - Topic 라우팅에 사용
     * @param aggregateId 집계 ID (예: userId, orderId) - Kafka Message Key로 사용하여 순서 보장
     * @param eventType 이벤트 타입 (예: ORDER_PAID, COUPON_ISSUED)
     * @param payload 이벤트 데이터 (Map 또는 String) - Kafka Message Value로 전송
     * @return 저장된 OutboxEvent
     */
    fun publish(
        aggregateType: String,
        aggregateId: String,
        eventType: String,
        payload: Any
    ): OutboxEvent
}