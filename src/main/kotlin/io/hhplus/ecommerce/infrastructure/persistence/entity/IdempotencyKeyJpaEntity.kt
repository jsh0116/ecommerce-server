package io.hhplus.ecommerce.infrastructure.persistence.entity

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * 멱등성 키 엔티티
 *
 * Phase 2: 멱등성 보장 개선
 *
 * 피드백 반영:
 * - "타임아웃 + 오류발생이 동시에 나면? → 어떤 시점에 idempotencyKey를 정리해주는지"
 *
 * 해결 방안:
 * 1. TTL 기반 자동 정리: 24시간 후 자동 삭제
 * 2. 좀비 요청 감지: 1시간 이상 PROCESSING 상태면 FAILED로 전환
 * 3. 상태별 처리:
 *    - PROCESSING: 요청 처리 중 (1시간 초과 시 좀비로 간주)
 *    - COMPLETED: 성공 완료 (24시간 후 정리)
 *    - FAILED: 실패 완료 (24시간 후 정리)
 */
@Entity
@Table(
    name = "idempotency_keys",
    indexes = [
        Index(name = "idx_idempotency_key", columnList = "idempotencyKey", unique = true),
        Index(name = "idx_created_at", columnList = "createdAt"),
        Index(name = "idx_status_updated_at", columnList = "status,updatedAt")
    ]
)
class IdempotencyKeyJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    /**
     * 멱등성 키 (고유)
     * 형식: "order-{orderId}-payment-{userId}-{timestamp}"
     */
    @Column(nullable = false, unique = true, length = 200)
    val idempotencyKey: String,

    /**
     * 요청 타입 (order-payment, balance-deduct, inventory-reserve 등)
     */
    @Column(nullable = false, length = 50)
    val requestType: String,

    /**
     * 사용자 ID
     */
    @Column(nullable = false)
    val userId: Long,

    /**
     * 관련 엔티티 ID (주문 ID, 상품 ID 등)
     */
    @Column(nullable = true)
    val entityId: Long? = null,

    /**
     * 상태
     * - PROCESSING: 요청 처리 중
     * - COMPLETED: 성공 완료
     * - FAILED: 실패 완료
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: IdempotencyStatus = IdempotencyStatus.PROCESSING,

    /**
     * 응답 데이터 (JSON 형태로 저장)
     * 동일한 요청 재시도 시 이전 응답 반환
     */
    @Column(columnDefinition = "TEXT")
    var responseData: String? = null,

    /**
     * 오류 메시지 (실패 시)
     */
    @Column(columnDefinition = "TEXT")
    var errorMessage: String? = null,

    /**
     * 생성 시각
     */
    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    /**
     * 수정 시각
     */
    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    /**
     * 완료 시각
     */
    @Column(nullable = true)
    var completedAt: LocalDateTime? = null
) {
    /**
     * 성공 완료 처리
     */
    fun markAsCompleted(responseData: String) {
        this.status = IdempotencyStatus.COMPLETED
        this.responseData = responseData
        this.updatedAt = LocalDateTime.now()
        this.completedAt = LocalDateTime.now()
    }

    /**
     * 실패 처리
     */
    fun markAsFailed(errorMessage: String) {
        this.status = IdempotencyStatus.FAILED
        this.errorMessage = errorMessage
        this.updatedAt = LocalDateTime.now()
        this.completedAt = LocalDateTime.now()
    }

    /**
     * 좀비 요청인지 확인 (1시간 이상 PROCESSING 상태)
     */
    fun isZombie(): Boolean {
        if (status != IdempotencyStatus.PROCESSING) return false
        return updatedAt.isBefore(LocalDateTime.now().minusHours(1))
    }

    /**
     * TTL 초과 확인 (24시간 경과)
     */
    fun isExpired(): Boolean {
        return createdAt.isBefore(LocalDateTime.now().minusHours(24))
    }
}

/**
 * 멱등성 키 상태
 */
enum class IdempotencyStatus {
    PROCESSING,  // 처리 중
    COMPLETED,   // 성공 완료
    FAILED       // 실패
}
