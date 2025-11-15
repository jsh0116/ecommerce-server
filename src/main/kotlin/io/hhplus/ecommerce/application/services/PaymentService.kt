package io.hhplus.ecommerce.application.services

import io.hhplus.ecommerce.infrastructure.persistence.entity.PaymentJpaEntity
import io.hhplus.ecommerce.infrastructure.persistence.entity.PaymentMethodJpa
import io.hhplus.ecommerce.infrastructure.persistence.entity.PaymentStatusJpa
import io.hhplus.ecommerce.infrastructure.persistence.repository.PaymentJpaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 결제 비즈니스 로직 Service
 *
 * 멱등성 처리를 통해 중복 결제 방지
 * - 동일한 idempotency_key로 재요청 시 기존 결과 반환
 */
@Service
class PaymentService(
    private val paymentRepository: PaymentJpaRepository
) {
    /**
     * 결제 처리 (멱등성 보장)
     *
     * 1. 동일한 idempotency_key가 이미 존재하면 기존 결과 반환
     * 2. 새로운 요청이면 새 결제 레코드 생성
     */
    @Transactional
    fun processPayment(
        orderId: Long,
        amount: Long,
        method: PaymentMethodJpa,
        idempotencyKey: String
    ): PaymentJpaEntity {
        // 1단계: 기존 요청 확인 (멱등성)
        val existingPayment = paymentRepository.findByIdempotencyKey(idempotencyKey)
        if (existingPayment != null) {
            // 기존 결과 반환 (중복 요청 처리)
            return existingPayment
        }

        // 2단계: 새로운 결제 생성
        val payment = PaymentJpaEntity(
            orderId = orderId,
            idempotencyKey = idempotencyKey,
            method = method,
            amount = amount
        )

        return paymentRepository.save(payment)
    }

    /**
     * 결제 승인 처리
     */
    @Transactional
    fun approvePayment(paymentId: Long, transactionId: String): PaymentJpaEntity {
        val payment = paymentRepository.findById(paymentId)
            .orElseThrow { IllegalArgumentException("결제를 찾을 수 없습니다: $paymentId") }

        payment.approve(transactionId)

        return paymentRepository.save(payment)
    }

    /**
     * 결제 실패 처리
     */
    @Transactional
    fun failPayment(
        paymentId: Long,
        failReason: String,
        pgCode: String? = null
    ): PaymentJpaEntity {
        val payment = paymentRepository.findById(paymentId)
            .orElseThrow { IllegalArgumentException("결제를 찾을 수 없습니다: $paymentId") }

        payment.fail(failReason, pgCode)

        return paymentRepository.save(payment)
    }

    /**
     * 환불 처리
     */
    @Transactional
    fun refundPayment(paymentId: Long): PaymentJpaEntity {
        val payment = paymentRepository.findById(paymentId)
            .orElseThrow { IllegalArgumentException("결제를 찾을 수 없습니다: $paymentId") }

        payment.refund()

        return paymentRepository.save(payment)
    }

    /**
     * 결제 조회
     */
    @Transactional(readOnly = true)
    fun getPaymentByIdempotencyKey(idempotencyKey: String): PaymentJpaEntity? {
        return paymentRepository.findByIdempotencyKey(idempotencyKey)
    }

    /**
     * 주문별 결제 조회
     */
    @Transactional(readOnly = true)
    fun getPaymentByOrderId(orderId: Long): PaymentJpaEntity? {
        return paymentRepository.findByOrderId(orderId)
    }

    /**
     * 결제가 이미 승인되었는지 확인
     */
    @Transactional(readOnly = true)
    fun isPaymentApproved(orderId: Long): Boolean {
        val payment = paymentRepository.findByOrderId(orderId)
        return payment?.isApproved() ?: false
    }
}
