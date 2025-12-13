package io.hhplus.ecommerce.infrastructure.persistence.repository

import io.hhplus.ecommerce.infrastructure.persistence.entity.SagaInstanceJpaEntity
import io.hhplus.ecommerce.infrastructure.persistence.entity.SagaStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime

/**
 * SAGA 인스턴스 Repository
 */
interface SagaInstanceJpaRepository : JpaRepository<SagaInstanceJpaEntity, String> {

    /**
     * 주문 ID로 SAGA 조회
     */
    fun findByOrderId(orderId: Long): List<SagaInstanceJpaEntity>

    /**
     * 상태별 SAGA 조회
     */
    fun findByStatus(status: SagaStatus): List<SagaInstanceJpaEntity>

    /**
     * 상태 목록으로 SAGA 조회
     */
    fun findByStatusIn(statuses: List<SagaStatus>): List<SagaInstanceJpaEntity>

    /**
     * 재시도 가능한 SAGA 조회
     * (FAILED 상태이고 재시도 횟수가 최대치 미만)
     */
    @Query("""
        SELECT s FROM SagaInstanceJpaEntity s
        WHERE s.status = 'FAILED'
        AND s.retryCount < s.maxRetryCount
        AND s.updatedAt < :before
        ORDER BY s.createdAt ASC
    """)
    fun findRetryableSagas(before: LocalDateTime): List<SagaInstanceJpaEntity>

    /**
     * 중단된 SAGA 조회 (수동 처리 필요)
     */
    @Query("""
        SELECT s FROM SagaInstanceJpaEntity s
        WHERE s.status IN ('STUCK', 'COMPENSATING')
        AND s.updatedAt < :before
        ORDER BY s.createdAt ASC
    """)
    fun findStuckSagas(before: LocalDateTime): List<SagaInstanceJpaEntity>

    /**
     * 오래된 완료/실패 SAGA 정리
     */
    @Query("""
        SELECT s FROM SagaInstanceJpaEntity s
        WHERE s.status IN ('COMPLETED', 'FAILED')
        AND s.retryCount >= s.maxRetryCount
        AND s.completedAt < :before
        ORDER BY s.completedAt ASC
    """)
    fun findOldSagasForCleanup(before: LocalDateTime): List<SagaInstanceJpaEntity>
}
