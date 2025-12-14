package io.hhplus.ecommerce.dto

import io.hhplus.ecommerce.domain.OrderItem
import java.time.LocalDateTime

/**
 * 외부 데이터 전송용 Payload DTO
 */
data class DataPayload(
    val orderId: Long,
    val userId: Long,
    val items: List<OrderItem>,
    val totalAmount: Long,
    val discountAmount: Long,
    val paidAt: LocalDateTime?
)
