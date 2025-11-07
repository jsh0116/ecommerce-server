package io.hhplus.week2.domain

import java.time.LocalDateTime

/**
 * 상품 도메인 모델
 *
 * 상품 정보만 관리하며, 재고 관리는 Inventory 도메인이 담당합니다.
 */
data class Product(
    val id: String,
    val name: String,
    val description: String?,
    val price: Long,
    val category: String,
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    /**
     * 가격 계산
     */
    fun calculatePrice(quantity: Int): Long {
        if (quantity <= 0) {
            throw IllegalArgumentException("수량은 1 이상이어야 합니다")
        }
        return price * quantity
    }

    /**
     * 가격 유효성 검증
     */
    fun isValidPrice(): Boolean = price > 0
}
