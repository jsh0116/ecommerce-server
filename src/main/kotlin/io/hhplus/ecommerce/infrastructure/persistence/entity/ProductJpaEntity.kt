package io.hhplus.ecommerce.infrastructure.persistence.entity

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * 상품 JPA Entity
 */
@Entity
@Table(
    name = "products",
    indexes = [
        Index(name = "idx_products_category", columnList = "category"),
        Index(name = "idx_products_sales_count", columnList = "sales_count DESC"),
        Index(name = "idx_products_created_at", columnList = "created_at DESC")
    ]
)
class ProductJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Column(nullable = false, length = 255)
    var name: String = "",

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @Column(nullable = false)
    var price: Long = 0,

    @Column(nullable = false, length = 50)
    var category: String = "",

    @Column(nullable = false)
    var viewCount: Long = 0,

    @Column(nullable = false)
    var salesCount: Long = 0,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    /**
     * 조회수 증가
     */
    fun incrementViewCount() {
        viewCount++
        updatedAt = LocalDateTime.now()
    }

    /**
     * 판매량 증가
     */
    fun incrementSalesCount(quantity: Int) {
        require(quantity > 0) { "판매 수량은 1 이상이어야 합니다" }
        salesCount += quantity
        updatedAt = LocalDateTime.now()
    }

    /**
     * 인기도 점수 계산
     */
    fun calculatePopularityScore(): Long {
        return salesCount * 10 + viewCount
    }
}