package io.hhplus.ecommerce.infrastructure.persistence.entity

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * SAGA 인스턴스 영속화 엔티티
 *
 * SAGA의 실행 상태를 DB에 저장하여:
 * 1. Orchestrator 재시작 시 복구 가능
 * 2. 실패한 SAGA 자동 재시도 가능
 * 3. 모니터링 및 추적 가능
 */
@Entity
@Table(
    name = "saga_instances",
    indexes = [
        Index(name = "idx_saga_status", columnList = "status"),
        Index(name = "idx_saga_order_id", columnList = "order_id"),
        Index(name = "idx_saga_created_at", columnList = "created_at")
    ]
)
class SagaInstanceJpaEntity(
    @Id
    @Column(length = 100)
    val sagaId: String,

    @Column(name = "saga_type", nullable = false, length = 50)
    val sagaType: String = "PAYMENT",

    @Column(name = "order_id", nullable = false)
    val orderId: Long,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: SagaStatus = SagaStatus.PENDING,

    @Column(name = "current_step", length = 50)
    var currentStep: String? = null,

    @Column(name = "completed_steps", columnDefinition = "TEXT")
    var completedStepsJson: String = "[]",

    @Column(name = "compensation_steps", columnDefinition = "TEXT")
    var compensationStepsJson: String = "[]",

    @Column(name = "error_message", columnDefinition = "TEXT")
    var errorMessage: String? = null,

    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0,

    @Column(name = "max_retry_count", nullable = false)
    val maxRetryCount: Int = 3,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "completed_at")
    var completedAt: LocalDateTime? = null
) {
    /**
     * SAGA를 진행 중 상태로 변경
     */
    fun markAsRunning(currentStep: String) {
        this.status = SagaStatus.RUNNING
        this.currentStep = currentStep
        this.updatedAt = LocalDateTime.now()
    }

    /**
     * SAGA를 보상 중 상태로 변경
     */
    fun markAsCompensating(errorMessage: String) {
        this.status = SagaStatus.COMPENSATING
        this.errorMessage = errorMessage
        this.updatedAt = LocalDateTime.now()
    }

    /**
     * SAGA를 완료 상태로 변경
     */
    fun markAsCompleted() {
        this.status = SagaStatus.COMPLETED
        this.completedAt = LocalDateTime.now()
        this.updatedAt = LocalDateTime.now()
    }

    /**
     * SAGA를 실패 상태로 변경
     */
    fun markAsFailed(errorMessage: String) {
        this.status = SagaStatus.FAILED
        this.errorMessage = errorMessage
        this.updatedAt = LocalDateTime.now()
    }

    /**
     * SAGA를 중단 상태로 변경 (수동 처리 필요)
     */
    fun markAsStuck(errorMessage: String) {
        this.status = SagaStatus.STUCK
        this.errorMessage = errorMessage
        this.updatedAt = LocalDateTime.now()
    }

    /**
     * 재시도 가능 여부 확인
     */
    fun canRetry(): Boolean {
        return retryCount < maxRetryCount &&
               (status == SagaStatus.FAILED || status == SagaStatus.COMPENSATING)
    }

    /**
     * 재시도 횟수 증가
     */
    fun incrementRetry() {
        retryCount++
        updatedAt = LocalDateTime.now()
    }

    /**
     * 완료된 단계 추가
     */
    fun addCompletedStep(step: String) {
        val steps = completedStepsJson
            .removeSurrounding("[", "]")
            .split(",")
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotBlank() }
            .toMutableList()

        if (!steps.contains(step)) {
            steps.add(step)
            completedStepsJson = steps.joinToString(",") { "\"$it\"" }
                .let { "[$it]" }
        }
        updatedAt = LocalDateTime.now()
    }

    /**
     * 보상 단계 추가
     */
    fun addCompensationStep(step: String) {
        val steps = compensationStepsJson
            .removeSurrounding("[", "]")
            .split(",")
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotBlank() }
            .toMutableList()

        if (!steps.contains(step)) {
            steps.add(step)
            compensationStepsJson = steps.joinToString(",") { "\"$it\"" }
                .let { "[$it]" }
        }
        updatedAt = LocalDateTime.now()
    }

    /**
     * 완료된 단계 목록 가져오기
     */
    fun getCompletedSteps(): List<String> {
        return completedStepsJson
            .removeSurrounding("[", "]")
            .split(",")
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotBlank() }
    }
}

/**
 * SAGA 상태
 */
enum class SagaStatus {
    PENDING,        // 대기 중
    RUNNING,        // 실행 중
    COMPENSATING,   // 보상 트랜잭션 실행 중
    COMPLETED,      // 성공 완료
    FAILED,         // 실패 (재시도 가능)
    STUCK           // 중단 (수동 처리 필요)
}
