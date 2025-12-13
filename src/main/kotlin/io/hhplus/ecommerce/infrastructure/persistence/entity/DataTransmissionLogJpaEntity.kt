package io.hhplus.ecommerce.infrastructure.persistence.entity

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * 외부 데이터 전송 로그 엔티티
 *
 * 주문 결제 완료 후 외부 시스템으로 데이터를 전송한 이력을 추적합니다.
 * 비동기 작업의 상태를 확인할 수 있도록 상태 정보를 저장합니다.
 */
@Entity
@Table(
    name = "data_transmission_logs",
    indexes = [
        Index(name = "idx_order_id", columnList = "order_id"),
        Index(name = "idx_status", columnList = "status"),
        Index(name = "idx_created_at", columnList = "created_at")
    ]
)
class DataTransmissionLogJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Column(name = "order_id", nullable = false)
    val orderId: Long,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: TransmissionStatus = TransmissionStatus.PENDING,

    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0,

    @Column(name = "last_attempt_at")
    var lastAttemptAt: LocalDateTime? = null,

    @Column(name = "error_message", columnDefinition = "TEXT")
    var errorMessage: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "completed_at")
    var completedAt: LocalDateTime? = null
) {
    /**
     * 전송 성공 처리
     */
    fun markAsSuccess() {
        this.status = TransmissionStatus.SUCCESS
        this.completedAt = LocalDateTime.now()
        this.lastAttemptAt = LocalDateTime.now()
        this.errorMessage = null
    }

    /**
     * 전송 실패 처리
     */
    fun markAsFailed(errorMessage: String) {
        this.status = TransmissionStatus.FAILED
        this.lastAttemptAt = LocalDateTime.now()
        this.errorMessage = errorMessage
        this.retryCount++
    }

    /**
     * 재시도 대기 처리
     */
    fun markAsRetrying() {
        this.status = TransmissionStatus.RETRYING
        this.lastAttemptAt = LocalDateTime.now()
    }
}

/**
 * 외부 데이터 전송 상태
 */
enum class TransmissionStatus {
    /** 전송 대기 중 */
    PENDING,

    /** 전송 중 */
    SENDING,

    /** 전송 성공 */
    SUCCESS,

    /** 전송 실패 */
    FAILED,

    /** 재시도 대기 중 */
    RETRYING
}
