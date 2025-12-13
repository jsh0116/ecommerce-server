package io.hhplus.ecommerce.infrastructure.persistence.repository

import io.hhplus.ecommerce.infrastructure.persistence.entity.IdempotencyKeyJpaEntity
import io.hhplus.ecommerce.infrastructure.persistence.entity.IdempotencyStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime
import java.util.*

/**
 * 멱등성 키 Repository
 */
interface IdempotencyKeyJpaRepository : JpaRepository<IdempotencyKeyJpaEntity, Long> {

    /**
     * 멱등성 키로 조회
     */
    fun findByIdempotencyKey(idempotencyKey: String): Optional<IdempotencyKeyJpaEntity>

    /**
     * 좀비 요청 조회 (1시간 이상 PROCESSING 상태)
     *
     * 타임아웃 시나리오:
     * - 클라이언트는 타임아웃으로 실패했다고 판단
     * - 서버는 여전히 PROCESSING 상태로 남아있음
     * - 이런 좀비 요청을 주기적으로 FAILED 처리
     */
    @Query("""
        SELECT i FROM IdempotencyKeyJpaEntity i
        WHERE i.status = 'PROCESSING'
        AND i.updatedAt < :before
        ORDER BY i.createdAt ASC
    """)
    fun findZombieRequests(before: LocalDateTime): List<IdempotencyKeyJpaEntity>

    /**
     * TTL 초과한 멱등성 키 조회 (24시간 경과)
     *
     * 정리 대상:
     * - COMPLETED 상태로 24시간 경과
     * - FAILED 상태로 24시간 경과
     */
    @Query("""
        SELECT i FROM IdempotencyKeyJpaEntity i
        WHERE i.status IN ('COMPLETED', 'FAILED')
        AND i.createdAt < :before
        ORDER BY i.createdAt ASC
    """)
    fun findExpiredKeys(before: LocalDateTime): List<IdempotencyKeyJpaEntity>

    /**
     * 상태별 개수 조회 (모니터링용)
     */
    fun countByStatus(status: IdempotencyStatus): Long

    /**
     * 사용자별 멱등성 키 조회 (디버깅용)
     */
    fun findByUserIdOrderByCreatedAtDesc(userId: Long): List<IdempotencyKeyJpaEntity>
}
