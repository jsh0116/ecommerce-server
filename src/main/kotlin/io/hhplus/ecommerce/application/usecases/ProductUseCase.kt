package io.hhplus.ecommerce.application.usecases

import io.hhplus.ecommerce.domain.Product
import io.hhplus.ecommerce.infrastructure.repositories.ProductRepository
import io.hhplus.ecommerce.infrastructure.repositories.InventoryRepository
import io.hhplus.ecommerce.infrastructure.cache.CacheService
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
    fun getProductById(productId: Long): Product? {
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
     * 상품 조회 (조회수 증가)
     */
    fun viewProduct(productId: Long): Product {
        val product = productRepository.findById(productId)
            ?: throw IllegalStateException("상품을 찾을 수 없습니다")

        // 조회수 증가
        product.incrementViewCount()
        productRepository.save(product)

        return product
    }

    /**
     * 판매량 증가 (주문 완료 시 호출)
     */
    fun recordSale(productId: Long, quantity: Int) {
        val product = productRepository.findById(productId)
            ?: throw IllegalStateException("상품을 찾을 수 없습니다")

        // 판매량 증가
        product.incrementSalesCount(quantity)
        productRepository.save(product)
    }

    /**
     * 인기 상품 조회 (조회수 및 판매량 기반 순위 계산)
     *
     * 인기도 점수 = (판매량 × 10) + 조회수
     * - 판매량에 더 높은 가중치를 부여하여 실제 구매로 이어진 상품을 우선
     */
    fun getTopProducts(limit: Int = 5): TopProductResponse {
        // 전체 상품 조회
        val allProducts = productRepository.findAll(null, "newest")

        // 인기도 점수 기준으로 정렬 및 상위 N개 선택
        val topProducts = allProducts
            .sortedByDescending { it.calculatePopularityScore() }
            .take(limit)
            .mapIndexed { index, product ->
                TopProductItem(
                    rank = index + 1,
                    product = product,
                    popularityScore = product.calculatePopularityScore(),
                    viewCount = product.viewCount,
                    salesCount = product.salesCount
                )
            }

        return TopProductResponse(products = topProducts)
    }

    /**
     * 최근 판매량 기준 인기 상품 조회 (기간별)
     */
    fun getTopSellingProducts(days: Int, limit: Int): TopProductResponse {
        // 조회 기간 계산
        val now = System.currentTimeMillis()
        val from = now - (days * 24L * 60 * 60 * 1000)

        // 상위 판매 상품 조회 (Repository에서 기간별 집계 지원 시)
        val topProducts = productRepository.findTopSelling(from, limit)
            .mapIndexed { index, product ->
                TopProductItem(
                    rank = index + 1,
                    product = product,
                    popularityScore = product.calculatePopularityScore(),
                    viewCount = product.viewCount,
                    salesCount = product.salesCount
                )
            }

        return TopProductResponse(products = topProducts)
    }

    /**
     * 재고 확인
     */
    fun checkStock(productId: Long, quantity: Int): StockCheckResponse {
        // 상품 조회
        val product = productRepository.findById(productId)
            ?: throw IllegalStateException("상품을 찾을 수 없습니다")

        // 재고 조회 (Product ID를 SKU로 사용)
        val inventory = inventoryRepository.findBySku(productId.toString())
            ?: throw IllegalStateException("재고 정보를 찾을 수 없습니다")

        // 재고 정보 반환
        return StockCheckResponse(
            available = inventory.canReserve(quantity),
            currentStock = inventory.getAvailableStock(),
            requested = quantity
        )
    }

    // 응답 DTO
    data class TopProductResponse(val products: List<TopProductItem>)

    data class TopProductItem(
        val rank: Int,
        val product: Product,
        val popularityScore: Long,
        val viewCount: Long,
        val salesCount: Long
    )

    data class StockCheckResponse(val available: Boolean, val currentStock: Int, val requested: Int)
}
