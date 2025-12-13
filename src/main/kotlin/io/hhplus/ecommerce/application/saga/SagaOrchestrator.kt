package io.hhplus.ecommerce.application.saga

import java.time.LocalDateTime

/**
 * SAGA 패턴 Orchestrator 인터페이스
 *
 * 분산 트랜잭션을 여러 로컬 트랜잭션으로 분해하고,
 * 실패 시 보상 트랜잭션을 실행하는 책임을 가집니다.
 */
interface SagaOrchestrator<T, R> {
    /**
     * SAGA 실행
     *
     * @param request SAGA 요청 데이터
     * @return SAGA 실행 결과
     * @throws SagaExecutionException SAGA 실행 중 복구 불가능한 오류 발생 시
     */
    fun execute(request: T): R
}

/**
 * SAGA 실행 예외
 */
class SagaExecutionException(
    message: String,
    val sagaId: String,
    val failedStep: SagaStep,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * SAGA 단계
 */
enum class SagaStep {
    ORDER_CREATE,           // 주문 생성
    USER_BALANCE_DEDUCT,    // 사용자 잔액 차감
    INVENTORY_CONFIRM,      // 재고 확정
    COUPON_USE,             // 쿠폰 사용
    ORDER_COMPLETE          // 주문 완료
}

/**
 * SAGA 상태
 */
enum class SagaStatus {
    RUNNING,        // 진행 중
    COMPLETED,      // 성공 완료
    COMPENSATING,   // 보상 트랜잭션 실행 중
    FAILED,         // 실패 (보상 완료)
    STUCK           // 보상 실패 (수동 처리 필요)
}

/**
 * SAGA 인스턴스 (상태 저장용)
 */
data class SagaInstance(
    val sagaId: String,
    val orderId: Long,
    var currentStep: SagaStep? = null,
    var status: SagaStatus = SagaStatus.RUNNING,
    val completedSteps: MutableList<SagaStep> = mutableListOf(),
    val createdAt: LocalDateTime = LocalDateTime.now(),
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    fun addCompletedStep(step: SagaStep) {
        completedSteps.add(step)
        currentStep = step
        updatedAt = LocalDateTime.now()
    }

    fun markAsCompensating() {
        status = SagaStatus.COMPENSATING
        updatedAt = LocalDateTime.now()
    }

    fun markAsCompleted() {
        status = SagaStatus.COMPLETED
        updatedAt = LocalDateTime.now()
    }

    fun markAsFailed() {
        status = SagaStatus.FAILED
        updatedAt = LocalDateTime.now()
    }

    fun markAsStuck() {
        status = SagaStatus.STUCK
        updatedAt = LocalDateTime.now()
    }
}
