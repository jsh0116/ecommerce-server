package io.hhplus.ecommerce.domain

import java.time.LocalDateTime

/**
 * 결제 수단
 */
enum class PaymentMethod {
    CARD,
    BANK_TRANSFER,
    VIRTUAL_ACCOUNT
}

/**
 * 결제 상태
 */
enum class PaymentStatus {
    PENDING,
    APPROVED,
    FAILED,
    REFUNDED
}

/**
 * 결제 도메인 모델
 *
 * 비즈니스 로직을 포함하는 순수한 도메인 객체
 * Infrastructure 의존성이 없음
 */
data class Payment(
    val id: Long = 0L,
    val orderId: Long,
    val idempotencyKey: String,
    val method: PaymentMethod,
    var status: PaymentStatus = PaymentStatus.PENDING,
    val amount: Long,
    var transactionId: String? = null,
    var pgCode: String? = null,
    var failReason: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    var updatedAt: LocalDateTime = LocalDateTime.now(),
    var approvedAt: LocalDateTime? = null
) {
    /**
     * 결제 승인 처리
     */
    fun approve(transactionId: String): Payment {
        require(status == PaymentStatus.PENDING) { "이미 처리된 결제입니다" }
        return copy(
            status = PaymentStatus.APPROVED,
            transactionId = transactionId,
            approvedAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
    }

    /**
     * 결제 실패 처리
     */
    fun fail(failReason: String, pgCode: String? = null): Payment {
        require(status == PaymentStatus.PENDING) { "이미 처리된 결제입니다" }
        return copy(
            status = PaymentStatus.FAILED,
            failReason = failReason,
            pgCode = pgCode,
            updatedAt = LocalDateTime.now()
        )
    }

    /**
     * 환불 처리
     */
    fun refund(): Payment {
        require(status == PaymentStatus.APPROVED) { "승인된 결제만 환불 가능합니다" }
        return copy(
            status = PaymentStatus.REFUNDED,
            updatedAt = LocalDateTime.now()
        )
    }

    /**
     * 결제가 성공했는지 확인
     */
    fun isApproved(): Boolean = status == PaymentStatus.APPROVED

    /**
     * 결제가 처리 완료되었는지 확인 (승인, 실패, 환불 모두 포함)
     */
    fun isCompleted(): Boolean = status != PaymentStatus.PENDING

    companion object {
        /**
         * 새로운 결제 생성
         */
        fun create(
            orderId: Long,
            idempotencyKey: String,
            method: PaymentMethod,
            amount: Long
        ): Payment {
            require(amount > 0) { "결제 금액은 0보다 커야 합니다" }
            require(idempotencyKey.isNotBlank()) { "멱등성 키는 필수입니다" }

            return Payment(
                orderId = orderId,
                idempotencyKey = idempotencyKey,
                method = method,
                amount = amount
            )
        }
    }
}
