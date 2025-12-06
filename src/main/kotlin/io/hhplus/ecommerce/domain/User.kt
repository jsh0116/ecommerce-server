package io.hhplus.ecommerce.domain

import io.hhplus.ecommerce.exception.UserException

/**
 * 사용자 도메인 모델
 */
data class User(
    val id: Long,
    var balance: Long,
    val createdAt: String
) {
    fun charge(amount: Long) {
        if (amount <= 0) {
            throw UserException.InvalidChargeAmount(amount)
        }
        balance += amount
    }

    fun pay(amount: Long) {
        if (amount <= 0) {
            throw UserException.InvalidPaymentAmount(amount)
        }
        if (balance < amount) {
            throw UserException.InsufficientBalance(
                required = amount,
                current = balance
            )
        }
        balance -= amount
    }
}
