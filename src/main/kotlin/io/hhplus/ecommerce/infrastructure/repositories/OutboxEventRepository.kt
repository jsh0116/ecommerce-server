package io.hhplus.ecommerce.infrastructure.repositories

import io.hhplus.ecommerce.domain.OutboxEvent
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * Outbox Event Repository
 *
 * CDC(Change Data Capture) 방식에서는 복잡한 상태 관리 쿼리가 필요 없습니다.
 * Debezium이 INSERT를 감지하여 자동으로 Kafka로 발행하므로,
 * 애플리케이션은 단순히 저장(save)만 하면 됩니다.
 *
 * 비즈니스 트랜잭션 내에서:
 * 1. 비즈니스 데이터 저장 (예: Order, Payment)
 * 2. OutboxEvent 저장
 * 3. 트랜잭션 커밋
 *
 * 커밋 후 Debezium이 자동으로:
 * - MySQL Binlog를 감시
 * - outbox_events 테이블의 INSERT 감지
 * - SMT를 통해 aggregateId를 Key로, payload를 Value로 변환
 * - Kafka Topic으로 발행 (Topic: outbox.event.{aggregateType})
 */
@Repository
interface OutboxEventRepository : JpaRepository<OutboxEvent, Long>