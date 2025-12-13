package io.hhplus.ecommerce.infrastructure.persistence.repository

import io.hhplus.ecommerce.infrastructure.persistence.entity.DataTransmissionLogJpaEntity
import io.hhplus.ecommerce.infrastructure.persistence.entity.TransmissionStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * 외부 데이터 전송 로그 Repository
 */
@Repository
interface DataTransmissionLogJpaRepository : JpaRepository<DataTransmissionLogJpaEntity, Long> {

    /**
     * 주문 ID로 전송 로그 조회
     */
    fun findByOrderId(orderId: Long): DataTransmissionLogJpaEntity?

    /**
     * 특정 상태의 전송 로그 조회
     */
    fun findByStatus(status: TransmissionStatus): List<DataTransmissionLogJpaEntity>

    /**
     * 재시도 대상 조회 (FAILED 또는 RETRYING 상태)
     */
    fun findByStatusIn(statuses: List<TransmissionStatus>): List<DataTransmissionLogJpaEntity>
}
