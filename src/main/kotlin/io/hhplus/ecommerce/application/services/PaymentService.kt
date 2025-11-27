package io.hhplus.ecommerce.application.services

import io.hhplus.ecommerce.exception.PaymentException
import io.hhplus.ecommerce.infrastructure.lock.DistributedLockService
import io.hhplus.ecommerce.infrastructure.persistence.entity.PaymentJpaEntity
import io.hhplus.ecommerce.infrastructure.persistence.entity.PaymentMethodJpa
import io.hhplus.ecommerce.infrastructure.persistence.entity.PaymentStatusJpa
import io.hhplus.ecommerce.infrastructure.persistence.repository.PaymentJpaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.annotation.Propagation
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
        val threadId = Thread.currentThread().id
        val threadName = Thread.currentThread().name

        // 1단계: 분산 락 획득 (멱등성 키별로 동시 요청 직렬화)
        val lockKey = "$PAYMENT_LOCK_PREFIX$idempotencyKey"
        logger.info("[{}][{}] 분산 락 획득 시도 시작", threadName, threadId)

        val lockAcquired = distributedLockService.tryLock(
            key = lockKey,
            waitTime = 60L,  // 충분한 대기 시간 (동시 요청 대응)
            holdTime = 30L,  // 충분한 보유 시간
            unit = TimeUnit.SECONDS
        )

        logger.info("[{}][{}] 분산 락 획득 결과: {}", threadName, threadId, lockAcquired)

        if (!lockAcquired) {
            logger.error("[{}][{}] 분산 락 획득 실패 - 타임아웃", threadName, threadId)
            throw PaymentException.PaymentLockTimeout("결제 처리 대기 시간 초과")
        }

        try {
            // 2단계: 기존 요청 확인 (멱등성) - 별도 transaction에서 조회하여 committed 데이터 확인
            logger.info("[{}][{}] 기존 결제 조회 시작", threadName, threadId)
            var existingPayment = queryExistingPayment(idempotencyKey)

            if (existingPayment != null) {
                logger.info("[{}][{}] 기존 결제 발견 - ID: {}", threadName, threadId, existingPayment.id)
                // 기존 결과 반환 (중복 요청 처리)
                return existingPayment
            }

            logger.info("[{}][{}] 새로운 결제 생성 시작", threadName, threadId)

            // 3단계: 새로운 결제 생성 (별도 transaction에서 수행하여 즉시 commit)
            // 이를 통해 unlock 후 다른 스레드가 변경사항을 볼 수 있게 합니다
            val savedPayment = createPaymentInNewTransaction(
                orderId = orderId,
                idempotencyKey = idempotencyKey,
                method = method,
                amount = amount
            )

            logger.info("[{}][{}] 새로운 결제 생성 완료 - ID: {}", threadName, threadId, savedPayment.id)

            return savedPayment
        } catch (e: Exception) {
            logger.error("[{}][{}] processPayment 실행 중 예외 발생", threadName, threadId, e)
            throw e
        } finally {
            logger.info("[{}][{}] 분산 락 해제", threadName, threadId)
            distributedLockService.unlock(lockKey)
        }
    }

    /**
     * 멱등성 키로 기존 결제 조회 (별도 transaction)
     *
     * 분산 락을 획득한 후 별도의 독립적인 transaction에서 조회합니다.
     * 이를 통해 다른 스레드의 commit된 데이터를 확실히 볼 수 있습니다.
     *
     * readOnly = false로 설정: readOnly 트랜잭션은 특별한 connection을 사용할 수 있어
     * 최신 committed 데이터를 보지 못할 수 있습니다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private fun queryExistingPayment(idempotencyKey: String): PaymentJpaEntity? {
        val threadId = Thread.currentThread().id
        val threadName = Thread.currentThread().name
        logger.debug("[{}][{}] queryExistingPayment 시작 - idempotencyKey: {}", threadName, threadId, idempotencyKey)

        val result = paymentRepository.findByIdempotencyKey(idempotencyKey)
        logger.debug("[{}][{}] queryExistingPayment 완료 - 결과: {}", threadName, threadId, result?.id ?: "null")

        return result
    }

    /**
     * 결제 생성 (별도 transaction)
     *
     * 새로운 transaction에서 payment를 생성하고 즉시 commit합니다.
     * 이를 통해 lock 해제 후에도 다른 스레드가 변경사항을 볼 수 있게 합니다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private fun createPaymentInNewTransaction(
        orderId: Long,
        idempotencyKey: String,
        method: PaymentMethodJpa,
        amount: Long
    ): PaymentJpaEntity {
        val threadId = Thread.currentThread().id
        val threadName = Thread.currentThread().name
        logger.debug("[{}][{}] createPaymentInNewTransaction 시작 - orderId: {}, idempotencyKey: {}", threadName, threadId, orderId, idempotencyKey)

        // 먼저 기존 결제가 있는지 확인합니다
        val existingPayment = paymentRepository.findByIdempotencyKey(idempotencyKey)
        if (existingPayment != null) {
            logger.debug("[{}][{}] 기존 결제 이미 존재 - ID: {}", threadName, threadId, existingPayment.id)
            return existingPayment
        }

        val payment = PaymentJpaEntity(
            orderId = orderId,
            idempotencyKey = idempotencyKey,
            method = method,
            amount = amount
        )

        try {
            val savedPayment = paymentRepository.save(payment)
            logger.debug("[{}][{}] 결제 save 완료 - ID: {}", threadName, threadId, savedPayment.id)

            // 동시 요청 시 다른 스레드에서 방금 생성된 결제를 조회할 수 있도록
            // JPA Persistence Context의 변경사항을 즉시 데이터베이스에 반영합니다
            paymentRepository.flush()
            logger.debug("[{}][{}] 결제 flush 완료", threadName, threadId)

            return savedPayment
        } catch (e: org.springframework.dao.DataIntegrityViolationException) {
            // 중복 idempotencyKey로 인한 제약 조건 위반
            // 다른 스레드가 이미 생성한 결제가 있으므로, 그것을 반환합니다
            logger.debug("[{}][{}] 중복 idempotencyKey 감지 - 기존 결제 조회 시도", threadName, threadId)

            // 명시적으로 새로운 쿼리로 조회하여 다른 스레드가 만든 결제를 찾습니다
            val foundPayment = paymentRepository.findByIdempotencyKey(idempotencyKey)
            if (foundPayment != null) {
                logger.debug("[{}][{}] 기존 결제 발견 - ID: {}", threadName, threadId, foundPayment.id)
                return foundPayment
            }

            // 예상치 못한 에러인 경우 재발생
            logger.error("[{}][{}] createPaymentInNewTransaction 실행 중 예외 발생", threadName, threadId, e)
            throw e
        } catch (e: Exception) {
            logger.error("[{}][{}] createPaymentInNewTransaction 실행 중 예외 발생", threadName, threadId, e)
            throw e
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
