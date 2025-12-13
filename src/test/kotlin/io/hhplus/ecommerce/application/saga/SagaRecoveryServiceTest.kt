package io.hhplus.ecommerce.application.saga

import io.hhplus.ecommerce.infrastructure.persistence.entity.SagaInstanceJpaEntity
import io.hhplus.ecommerce.infrastructure.persistence.entity.SagaStatus
import io.hhplus.ecommerce.infrastructure.persistence.repository.SagaInstanceJpaRepository
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/**
 * SAGA 복구 서비스 테스트
 *
 * 피드백 반영:
 * - 이벤트 기반 흐름에서 어디까지 실행되었는지 추적
 * - 어떤 부분에서 오류 발생했는지 파악
 * - 자동 재조정(복구) 메커니즘
 */
@DisplayName("SAGA 복구 서비스 테스트")
class SagaRecoveryServiceTest {

    private lateinit var sagaRepository: SagaInstanceJpaRepository
    private lateinit var paymentSagaOrchestrator: PaymentSagaOrchestrator
    private lateinit var sagaRecoveryService: SagaRecoveryService

    @BeforeEach
    fun setUp() {
        sagaRepository = mockk(relaxed = true)
        paymentSagaOrchestrator = mockk(relaxed = true)
        sagaRecoveryService = SagaRecoveryService(
            sagaRepository,
            paymentSagaOrchestrator
        )
    }

    @Test
    @DisplayName("실패한 SAGA를 자동으로 재시도한다")
    fun shouldRetryFailedSagas() {
        // Given: 재시도 가능한 실패 SAGA
        val failedSaga = SagaInstanceJpaEntity(
            sagaId = "saga-1",
            orderId = 1L,
            userId = 100L,
            status = SagaStatus.FAILED,
            currentStep = "USER_BALANCE_DEDUCT",
            errorMessage = "잔액 부족",
            retryCount = 0,
            maxRetryCount = 3,
            updatedAt = LocalDateTime.now().minusMinutes(10)
        )

        every { sagaRepository.findRetryableSagas(any()) } returns listOf(failedSaga)
        every { sagaRepository.save(any()) } returnsArgument 0
        every { paymentSagaOrchestrator.execute(any()) } returns PaymentSagaResponse(
            sagaId = "saga-1",
            orderId = 1L,
            status = "SUCCESS",
            message = "성공"
        )

        // When: 복구 작업 실행
        val result = sagaRecoveryService.recoverFailedSagas()

        // Then: SAGA 재시도 실행
        verify(exactly = 1) {
            paymentSagaOrchestrator.execute(
                match { it.orderId == 1L && it.userId == 100L }
            )
        }

        verify(atLeast = 1) {
            sagaRepository.save(any())
        }

        assertThat(result.retriedCount).isEqualTo(1)
        assertThat(result.successCount).isEqualTo(1)
    }

    @Test
    @DisplayName("최대 재시도 횟수 초과 시 STUCK 상태로 변경한다")
    fun shouldMarkAsStuckWhenMaxRetryExceeded() {
        // Given: 재시도 횟수 초과
        val failedSaga = SagaInstanceJpaEntity(
            sagaId = "saga-2",
            orderId = 2L,
            userId = 200L,
            status = SagaStatus.FAILED,
            retryCount = 3,
            maxRetryCount = 3,
            updatedAt = LocalDateTime.now().minusMinutes(10)
        )

        every { sagaRepository.findRetryableSagas(any()) } returns emptyList()
        every { sagaRepository.findByStatus(SagaStatus.FAILED) } returns listOf(failedSaga)
        every { sagaRepository.save(any()) } returnsArgument 0

        // When: 정리 작업 실행
        sagaRecoveryService.markExceededSagasAsStuck()

        // Then: STUCK으로 변경
        verify {
            sagaRepository.save(
                match {
                    it.sagaId == "saga-2" &&
                    it.status == SagaStatus.STUCK
                }
            )
        }
    }

    @Test
    @DisplayName("SAGA 상태를 조회하여 진행 상황을 확인할 수 있다")
    fun shouldGetSagaProgress() {
        // Given: 실행 중인 SAGA
        val runningSaga = SagaInstanceJpaEntity(
            sagaId = "saga-3",
            orderId = 3L,
            userId = 300L,
            status = SagaStatus.RUNNING,
            currentStep = "INVENTORY_CONFIRM",
            completedStepsJson = "[\"ORDER_CREATE\",\"USER_BALANCE_DEDUCT\"]"
        )

        every { sagaRepository.findByOrderId(3L) } returns listOf(runningSaga)

        // When: 진행 상황 조회
        val progress = sagaRecoveryService.getSagaProgress(3L)

        // Then: 현재 상태와 완료된 단계 반환
        assertThat(progress).isNotNull
        assertThat(progress!!.status).isEqualTo(SagaStatus.RUNNING)
        assertThat(progress.currentStep).isEqualTo("INVENTORY_CONFIRM")
        assertThat(progress.completedSteps).containsExactly(
            "ORDER_CREATE",
            "USER_BALANCE_DEDUCT"
        )
    }

    @Test
    @DisplayName("중단된 SAGA 목록을 조회할 수 있다")
    fun shouldGetStuckSagas() {
        // Given: 중단된 SAGA들
        val stuckSagas = listOf(
            SagaInstanceJpaEntity(
                sagaId = "saga-4",
                orderId = 4L,
                userId = 400L,
                status = SagaStatus.STUCK,
                errorMessage = "보상 트랜잭션 실패"
            )
        )

        every { sagaRepository.findStuckSagas(any()) } returns stuckSagas

        // When: 중단된 SAGA 조회
        val result = sagaRecoveryService.getStuckSagas()

        // Then: 수동 처리가 필요한 SAGA 반환
        assertThat(result).hasSize(1)
        assertThat(result[0].sagaId).isEqualTo("saga-4")
        assertThat(result[0].status).isEqualTo(SagaStatus.STUCK)
    }
}
