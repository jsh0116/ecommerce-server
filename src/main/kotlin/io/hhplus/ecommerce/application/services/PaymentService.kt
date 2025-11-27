package io.hhplus.ecommerce.application.services

import io.hhplus.ecommerce.exception.PaymentException
import io.hhplus.ecommerce.infrastructure.lock.DistributedLockService
import io.hhplus.ecommerce.infrastructure.persistence.entity.PaymentJpaEntity
import io.hhplus.ecommerce.infrastructure.persistence.entity.PaymentMethodJpa
import io.hhplus.ecommerce.infrastructure.persistence.entity.PaymentStatusJpa
import io.hhplus.ecommerce.infrastructure.persistence.repository.PaymentJpaRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory

/**
 * 결제 비즈니스 로직 Service
 *
 * 멱등성 처리를 통해 중복 결제 방지
 * - 분산 락으로 동일한 idempotencyKey의 동시 요청을 직렬화
 * - 동일한 idempotency_key로 재요청 시 기존 결과 반환
 */
@Service
class PaymentService(
    private val paymentRepository: PaymentJpaRepository,
    private val distributedLockService: DistributedLockService
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    companion object {
        private const val PAYMENT_LOCK_PREFIX = "payment:lock:"
    }
    /**
     * 결제 처리 (멱등성 보장 + 분산 락)
     *
     * 분산 락을 통해 동일한 idempotencyKey의 동시 요청을 직렬화합니다.
     * 이는 Race Condition을 방지하고 멱등성을 보장합니다.
     *
     * 실행 순서:
     * 1. 분산 락 획득 시도 (waitTime: 10초, holdTime: 10초)
     * 2. 기존 요청 확인 (멱등성)
     * 3. 새로운 요청이면 새 결제 레코드 생성
     * 4. finally에서 명시적 락 해제
     *
     * 격리 수준: READ_COMMITTED
     * - 다른 트랜잭션의 커밋된 변경사항을 즉시 반영 (동시성 테스트 안정성)
     * - 분산 락이 있으므로 dirty read 걱정 없음
     */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    fun processPayment(
        orderId: Long,
        amount: Long,
        method: PaymentMethodJpa,
        idempotencyKey: String
    ): PaymentJpaEntity {
        // 1단계: 분산 락 획득
        val lockKey = "$PAYMENT_LOCK_PREFIX$idempotencyKey"
        val lockAcquired = distributedLockService.tryLock(
            key = lockKey,
            waitTime = 10L,  // 대기 시간 (동시 요청 대응)
            holdTime = 10L,
            unit = TimeUnit.SECONDS
        )

        if (!lockAcquired) {
            throw PaymentException.PaymentLockTimeout("결제 처리 대기 시간 초과")
        }

        try {
            // 2단계: 기존 요청 확인 (멱등성)
            var existingPayment = paymentRepository.findByIdempotencyKey(idempotencyKey)
            if (existingPayment != null) {
                // 기존 결과 반환 (중복 요청 처리)
                return existingPayment
            }

            // 3단계: 새로운 결제 생성
            val payment = PaymentJpaEntity(
                orderId = orderId,
                idempotencyKey = idempotencyKey,
                method = method,
                amount = amount
            )

            try {
                val savedPayment = paymentRepository.save(payment)

                // 동시 요청 시 다른 스레드에서 방금 생성된 결제를 조회할 수 있도록
                // JPA Persistence Context의 변경사항을 즉시 데이터베이스에 반영합니다
                paymentRepository.flush()

                return savedPayment
            } catch (e: DataIntegrityViolationException) {
                // 고유 제약조건 위반 (동시성 요청으로 인한 중복)
                // 트랜잭션 롤백 후 기존 결제 조회
                logger.warn("중복 결제 감지, 기존 결제 조회 시도: {}", idempotencyKey)

                // DataIntegrityViolationException 발생 후에는 현재 트랜잭션이 rollback-only 상태
                // 따라서 새로운 쿼리 실행도 실패할 수 있으므로, 예외를 다시 던짐
                // (분산 락이 이미 확보되었으므로 재시도는 불필요)
                throw e
            }
        } finally {
            distributedLockService.unlock(lockKey)
        }
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
