package io.hhplus.ecommerce.infrastructure.persistence.repository

import io.hhplus.ecommerce.infrastructure.persistence.entity.PaymentJpaEntity
import io.hhplus.ecommerce.infrastructure.persistence.entity.PaymentStatusJpa
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

/**
 * 결제 JPA Repository
 *
 * 멱등성 처리: idempotency_key로 중복 결제 방지
 */
@Repository
interface PaymentJpaRepository : JpaRepository<PaymentJpaEntity, Long> {

    /**
     *
     * 주문 ID로 결제 조회
     */
    fun findByOrderId(orderId: Long): PaymentJpaEntity?

    /**
     * 멱등성 키로 결제 조회 (중복 요청 방지)
     */
    fun findByIdempotencyKey(idempotencyKey: String): PaymentJpaEntity?

    /**
     * 상태별 결제 조회
     */
    fun findByStatus(status: PaymentStatusJpa): List<PaymentJpaEntity>

    /**
     * 승인된 결제 조회
     */
    @Query("SELECT p FROM PaymentJpaEntity p WHERE p.status = 'APPROVED' ORDER BY p.approvedAt DESC")
    fun findApprovedPayments(): List<PaymentJpaEntity>
}
