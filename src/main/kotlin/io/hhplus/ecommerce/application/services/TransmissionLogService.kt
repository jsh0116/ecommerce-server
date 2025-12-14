package io.hhplus.ecommerce.application.services

import io.hhplus.ecommerce.infrastructure.persistence.entity.DataTransmissionLogJpaEntity
import io.hhplus.ecommerce.infrastructure.persistence.entity.TransmissionStatus
import io.hhplus.ecommerce.infrastructure.persistence.repository.DataTransmissionLogJpaRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 데이터 전송 로그 서비스
 *
 * 외부 시스템 전송 로그를 관리합니다.
 * Repository 직접 접근을 캡슐화하여 Application Layer에서 사용합니다.
 */
@Service
class TransmissionLogService(
    private val transmissionLogRepository: DataTransmissionLogJpaRepository
) {
    companion object {
        private val logger = LoggerFactory.getLogger(TransmissionLogService::class.java)
    }

    /**
     * 전송 로그 생성 (PENDING 상태)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun createPendingLog(orderId: Long, userId: Long): DataTransmissionLogJpaEntity {
        val log = DataTransmissionLogJpaEntity(
            orderId = orderId,
            userId = userId,
            status = TransmissionStatus.PENDING
        )
        return transmissionLogRepository.save(log)
    }

    /**
     * 전송 성공으로 로그 업데이트
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun markAsSuccess(log: DataTransmissionLogJpaEntity): DataTransmissionLogJpaEntity {
        log.markAsSuccess()
        val saved = transmissionLogRepository.save(log)
        logger.info("전송 성공 로그 업데이트: orderId=${log.orderId}")
        return saved
    }

    /**
     * 전송 실패로 로그 업데이트
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun markAsFailed(log: DataTransmissionLogJpaEntity, errorMessage: String): DataTransmissionLogJpaEntity {
        log.markAsFailed(errorMessage)
        val saved = transmissionLogRepository.save(log)
        logger.warn("전송 실패 로그 업데이트: orderId=${log.orderId}, error=$errorMessage")
        return saved
    }

    /**
     * 재시도 중으로 로그 업데이트
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun markAsRetrying(log: DataTransmissionLogJpaEntity): DataTransmissionLogJpaEntity {
        log.markAsRetrying()
        val saved = transmissionLogRepository.save(log)
        logger.info("재시도 로그 업데이트: orderId=${log.orderId}")
        return saved
    }

    /**
     * 특정 주문의 전송 로그 조회
     */
    @Transactional(readOnly = true)
    fun findByOrderId(orderId: Long): DataTransmissionLogJpaEntity? {
        return transmissionLogRepository.findByOrderId(orderId)
    }

    /**
     * 특정 상태의 전송 로그 조회
     */
    @Transactional(readOnly = true)
    fun findByStatus(status: TransmissionStatus): List<DataTransmissionLogJpaEntity> {
        return transmissionLogRepository.findByStatus(status)
    }
}
