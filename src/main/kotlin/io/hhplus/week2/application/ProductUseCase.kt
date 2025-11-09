package io.hhplus.week2.application

import io.hhplus.week2.domain.Product
import io.hhplus.week2.repository.ProductRepository
import io.hhplus.week2.repository.InventoryRepository
import io.hhplus.week2.infrastructure.cache.CacheService
import org.springframework.stereotype.Service

/**
 * 상품 관리 유스케이스
 */
@Service
class ProductUseCase(
    private val productRepository: ProductRepository,
    private val inventoryRepository: InventoryRepository,
    private val cacheService: CacheService?
) {
    /**
     * 상품 단건 조회
     */
    fun getProductById(productId: String): Product? {
        return productRepository.findById(productId)
    }
    /**
     * 상품 목록 조회 (캐시 확인 포함)
     */
    fun getProducts(category: String?, sort: String): List<Product> {
        val cacheKey = "products:${category ?: "all"}:$sort"

        // 캐시 확인
        cacheService?.get(cacheKey)?.let {
            @Suppress("UNCHECKED_CAST")
            return it as List<Product>
        }

        // DB 조회
        val products = productRepository.findAll(category, sort)

        // 캐시 저장 (TTL = 60초)
        cacheService?.set(cacheKey, products, 60)

        return products
    }

    /**
     * 인기 상품 조회
     */
    fun getTopProducts(days: Int, limit: Int): TopProductResponse {
        // 조회 기간 계산
        val now = System.currentTimeMillis()
        val from = now - (days * 24L * 60 * 60 * 1000)

        // 상위 판매 상품 조회
        val topProducts = productRepository.findTopSelling(from, limit)

        return TopProductResponse("${days}days", topProducts)
    }

    /**
     * 재고 확인
     */
    fun checkStock(productId: String, quantity: Int): StockCheckResponse {
        // 상품 조회
        val product = productRepository.findById(productId)
            ?: throw IllegalStateException("상품을 찾을 수 없습니다")

        // 재고 조회 (Product ID를 SKU로 사용)
        val inventory = inventoryRepository.findBySku(productId)
            ?: throw IllegalStateException("재고 정보를 찾을 수 없습니다")

        // 재고 정보 반환
        return StockCheckResponse(
            available = inventory.canReserve(quantity),
            currentStock = inventory.getAvailableStock(),
            requested = quantity
        )
    }

    data class TopProductResponse(val period: String, val products: List<Product>)
    data class StockCheckResponse(val available: Boolean, val currentStock: Int, val requested: Int)
}
