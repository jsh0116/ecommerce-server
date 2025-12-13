package io.hhplus.ecommerce.application.saga

import io.hhplus.ecommerce.infrastructure.persistence.entity.SagaInstanceJpaEntity
import io.hhplus.ecommerce.infrastructure.persistence.entity.SagaStatus
import io.hhplus.ecommerce.infrastructure.persistence.repository.SagaInstanceJpaRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * SAGA 복구 서비스
 *
 * 피드백 반영:
 * 1. 이벤트 흐름 추적: completedSteps로 어디까지 실행되었는지 파악
 * 2. 오류 파악: errorMessage와 currentStep으로 실패 지점 확인
 * 3. 자동 재조정: 실패한 SAGA를 주기적으로 자동 재시도
 *
 * Orchestrator SPOF 해결:
 * - SAGA 상태를 DB에 영속화
 * - Orchestrator 재시작 시 미완료 SAGA 자동 복구
 */
@Service
class SagaRecoveryService(
    private val sagaRepository: SagaInstanceJpaRepository,
    private val paymentSagaOrchestrator: PaymentSagaOrchestrator
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 실패한 SAGA 자동 복구 (5분마다 실행)
     *
     * 피드백: "자동으로 재조정하는 부분까지 고민"
     */
    @Scheduled(fixedDelay = 300000) // 5분
    @Transactional
    fun recoverFailedSagas(): RecoveryResult {
        logger.info("[SAGA Recovery] 실패한 SAGA 복구 작업 시작")

        // 5분 이상 지난 실패 SAGA 조회
        val failedSagas = sagaRepository.findRetryableSagas(
            LocalDateTime.now().minusMinutes(5)
        )

        if (failedSagas.isEmpty()) {
            logger.info("[SAGA Recovery] 복구할 SAGA 없음")
            return RecoveryResult(0, 0, 0)
        }

        logger.info("[SAGA Recovery] 복구 대상 SAGA: ${failedSagas.size}개")

        var successCount = 0
        var failCount = 0

        failedSagas.forEach { saga ->
            try {
                logger.info(
                    "[SAGA Recovery] 재시도: sagaId=${saga.sagaId}, " +
                    "orderId=${saga.orderId}, " +
                    "retryCount=${saga.retryCount}/${saga.maxRetryCount}, " +
                    "failedAt=${saga.currentStep}"
                )

                // 재시도 횟수 증가
                saga.incrementRetry()
                sagaRepository.save(saga)

                // SAGA 재실행
                val response = paymentSagaOrchestrator.execute(
                    PaymentSagaRequest(
                        orderId = saga.orderId,
                        userId = saga.userId
                    )
                )

                if (response.status == "SUCCESS") {
                    saga.markAsCompleted()
                    sagaRepository.save(saga)
                    successCount++
                    logger.info("[SAGA Recovery] 복구 성공: sagaId=${saga.sagaId}")
                } else {
                    saga.markAsFailed("재시도 실패: ${response.message}")
                    sagaRepository.save(saga)
                    failCount++
                }

            } catch (e: Exception) {
                logger.error("[SAGA Recovery] 복구 실패: sagaId=${saga.sagaId}", e)
                saga.markAsFailed("재시도 중 예외 발생: ${e.message}")
                sagaRepository.save(saga)
                failCount++

                // 최대 재시도 횟수 초과 시 STUCK 처리
                if (!saga.canRetry()) {
                    saga.markAsStuck("최대 재시도 횟수 초과")
                    sagaRepository.save(saga)
                    logger.warn("[SAGA Recovery] STUCK 처리: sagaId=${saga.sagaId}")
                }
            }
        }

        logger.info(
            "[SAGA Recovery] 복구 완료: 총 ${failedSagas.size}개, " +
            "성공 $successCount, 실패 $failCount"
        )

        return RecoveryResult(
            retriedCount = failedSagas.size,
            successCount = successCount,
            failCount = failCount
        )
    }

    /**
     * 재시도 횟수 초과한 SAGA를 STUCK 처리
     */
    @Transactional
    fun markExceededSagasAsStuck() {
        val exceededSagas = sagaRepository.findByStatus(SagaStatus.FAILED)
            .filter { !it.canRetry() }

        exceededSagas.forEach { saga ->
            saga.markAsStuck("최대 재시도 횟수(${saga.maxRetryCount}) 초과")
            sagaRepository.save(saga)
            logger.warn("[SAGA Recovery] STUCK 처리: sagaId=${saga.sagaId}")
        }
    }

    /**
     * SAGA 진행 상황 조회
     *
     * 피드백: "어디까지 실행되었고 어떤 부분에서 오류 발생했는지"
     */
    fun getSagaProgress(orderId: Long): SagaProgress? {
        val sagas = sagaRepository.findByOrderId(orderId)
        if (sagas.isEmpty()) return null

        // 가장 최근 SAGA
        val saga = sagas.maxByOrNull { it.createdAt } ?: return null

        return SagaProgress(
            sagaId = saga.sagaId,
            orderId = saga.orderId,
            status = saga.status,
            currentStep = saga.currentStep,
            completedSteps = saga.getCompletedSteps(),
            errorMessage = saga.errorMessage,
            retryCount = saga.retryCount,
            maxRetryCount = saga.maxRetryCount,
            createdAt = saga.createdAt,
            updatedAt = saga.updatedAt
        )
    }

    /**
     * 중단된 SAGA 조회 (수동 처리 필요)
     */
    fun getStuckSagas(): List<SagaInstanceJpaEntity> {
        return sagaRepository.findStuckSagas(
            LocalDateTime.now().minusHours(1) // 1시간 이상 중단
        )
    }

    /**
     * 오래된 완료/실패 SAGA 정리 (매일 자정 실행)
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    fun cleanupOldSagas() {
        val oldSagas = sagaRepository.findOldSagasForCleanup(
            LocalDateTime.now().minusDays(30) // 30일 이상 된 완료/실패 SAGA
        )

        if (oldSagas.isNotEmpty()) {
            sagaRepository.deleteAll(oldSagas)
            logger.info("[SAGA Cleanup] 오래된 SAGA ${oldSagas.size}개 삭제")
        }
    }
}

/**
 * 복구 결과
 */
data class RecoveryResult(
    val retriedCount: Int,
    val successCount: Int,
    val failCount: Int
)

/**
 * SAGA 진행 상황
 */
data class SagaProgress(
    val sagaId: String,
    val orderId: Long,
    val status: SagaStatus,
    val currentStep: String?,
    val completedSteps: List<String>,
    val errorMessage: String?,
    val retryCount: Int,
    val maxRetryCount: Int,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)
