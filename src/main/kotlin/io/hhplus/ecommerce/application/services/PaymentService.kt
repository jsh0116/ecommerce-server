package io.hhplus.ecommerce.application.services

import io.hhplus.ecommerce.domain.Payment
import io.hhplus.ecommerce.domain.PaymentMethod
import io.hhplus.ecommerce.exception.PaymentException
import io.hhplus.ecommerce.infrastructure.repositories.PaymentRepository
import org.springframework.stereotype.Service
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
    private val paymentRepository: PaymentRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 결제 처리 (멱등성 보장 + 분산 락)
     *
     * **중요**: 분산 락을 트랜잭션 외부에서 획득하여 올바른 순서를 보장합니다.
     *
     * 올바른 실행 순서:
     * 1. 분산 락 획득 시도 (트랜잭션 시작 전)
     * 2. 기존 요청 확인 (멱등성) - 별도 트랜잭션
     * 3. 새로운 요청이면 새 결제 레코드 생성 - 별도 트랜잭션
     * 4. 트랜잭션 커밋 완료 후 락 해제
     */
    fun processPayment(
        orderId: Long,
        amount: Long,
        method: PaymentMethod,
        idempotencyKey: String
    ): Payment {
        val threadId = Thread.currentThread().id
        val threadName = Thread.currentThread().name

        logger.info("[{}][{}] 결제 처리 시작 - orderId: {}, idempotencyKey: {}", threadName, threadId, orderId, idempotencyKey)

        return paymentRepository.withDistributedLock(
            idempotencyKey = idempotencyKey,
            waitTime = 60L,
            holdTime = 30L
        ) {
            try {
                // 1단계: 기존 요청 확인 (멱등성)
                logger.info("[{}][{}] 기존 결제 조회 시작", threadName, threadId)
                val existingPayment = paymentRepository.findByIdempotencyKeyInNewTransaction(idempotencyKey)

                if (existingPayment != null) {
                    logger.info("[{}][{}] 기존 결제 발견 - ID: {}", threadName, threadId, existingPayment.id)
                    return@withDistributedLock existingPayment
                }

                logger.info("[{}][{}] 새로운 결제 생성 시작", threadName, threadId)

                // 2단계: 새로운 결제 생성
                val newPayment = Payment.create(
                    orderId = orderId,
                    idempotencyKey = idempotencyKey,
                    method = method,
                    amount = amount
                )

                val savedPayment = paymentRepository.saveInNewTransaction(newPayment)

                logger.info("[{}][{}] 새로운 결제 생성 완료 - ID: {}", threadName, threadId, savedPayment.id)

                savedPayment
            } catch (e: Exception) {
                logger.error("[{}][{}] processPayment 실행 중 예외 발생", threadName, threadId, e)
                throw e
            }
        }
    }

    /**
     * 결제 승인 처리
     */
    fun approvePayment(paymentId: Long, transactionId: String): Payment {
        val payment = paymentRepository.findById(paymentId)
            ?: throw IllegalArgumentException("결제를 찾을 수 없습니다: $paymentId")

        val approvedPayment = payment.approve(transactionId)

        return paymentRepository.save(approvedPayment)
    }

    /**
     * 결제 실패 처리
     */
    fun failPayment(
        paymentId: Long,
        failReason: String,
        pgCode: String? = null
    ): Payment {
        val payment = paymentRepository.findById(paymentId)
            ?: throw IllegalArgumentException("결제를 찾을 수 없습니다: $paymentId")

        val failedPayment = payment.fail(failReason, pgCode)

        return paymentRepository.save(failedPayment)
    }

    /**
     * 환불 처리
     */
    fun refundPayment(paymentId: Long): Payment {
        val payment = paymentRepository.findById(paymentId)
            ?: throw IllegalArgumentException("결제를 찾을 수 없습니다: $paymentId")

        val refundedPayment = payment.refund()

        return paymentRepository.save(refundedPayment)
    }

    /**
     * 결제 조회
     */
    fun getPaymentByIdempotencyKey(idempotencyKey: String): Payment? {
        return paymentRepository.findByIdempotencyKey(idempotencyKey)
    }

    /**
     * 주문별 결제 조회
     */
    fun getPaymentByOrderId(orderId: Long): Payment? {
        return paymentRepository.findByOrderId(orderId)
    }

    /**
     * 결제가 이미 승인되었는지 확인
     */
    fun isPaymentApproved(orderId: Long): Boolean {
        val payment = paymentRepository.findByOrderId(orderId)
        return payment?.isApproved() ?: false
    }
}
