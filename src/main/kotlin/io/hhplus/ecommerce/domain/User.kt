package io.hhplus.ecommerce.domain

/**
 * 사용자 도메인 모델
 */
data class User(
    val id: Long,
    var balance: Long,
    val createdAt: String
)
