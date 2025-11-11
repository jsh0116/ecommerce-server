package io.hhplus.ecommerce.infrastructure.persistence.repository

import io.hhplus.ecommerce.infrastructure.persistence.entity.PaymentFailureJpaEntity
import io.hhplus.ecommerce.infrastructure.persistence.entity.CompensationStatusJpa
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

/**
 * 결제 실패 JPA Repository
 *
 * 보상 트랜잭션(환불, 재고 복구) 추적
 */
@Repository
interface PaymentFailureJpaRepository : JpaRepository<PaymentFailureJpaEntity, String> {

    /**
     * 주문별 결제 실패 조회
     */
    fun findByOrderId(orderId: Long): List<PaymentFailureJpaEntity>

    /**
     * 결제별 결제 실패 조회
     */
    fun findByPaymentId(paymentId: Long): PaymentFailureJpaEntity?

    /**
     * 보상 대기 중인 실패 조회
     */
    @Query("""
        SELECT pf FROM PaymentFailureJpaEntity pf
        WHERE pf.compensationStatus = 'PENDING'
        ORDER BY pf.createdAt ASC
    """)
    fun findPendingCompensations(): List<PaymentFailureJpaEntity>

    /**
     * 상태별 실패 조회
     */
    fun findByCompensationStatus(compensationStatus: CompensationStatusJpa): List<PaymentFailureJpaEntity>
}
