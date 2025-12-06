package io.hhplus.ecommerce.application.usecases

import io.hhplus.ecommerce.application.services.ProductService
import io.hhplus.ecommerce.application.services.ProductRankingService
import io.hhplus.ecommerce.domain.Product
import io.hhplus.ecommerce.infrastructure.repositories.InventoryRepository
import io.hhplus.ecommerce.infrastructure.repositories.ProductRepository
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

/**
 * 상품 관리 유스케이스
 */
@Service
class ProductUseCase(
    private val productService: ProductService,
    private val inventoryRepository: InventoryRepository,
    private val productRepository: ProductRepository,
    private val productRankingService: ProductRankingService
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    /**
     * 상품 단건 조회
     */
    fun getProductById(productId: Long): Product? {
        return productService.getByIdOrNull(productId)
    }
    /**
     * 상품 목록 조회
     *
     * Spring Cache 전략:
     * - 카테고리별, 정렬 방식별로 캐시 키 자동 생성
     * - TTL 60초 (RedisConfig에서 설정)
     */
    @Cacheable(value = ["products"], key = "T(String).valueOf(#category ?: 'all') + ':' + #sort")
    fun getProducts(category: String?, sort: String): List<Product> {
        logger.debug("상품 목록 조회 (DB): category=$category, sort=$sort")
        return productService.findAll(category, sort)
    }

    /**
     * 상품 조회 (조회수 증가)
     */
    fun viewProduct(productId: Long): Product {
        val product = productService.getByIdOrNull(productId)
            ?: throw IllegalStateException("상품을 찾을 수 없습니다")

        // 조회수 증가
        product.incrementViewCount()
        productService.save(product)

        return product
    }

    /**
     * 판매량 증가 (주문 완료 시 호출)
     */
    fun recordSale(productId: Long, quantity: Int) {
        val product = productService.getByIdOrNull(productId)
            ?: throw IllegalStateException("상품을 찾을 수 없습니다")

        // 판매량 증가
        product.incrementSalesCount(quantity)
        productService.save(product)
    }

    /**
     * 인기 상품 조회 (조회수 및 판매량 기반 순위 계산)
     *
     * Spring Cache 전략:
     * - TTL 5분 (RedisConfig에서 topProducts 캐시로 설정)
     * - 인기도 점수 = (판매량 × 10) + 조회수
     */
    @Cacheable(value = ["topProducts"], key = "#limit")
    fun getTopProducts(limit: Int = 5): TopProductResponse {
        logger.debug("인기 상품 조회 (DB): limit=$limit")

        // 전체 상품 조회
        val allProducts = productService.findAll(null, "newest")

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
        val topProducts = productService.findTopSelling(from, limit)
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
     * Redis 기반 일간 TOP N 상품 조회
     *
     * ProductRankingService에서 상품 ID 목록을 조회한 후,
     * ProductRepository에서 일괄 조회하여 응답을 생성합니다.
     *
     * @param limit 조회할 상품 수
     * @return 랭킹 순으로 정렬된 상품 목록
     */
    fun getTopProductsFromDailyRanking(limit: Int = 10): TopProductResponse {
        logger.debug("일간 랭킹 기반 인기 상품 조회: limit=$limit")

        // 1. Redis에서 상품 ID 목록 조회
        val rankingIds = productRankingService.getTopProductIdsDaily(limit)

        if (rankingIds.isEmpty()) {
            return TopProductResponse(products = emptyList())
        }

        // 2. ProductRepository에서 상품 정보 일괄 조회
        val productIds = rankingIds.map { it.productId }
        val products = productRepository.findAllById(productIds).associateBy { it.id }

        // 3. 랭킹 순서대로 응답 생성
        val topProducts = rankingIds.mapNotNull { rankingId ->
            val product = products[rankingId.productId] ?: return@mapNotNull null
            TopProductItem(
                rank = rankingId.rank,
                product = product,
                popularityScore = product.calculatePopularityScore(),
                viewCount = product.viewCount,
                salesCount = rankingId.salesCount // Redis의 판매량 사용
            )
        }

        return TopProductResponse(products = topProducts)
    }

    /**
     * Redis 기반 주간 TOP N 상품 조회
     *
     * ProductRankingService에서 상품 ID 목록을 조회한 후,
     * ProductRepository에서 일괄 조회하여 응답을 생성합니다.
     *
     * @param limit 조회할 상품 수
     * @return 랭킹 순으로 정렬된 상품 목록
     */
    fun getTopProductsFromWeeklyRanking(limit: Int = 10): TopProductResponse {
        logger.debug("주간 랭킹 기반 인기 상품 조회: limit=$limit")

        // 1. Redis에서 상품 ID 목록 조회
        val rankingIds = productRankingService.getTopProductIdsWeekly(limit)

        if (rankingIds.isEmpty()) {
            return TopProductResponse(products = emptyList())
        }

        // 2. ProductRepository에서 상품 정보 일괄 조회
        val productIds = rankingIds.map { it.productId }
        val products = productRepository.findAllById(productIds).associateBy { it.id }

        // 3. 랭킹 순서대로 응답 생성
        val topProducts = rankingIds.mapNotNull { rankingId ->
            val product = products[rankingId.productId] ?: return@mapNotNull null
            TopProductItem(
                rank = rankingId.rank,
                product = product,
                popularityScore = product.calculatePopularityScore(),
                viewCount = product.viewCount,
                salesCount = rankingId.salesCount // Redis의 판매량 사용
            )
        }

        return TopProductResponse(products = topProducts)
    }

    /**
     * 재고 확인
     */
    fun checkStock(productId: Long, quantity: Int): StockCheckResponse {
        // 상품 조회
        val product = productService.getByIdOrNull(productId)
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
