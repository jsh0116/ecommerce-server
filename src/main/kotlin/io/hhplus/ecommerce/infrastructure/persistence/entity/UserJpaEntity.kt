package io.hhplus.ecommerce.infrastructure.persistence.entity

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * 사용자 JPA Entity
 */
@Entity
@Table(
    name = "users",
    indexes = [
        Index(name = "idx_users_created_at", columnList = "created_at DESC")
    ]
)
class UserJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Column(nullable = false)
    var balance: Long = 0,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    /**
     * 잔액 충전
     */
    fun chargeBalance(amount: Long) {
        require(amount > 0) { "충전 금액은 0 이상이어야 합니다" }
        balance += amount
        updatedAt = LocalDateTime.now()
    }

    /**
     * 잔액 차감
     */
    fun deductBalance(amount: Long) {
        require(amount > 0) { "차감 금액은 0 이상이어야 합니다" }
        require(balance >= amount) { "잔액이 부족합니다" }
        balance -= amount
        updatedAt = LocalDateTime.now()
    }

    /**
     * 잔액 확인
     */
    fun hasEnoughBalance(amount: Long): Boolean {
        return balance >= amount
    }
}