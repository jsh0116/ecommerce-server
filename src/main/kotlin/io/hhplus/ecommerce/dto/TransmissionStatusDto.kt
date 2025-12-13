package io.hhplus.ecommerce.dto

import io.hhplus.ecommerce.infrastructure.persistence.entity.DataTransmissionLogJpaEntity
import io.hhplus.ecommerce.infrastructure.persistence.entity.TransmissionStatus
import java.time.LocalDateTime

/**
 * 외부 전송 상태 응답 DTO
 */
data class TransmissionStatusResponse(
    val orderId: Long,
    val userId: Long,
    val status: TransmissionStatus,
    val retryCount: Int,
    val lastAttemptAt: LocalDateTime?,
    val errorMessage: String?,
    val completedAt: LocalDateTime?
) {
    companion object {
        fun from(log: DataTransmissionLogJpaEntity): TransmissionStatusResponse {
            return TransmissionStatusResponse(
                orderId = log.orderId,
                userId = log.userId,
                status = log.status,
                retryCount = log.retryCount,
                lastAttemptAt = log.lastAttemptAt,
                errorMessage = log.errorMessage,
                completedAt = log.completedAt
            )
        }
    }
}
