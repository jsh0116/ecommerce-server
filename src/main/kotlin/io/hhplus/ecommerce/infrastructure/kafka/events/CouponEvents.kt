package io.hhplus.ecommerce.infrastructure.kafka.events

import java.time.LocalDateTime

/**
 * 쿠폰 발급 요청 이벤트
 */
data class CouponIssuanceRequestedEvent(
    val requestId: String,
    val userId: Long,
    val couponId: Long,
    val requestedAt: LocalDateTime = LocalDateTime.now()
)

/**
 * 쿠폰 발급 완료 이벤트
 */
data class CouponIssuedEvent(
    val requestId: String,
    val userId: Long,
    val couponId: Long,
    val userCouponId: Long? = null,  // Optional - domain model doesn't expose ID
    val issuedAt: LocalDateTime = LocalDateTime.now()
)

/**
 * 쿠폰 발급 실패 이벤트
 */
data class CouponIssuanceFailedEvent(
    val requestId: String,
    val userId: Long,
    val couponId: Long,
    val errorCode: String,
    val errorMessage: String,
    val failedAt: LocalDateTime = LocalDateTime.now()
)

/**
 * 쿠폰 발급 응답 DTO
 */
data class CouponIssuanceResponse(
    val requestId: String,
    val status: String, // PENDING, SUCCESS, FAILED
    val message: String,
    val userCouponId: Long? = null
)
