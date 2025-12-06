package io.hhplus.ecommerce.infrastructure.repositories

import io.hhplus.ecommerce.domain.Payment
import io.hhplus.ecommerce.domain.PaymentMethod
import io.hhplus.ecommerce.domain.PaymentStatus
import io.hhplus.ecommerce.infrastructure.lock.DistributedLockService
import io.hhplus.ecommerce.infrastructure.persistence.entity.PaymentJpaEntity
import io.hhplus.ecommerce.infrastructure.persistence.entity.PaymentMethodJpa
import io.hhplus.ecommerce.infrastructure.persistence.entity.PaymentStatusJpa
import io.hhplus.ecommerce.infrastructure.persistence.repository.PaymentJpaRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.TimeUnit

/**
 * 결제 Repository 구현체
 *
 * - JPA Repository와 통신
 * - Entity ↔ Domain 모델 변환
 * - 트랜잭션 및 분산 락 관리
 */
@Repository
class PaymentRepositoryImpl(
    private val paymentJpaRepository: PaymentJpaRepository,
    private val distributedLockService: DistributedLockService
) : PaymentRepository {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val PAYMENT_LOCK_PREFIX = "payment:lock:"
    }

    /**
     * 결제 저장
     */
    @Transactional
    override fun save(payment: Payment): Payment {
        val entity = toEntity(payment)
        val saved = paymentJpaRepository.save(entity)
        return toDomain(saved)
    }

    /**
     * 결제 저장 (새 트랜잭션)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun saveInNewTransaction(payment: Payment): Payment {
        val threadId = Thread.currentThread().id
        val threadName = Thread.currentThread().name
        logger.debug("[{}][{}] saveInNewTransaction 시작 - idempotencyKey: {}", threadName, threadId, payment.idempotencyKey)

        // 먼저 기존 결제가 있는지 확인
        val existingPayment = findByIdempotencyKey(payment.idempotencyKey)
        if (existingPayment != null) {
            logger.debug("[{}][{}] 기존 결제 이미 존재 - ID: {}", threadName, threadId, existingPayment.id)
            return existingPayment
        }

        val entity = toEntity(payment)

        try {
            val saved = paymentJpaRepository.save(entity)
            logger.debug("[{}][{}] 결제 save 완료 - ID: {}", threadName, threadId, saved.id)

            // 즉시 DB에 반영
            paymentJpaRepository.flush()
            logger.debug("[{}][{}] 결제 flush 완료", threadName, threadId)

            return toDomain(saved)
        } catch (e: org.springframework.dao.DataIntegrityViolationException) {
            // 중복 idempotencyKey로 인한 제약 조건 위반
            logger.debug("[{}][{}] 중복 idempotencyKey 감지 - 기존 결제 조회 시도", threadName, threadId)

            val foundPayment = findByIdempotencyKey(payment.idempotencyKey)
            if (foundPayment != null) {
                logger.debug("[{}][{}] 기존 결제 발견 - ID: {}", threadName, threadId, foundPayment.id)
                return foundPayment
            }

            logger.error("[{}][{}] saveInNewTransaction 실행 중 예외 발생", threadName, threadId, e)
            throw e
        } catch (e: Exception) {
            logger.error("[{}][{}] saveInNewTransaction 실행 중 예외 발생", threadName, threadId, e)
            throw e
        }
    }

    /**
     * 멱등성 키로 결제 조회
     */
    @Transactional(readOnly = true)
    override fun findByIdempotencyKey(idempotencyKey: String): Payment? {
        return paymentJpaRepository.findByIdempotencyKey(idempotencyKey)?.let { toDomain(it) }
    }

    /**
     * 멱등성 키로 결제 조회 (새 트랜잭션)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun findByIdempotencyKeyInNewTransaction(idempotencyKey: String): Payment? {
        val threadId = Thread.currentThread().id
        val threadName = Thread.currentThread().name
        logger.debug("[{}][{}] findByIdempotencyKeyInNewTransaction 시작 - idempotencyKey: {}", threadName, threadId, idempotencyKey)

        val result = paymentJpaRepository.findByIdempotencyKey(idempotencyKey)?.let { toDomain(it) }
        logger.debug("[{}][{}] findByIdempotencyKeyInNewTransaction 완료 - 결과: {}", threadName, threadId, result?.id ?: "null")

        return result
    }

    /**
     * 주문 ID로 결제 조회
     */
    @Transactional(readOnly = true)
    override fun findByOrderId(orderId: Long): Payment? {
        return paymentJpaRepository.findByOrderId(orderId)?.let { toDomain(it) }
    }

    /**
     * ID로 결제 조회
     */
    @Transactional(readOnly = true)
    override fun findById(id: Long): Payment? {
        return paymentJpaRepository.findById(id).map { toDomain(it) }.orElse(null)
    }

    /**
     * 분산 락을 사용하여 코드 블록 실행
     */
    override fun <T> withDistributedLock(
        idempotencyKey: String,
        waitTime: Long,
        holdTime: Long,
        block: () -> T
    ): T {
        val threadId = Thread.currentThread().id
        val threadName = Thread.currentThread().name
        val lockKey = "$PAYMENT_LOCK_PREFIX$idempotencyKey"

        logger.info("[{}][{}] 분산 락 획득 시도 시작", threadName, threadId)

        val lockAcquired = distributedLockService.tryLock(
            key = lockKey,
            waitTime = waitTime,
            holdTime = holdTime,
            unit = TimeUnit.SECONDS
        )

        logger.info("[{}][{}] 분산 락 획득 결과: {}", threadName, threadId, lockAcquired)

        if (!lockAcquired) {
            logger.error("[{}][{}] 분산 락 획득 실패 - 타임아웃", threadName, threadId)
            throw IllegalStateException("결제 처리 대기 시간 초과")
        }

        try {
            return block()
        } finally {
            logger.info("[{}][{}] 분산 락 해제", threadName, threadId)
            distributedLockService.unlock(lockKey)
        }
    }

    /**
     * Domain → Entity 변환
     */
    private fun toEntity(payment: Payment): PaymentJpaEntity {
        return PaymentJpaEntity(
            id = payment.id,
            orderId = payment.orderId,
            idempotencyKey = payment.idempotencyKey,
            method = toMethodJpa(payment.method),
            status = toStatusJpa(payment.status),
            amount = payment.amount,
            transactionId = payment.transactionId,
            pgCode = payment.pgCode,
            failReason = payment.failReason,
            createdAt = payment.createdAt,
            updatedAt = payment.updatedAt,
            approvedAt = payment.approvedAt
        )
    }

    /**
     * Entity → Domain 변환
     */
    private fun toDomain(entity: PaymentJpaEntity): Payment {
        return Payment(
            id = entity.id,
            orderId = entity.orderId,
            idempotencyKey = entity.idempotencyKey,
            method = toMethodDomain(entity.method),
            status = toStatusDomain(entity.status),
            amount = entity.amount,
            transactionId = entity.transactionId,
            pgCode = entity.pgCode,
            failReason = entity.failReason,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            approvedAt = entity.approvedAt
        )
    }

    /**
     * PaymentMethod → PaymentMethodJpa 변환
     */
    private fun toMethodJpa(method: PaymentMethod): PaymentMethodJpa {
        return when (method) {
            PaymentMethod.CARD -> PaymentMethodJpa.CARD
            PaymentMethod.BANK_TRANSFER -> PaymentMethodJpa.BANK_TRANSFER
            PaymentMethod.VIRTUAL_ACCOUNT -> PaymentMethodJpa.VIRTUAL_ACCOUNT
        }
    }

    /**
     * PaymentMethodJpa → PaymentMethod 변환
     */
    private fun toMethodDomain(methodJpa: PaymentMethodJpa): PaymentMethod {
        return when (methodJpa) {
            PaymentMethodJpa.CARD -> PaymentMethod.CARD
            PaymentMethodJpa.BANK_TRANSFER -> PaymentMethod.BANK_TRANSFER
            PaymentMethodJpa.VIRTUAL_ACCOUNT -> PaymentMethod.VIRTUAL_ACCOUNT
        }
    }

    /**
     * PaymentStatus → PaymentStatusJpa 변환
     */
    private fun toStatusJpa(status: PaymentStatus): PaymentStatusJpa {
        return when (status) {
            PaymentStatus.PENDING -> PaymentStatusJpa.PENDING
            PaymentStatus.APPROVED -> PaymentStatusJpa.APPROVED
            PaymentStatus.FAILED -> PaymentStatusJpa.FAILED
            PaymentStatus.REFUNDED -> PaymentStatusJpa.REFUNDED
        }
    }

    /**
     * PaymentStatusJpa → PaymentStatus 변환
     */
    private fun toStatusDomain(statusJpa: PaymentStatusJpa): PaymentStatus {
        return when (statusJpa) {
            PaymentStatusJpa.PENDING -> PaymentStatus.PENDING
            PaymentStatusJpa.APPROVED -> PaymentStatus.APPROVED
            PaymentStatusJpa.FAILED -> PaymentStatus.FAILED
            PaymentStatusJpa.REFUNDED -> PaymentStatus.REFUNDED
        }
    }
}
