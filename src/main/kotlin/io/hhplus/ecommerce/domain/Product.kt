package io.hhplus.ecommerce.domain

import java.time.LocalDateTime

/**
 * 상품 도메인 모델
 *
 * 상품 정보만 관리하며, 재고 관리는 Inventory 도메인이 담당합니다.
 */
data class Product(
    val id: Long,
    val name: String,
    val description: String?,
    val price: Long,
    val category: String,
    var viewCount: Long = 0L,           // 조회수
    var salesCount: Long = 0L,          // 판매량
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

    /**
     * 조회수 증가
     */
    fun incrementViewCount() {
        viewCount++
    }

    /**
     * 판매량 증가
     */
    fun incrementSalesCount(quantity: Int) {
        if (quantity <= 0) {
            throw IllegalArgumentException("판매 수량은 1 이상이어야 합니다")
        }
        salesCount += quantity
    }

    /**
     * 인기도 점수 계산 (조회수와 판매량의 가중 평균)
     * - 판매량에 더 높은 가중치 부여 (판매 1건 = 조회 10건)
     */
    fun calculatePopularityScore(): Long {
        return salesCount * 10 + viewCount
    }
}
