package io.hhplus.ecommerce.domain

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * Transactional Outbox Pattern을 위한 이벤트 엔티티
 *
 * CDC(Change Data Capture) 방식으로 Debezium이 이 테이블을 감시하여
 * INSERT 발생 시 자동으로 Kafka로 발행합니다.
 *
 * - aggregateType: Kafka Topic 라우팅에 사용 (예: ORDER, COUPON)
 * - aggregateId: Kafka Message Key로 사용하여 파티션 내 순서 보장 (예: userId, orderId)
 * - eventType: 이벤트 세부 타입 (예: ORDER_PAID, COUPON_ISSUED)
 * - payload: 실제 전송할 데이터 (JSON 문자열)
 * - createdAt: 이벤트 생성 시간
 *
 * CDC 방식에서는 status, retryCount 등의 상태 관리 필드가 필요 없습니다.
 * Debezium이 offset 관리와 재시도를 자체적으로 처리하기 때문입니다.
 */
@Entity
@Table(
    name = "outbox_events",
    indexes = [
        Index(name = "idx_outbox_aggregate", columnList = "aggregateType, aggregateId"),
        Index(name = "idx_outbox_created_at", columnList = "createdAt")
    ]
)
data class OutboxEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    /**
     * 집계 타입 (예: ORDER, COUPON)
     * Debezium SMT에서 Topic 라우팅에 사용됩니다.
     * 예: outbox.event.ORDER, outbox.event.COUPON
     */
    @Column(nullable = false, length = 100)
    val aggregateType: String,

    /**
     * 집계 ID (예: user-123, order-456)
     * Kafka Message Key로 사용되어 동일한 aggregateId는 같은 파티션으로 전송됩니다.
     * 이를 통해 특정 사용자/주문의 이벤트 순서가 보장됩니다.
     */
    @Column(nullable = false, length = 255)
    val aggregateId: String,

    /**
     * 이벤트 타입 (예: ORDER_PAID, COUPON_ISSUED)
     */
    @Column(nullable = false, length = 100)
    val eventType: String,

    /**
     * 실제 전송할 데이터 (JSON 문자열)
     * Debezium SMT에서 이 값만 추출하여 Kafka Message Value로 전송됩니다.
     */
    @Column(columnDefinition = "TEXT", nullable = false)
    val payload: String,

    /**
     * 이벤트 생성 시간
     * 오래된 이벤트 정리나 디버깅에 활용됩니다.
     */
    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OutboxEvent) return false
        return id != 0L && id == other.id
    }

    override fun hashCode(): Int = javaClass.hashCode()

    override fun toString(): String {
        return "OutboxEvent(id=$id, aggregateType='$aggregateType', aggregateId='$aggregateId', " +
                "eventType='$eventType', createdAt=$createdAt)"
    }
}