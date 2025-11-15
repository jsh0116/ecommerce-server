package io.hhplus.ecommerce.infrastructure.persistence.entity

import io.hhplus.ecommerce.infrastructure.util.toUuid
import io.hhplus.ecommerce.infrastructure.util.uuidToLong
import jakarta.persistence.*
import java.time.LocalDateTime

enum class PaymentMethodJpa {
    CARD,
    BANK_TRANSFER,
    VIRTUAL_ACCOUNT
}

enum class PaymentStatusJpa {
    PENDING,
    APPROVED,
    FAILED,
    REFUNDED
}

/**
 * 결제 JPA Entity
 *
 * 멱등성 처리: idempotency_key로 중복 결제 방지
 * - 동일한 idempotency_key로 재요청시 기존 결과를 반환
 */
@Entity
@Table(
    name = "payments",
    indexes = [
        Index(name = "idx_payments_order_id", columnList = "order_id", unique = true),
        Index(name = "idx_payments_idempotency_key", columnList = "idempotency_key", unique = true),
        Index(name = "idx_payments_status", columnList = "status"),
        Index(name = "idx_payments_approved_at", columnList = "approved_at")
    ]
)
class PaymentJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Column(nullable = false)
    var orderId: Long = 0L,

    @Column(nullable = false, unique = true, length = 255)
    var idempotencyKey: String = "",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var method: PaymentMethodJpa = PaymentMethodJpa.CARD,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: PaymentStatusJpa = PaymentStatusJpa.PENDING,

    @Column(nullable = false)
    var amount: Long = 0L,

    @Column(length = 100)
    var transactionId: String? = null,

    @Column(length = 100)
    var pgCode: String? = null,

    @Column(columnDefinition = "TEXT")
    var failReason: String? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @Column
    var approvedAt: LocalDateTime? = null
) {
    /**
     * 결제 승인 처리
     */
    fun approve(transactionId: String) {
        require(status == PaymentStatusJpa.PENDING) { "이미 처리된 결제입니다" }
        status = PaymentStatusJpa.APPROVED
        this.transactionId = transactionId
        approvedAt = LocalDateTime.now()
        updatedAt = LocalDateTime.now()
    }

    /**
     * 결제 실패 처리
     */
    fun fail(failReason: String, pgCode: String? = null) {
        require(status == PaymentStatusJpa.PENDING) { "이미 처리된 결제입니다" }
        status = PaymentStatusJpa.FAILED
        this.failReason = failReason
        this.pgCode = pgCode
        updatedAt = LocalDateTime.now()
    }

    /**
     * 환불 처리
     */
    fun refund() {
        require(status == PaymentStatusJpa.APPROVED) { "승인된 결제만 환불 가능합니다" }
        status = PaymentStatusJpa.REFUNDED
        updatedAt = LocalDateTime.now()
    }

    /**
     * 결제가 성공했는지 확인
     */
    fun isApproved(): Boolean = status == PaymentStatusJpa.APPROVED

    /**
     * 결제 ID를 UUID 문자열로 변환
     */
    fun getUuid(): String = id.toUuid()

    /**
     * 주문 ID를 UUID 문자열로 변환
     */
    fun getOrderUuid(): String = orderId.toUuid()
}
