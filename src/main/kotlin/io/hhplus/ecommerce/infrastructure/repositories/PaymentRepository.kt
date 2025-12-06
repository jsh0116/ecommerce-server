package io.hhplus.ecommerce.infrastructure.repositories

import io.hhplus.ecommerce.domain.Payment

/**
 * 결제 Repository 인터페이스
 */
interface PaymentRepository {
    /**
     * 결제 저장
     */
    fun save(payment: Payment): Payment

    /**
     * 결제 저장 (새 트랜잭션)
     */
    fun saveInNewTransaction(payment: Payment): Payment

    /**
     * 멱등성 키로 결제 조회
     */
    fun findByIdempotencyKey(idempotencyKey: String): Payment?

    /**
     * 멱등성 키로 결제 조회 (새 트랜잭션)
     */
    fun findByIdempotencyKeyInNewTransaction(idempotencyKey: String): Payment?

    /**
     * 주문 ID로 결제 조회
     */
    fun findByOrderId(orderId: Long): Payment?

    /**
     * ID로 결제 조회
     */
    fun findById(id: Long): Payment?

    /**
     * 분산 락을 사용하여 코드 블록 실행
     */
    fun <T> withDistributedLock(
        idempotencyKey: String,
        waitTime: Long = 60L,
        holdTime: Long = 30L,
        block: () -> T
    ): T
}
