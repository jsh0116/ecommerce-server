package io.hhplus.ecommerce.application.services

import io.hhplus.ecommerce.infrastructure.persistence.entity.IdempotencyKeyJpaEntity
import io.hhplus.ecommerce.infrastructure.persistence.entity.IdempotencyStatus
import io.hhplus.ecommerce.infrastructure.persistence.repository.IdempotencyKeyJpaRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 멱등성 키 서비스
 *
 * Phase 2: 멱등성 보장 개선
 *
 * 주요 기능:
 * 1. 멱등성 키 생성 및 검증
 * 2. 중복 요청 감지 및 이전 응답 반환
 * 3. 좀비 요청 자동 정리 (1시간 초과 PROCESSING)
 * 4. TTL 기반 자동 정리 (24시간 경과)
 */
@Service
class IdempotencyService(
    private val idempotencyKeyRepository: IdempotencyKeyJpaRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 멱등성 키 획득
     *
     * 반환값:
     * - null: 새로운 요청 (처리 진행)
     * - IdempotencyKeyJpaEntity: 기존 요청 존재
     *   - PROCESSING: 처리 중 (동시 요청, 대기 필요)
     *   - COMPLETED: 이미 완료 (이전 응답 반환)
     *   - FAILED: 이미 실패 (재시도 가능)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun acquireKey(
        idempotencyKey: String,
        requestType: String,
        userId: Long,
        entityId: Long? = null
    ): IdempotencyResult {
        // 기존 키 조회
        val existing = idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey)

        if (existing.isPresent) {
            val entity = existing.get()

            return when (entity.status) {
                IdempotencyStatus.PROCESSING -> {
                    // 좀비 요청인지 확인
                    if (entity.isZombie()) {
                        logger.warn("[Idempotency] 좀비 요청 감지: key=$idempotencyKey, updatedAt=${entity.updatedAt}")
                        entity.markAsFailed("타임아웃으로 좀비 요청 처리됨")
                        idempotencyKeyRepository.save(entity)
                        IdempotencyResult.Failed("이전 요청이 타임아웃되었습니다. 재시도해 주세요.")
                    } else {
                        logger.info("[Idempotency] 처리 중인 요청: key=$idempotencyKey")
                        IdempotencyResult.Processing("동일한 요청이 처리 중입니다. 잠시 후 다시 시도해 주세요.")
                    }
                }
                IdempotencyStatus.COMPLETED -> {
                    logger.info("[Idempotency] 완료된 요청: key=$idempotencyKey")
                    IdempotencyResult.AlreadyCompleted(entity.responseData ?: "{}")
                }
                IdempotencyStatus.FAILED -> {
                    logger.info("[Idempotency] 실패한 요청 재시도: key=$idempotencyKey")
                    // 실패한 요청은 재시도 가능하도록 새 키 생성
                    IdempotencyResult.NewRequest(createNewKey(idempotencyKey, requestType, userId, entityId))
                }
            }
        }

        // 새로운 키 생성
        return IdempotencyResult.NewRequest(createNewKey(idempotencyKey, requestType, userId, entityId))
    }

    /**
     * 새 멱등성 키 생성
     */
    private fun createNewKey(
        idempotencyKey: String,
        requestType: String,
        userId: Long,
        entityId: Long?
    ): IdempotencyKeyJpaEntity {
        try {
            val entity = IdempotencyKeyJpaEntity(
                idempotencyKey = idempotencyKey,
                requestType = requestType,
                userId = userId,
                entityId = entityId,
                status = IdempotencyStatus.PROCESSING
            )
            return idempotencyKeyRepository.save(entity)
        } catch (e: DataIntegrityViolationException) {
            // 동시 요청으로 인한 중복 키 생성 시도
            logger.warn("[Idempotency] 동시 요청 감지: key=$idempotencyKey", e)
            val existing = idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey)
            if (existing.isPresent) {
                return existing.get()
            }
            throw e
        }
    }

    /**
     * 성공 완료 처리
     */
    @Transactional
    fun markAsCompleted(idempotencyKey: String, responseData: String) {
        val entity = idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey)
            .orElseThrow { IllegalStateException("멱등성 키를 찾을 수 없습니다: $idempotencyKey") }

        entity.markAsCompleted(responseData)
        idempotencyKeyRepository.save(entity)

        logger.info("[Idempotency] 요청 완료: key=$idempotencyKey")
    }

    /**
     * 실패 처리
     */
    @Transactional
    fun markAsFailed(idempotencyKey: String, errorMessage: String) {
        val entity = idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey)
            .orElseThrow { IllegalStateException("멱등성 키를 찾을 수 없습니다: $idempotencyKey") }

        entity.markAsFailed(errorMessage)
        idempotencyKeyRepository.save(entity)

        logger.info("[Idempotency] 요청 실패: key=$idempotencyKey, error=$errorMessage")
    }

    /**
     * 좀비 요청 자동 정리 (10분마다 실행)
     *
     * 타임아웃 + 오류 동시 발생 시나리오 처리:
     * - 클라이언트: 타임아웃으로 실패 판단
     * - 서버: PROCESSING 상태로 남아있음
     * - 1시간 이상 PROCESSING 상태면 자동으로 FAILED 처리
     */
    @Scheduled(fixedDelay = 600000) // 10분
    @Transactional
    fun cleanupZombieRequests() {
        val zombies = idempotencyKeyRepository.findZombieRequests(
            LocalDateTime.now().minusHours(1)
        )

        if (zombies.isEmpty()) {
            return
        }

        logger.info("[Idempotency Cleanup] 좀비 요청 정리 시작: ${zombies.size}개")

        zombies.forEach { zombie ->
            zombie.markAsFailed("타임아웃으로 자동 정리됨")
            idempotencyKeyRepository.save(zombie)
            logger.warn("[Idempotency Cleanup] 좀비 요청 정리: key=${zombie.idempotencyKey}, created=${zombie.createdAt}")
        }

        logger.info("[Idempotency Cleanup] 좀비 요청 정리 완료: ${zombies.size}개")
    }

    /**
     * TTL 기반 자동 정리 (매일 자정 실행)
     *
     * 24시간 경과한 COMPLETED/FAILED 멱등성 키 삭제
     */
    @Scheduled(cron = "0 0 0 * * *") // 매일 자정
    @Transactional
    fun cleanupExpiredKeys() {
        val expiredKeys = idempotencyKeyRepository.findExpiredKeys(
            LocalDateTime.now().minusHours(24)
        )

        if (expiredKeys.isEmpty()) {
            logger.info("[Idempotency Cleanup] 정리할 만료 키 없음")
            return
        }

        logger.info("[Idempotency Cleanup] 만료 키 정리 시작: ${expiredKeys.size}개")

        idempotencyKeyRepository.deleteAll(expiredKeys)

        logger.info("[Idempotency Cleanup] 만료 키 정리 완료: ${expiredKeys.size}개")
    }

    /**
     * 멱등성 키 상태 조회 (모니터링용)
     */
    fun getKeyStatus(idempotencyKey: String): IdempotencyKeyJpaEntity? {
        return idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey).orElse(null)
    }

    /**
     * 상태별 통계 조회 (모니터링용)
     */
    fun getStatistics(): Map<String, Long> {
        return mapOf(
            "PROCESSING" to idempotencyKeyRepository.countByStatus(IdempotencyStatus.PROCESSING),
            "COMPLETED" to idempotencyKeyRepository.countByStatus(IdempotencyStatus.COMPLETED),
            "FAILED" to idempotencyKeyRepository.countByStatus(IdempotencyStatus.FAILED)
        )
    }
}

/**
 * 멱등성 키 획득 결과
 */
sealed class IdempotencyResult {
    /**
     * 새로운 요청 (처리 진행)
     */
    data class NewRequest(val entity: IdempotencyKeyJpaEntity) : IdempotencyResult()

    /**
     * 처리 중 (동시 요청)
     */
    data class Processing(val message: String) : IdempotencyResult()

    /**
     * 이미 완료 (이전 응답 반환)
     */
    data class AlreadyCompleted(val responseData: String) : IdempotencyResult()

    /**
     * 실패 (재시도 가능)
     */
    data class Failed(val message: String) : IdempotencyResult()
}
