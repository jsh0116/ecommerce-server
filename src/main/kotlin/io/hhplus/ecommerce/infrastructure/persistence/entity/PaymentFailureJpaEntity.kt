package io.hhplus.ecommerce.infrastructure.persistence.entity

import jakarta.persistence.*
import java.time.LocalDateTime

enum class CompensationStatusJpa {
    PENDING,
    COMPENSATED,
    FAILED
}

/**
 * 결제 실패 JPA Entity
 *
 * 결제 실패 시 자동으로 기록되며, 보상 트랜잭션(환불, 재고 복구 등)을 추적합니다.
 */
@Entity
@Table(
    name = "payment_failures",
    indexes = [
        Index(name = "idx_payment_failures_order_id", columnList = "order_id"),
        Index(name = "idx_payment_failures_payment_id", columnList = "payment_id"),
        Index(name = "idx_payment_failures_status", columnList = "compensation_status"),
        Index(name = "idx_payment_failures_created_at", columnList = "created_at")
    ]
)
class PaymentFailureJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String = "",

    @Column(nullable = false)
    var orderId: Long,

    @Column(nullable = false)
    var paymentId: Long,

    @Column(nullable = false, length = 255)
    var reason: String,

    @Column(length = 100)
    var pgCode: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var compensationStatus: CompensationStatusJpa = CompensationStatusJpa.PENDING,

    @Column(columnDefinition = "TEXT")
    var compensationReason: String? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    /**
     * 보상 완료 처리
     */
    fun markAsCompensated(reason: String = "") {
        require(compensationStatus == CompensationStatusJpa.PENDING) { "이미 처리된 보상입니다" }
        compensationStatus = CompensationStatusJpa.COMPENSATED
        compensationReason = reason
        updatedAt = LocalDateTime.now()
    }

    /**
     * 보상 실패 처리
     */
    fun markAsCompensationFailed(reason: String = "") {
        require(compensationStatus == CompensationStatusJpa.PENDING) { "이미 처리된 보상입니다" }
        compensationStatus = CompensationStatusJpa.FAILED
        compensationReason = reason
        updatedAt = LocalDateTime.now()
    }

    /**
     * 보상이 필요한지 확인
     */
    fun needsCompensation(): Boolean = compensationStatus == CompensationStatusJpa.PENDING
}
