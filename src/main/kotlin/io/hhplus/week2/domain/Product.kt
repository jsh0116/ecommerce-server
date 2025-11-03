package io.hhplus.week2.domain

/**
 * 상품 도메인 모델
 */
data class Product(
    val id: String,
    val name: String,
    val brand: String,
    val category: String,
    val description: String?,
    val basePrice: Long,
    val salePrice: Long,
    val discountRate: Int,
    val images: List<String>,
    val tags: List<String>,
    val rating: Double,
    val reviewCount: Int,
    val createdAt: String
)

/**
 * 상품 변량 (SKU별 정보)
 */
data class ProductVariant(
    val id: String,
    val productId: String,
    val sku: String,
    val color: String,
    val colorHex: String,
    val size: String,
    val length: String?,
    val price: Long,
    val originalPrice: Long,
    val stock: Int,
    val stockStatus: StockStatus,
    val images: List<String>?
)

enum class StockStatus {
    IN_STOCK,
    LOW_STOCK,
    OUT_OF_STOCK
}

/**
 * 재고 정보
 */
data class Inventory(
    val sku: String,
    val available: Int,
    val reserved: Int,
    val physical: Int,
    val safetyStock: Int,
    val status: StockStatus,
    val lastUpdated: String
)
