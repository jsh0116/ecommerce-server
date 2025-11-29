package io.hhplus.ecommerce.application.usecases

import io.hhplus.ecommerce.domain.Product
import io.hhplus.ecommerce.infrastructure.repositories.ProductRepository
import io.hhplus.ecommerce.infrastructure.repositories.InventoryRepository
import io.hhplus.ecommerce.infrastructure.cache.CacheService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.core.type.TypeReference
import org.slf4j.LoggerFactory
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
    private val logger = LoggerFactory.getLogger(javaClass)
    private val objectMapper = ObjectMapper()

    companion object {
        private const val TOP_PRODUCTS_CACHE_KEY = "products:top"
        private const val PRODUCTS_CACHE_PREFIX = "products:"
        private const val TOP_PRODUCTS_CACHE_TTL = 300 // 5분 (인기 상품은 자주 변경되므로 짧은 TTL)
        private const val PRODUCTS_LIST_CACHE_TTL = 60 // 1분 (상품 목록)
    }
    /**
     * 상품 단건 조회
     */
    fun getProductById(productId: Long): Product? {
        return productRepository.findById(productId)
    }
    /**
     * 상품 목록 조회 (Redis Cache-Aside 패턴)
     *
     * 캐시 전략:
     * - 카테고리별, 정렬 방식별로 캐시 키 생성
     * - TTL 60초: 상품 정보는 자주 변경되지 않지만 실시간성 필요
     * - Cache Miss 시 DB 조회 후 캐시 저장
     */
    fun getProducts(category: String?, sort: String): List<Product> {
        val cacheKey = "$PRODUCTS_CACHE_PREFIX${category ?: "all"}:$sort"

        return try {
            // 1단계: 캐시 조회 시도
            val cachedValue = cacheService?.get(cacheKey)
            if (cachedValue != null) {
                logger.debug("상품 목록 캐시 히트: cacheKey=$cacheKey")
                // JSON 문자열을 List<Product>로 역직렬화
                return try {
                    objectMapper.readValue(cachedValue as String, object : TypeReference<List<Product>>() {})
                } catch (e: Exception) {
                    logger.warn("상품 목록 캐시 역직렬화 실패: cacheKey=$cacheKey, error=${e.message}")
                    // 캐시 삭제 후 DB에서 조회
                    cacheService?.delete(cacheKey)
                    productRepository.findAll(category, sort)
                }
            }

            // 2단계: 캐시 미스 - DB에서 조회
            logger.debug("상품 목록 캐시 미스: cacheKey=$cacheKey, DB에서 조회 중")
            val products = productRepository.findAll(category, sort)

            // 3단계: DB에서 조회한 데이터를 캐시에 저장
            try {
                val jsonValue = objectMapper.writeValueAsString(products)
                cacheService?.set(cacheKey, jsonValue, PRODUCTS_LIST_CACHE_TTL)
                logger.debug("상품 목록 캐시 저장 완료: cacheKey=$cacheKey, ttl=${PRODUCTS_LIST_CACHE_TTL}s, count=${products.size}")
            } catch (e: Exception) {
                logger.warn("상품 목록 캐시 저장 실패: cacheKey=$cacheKey, error=${e.message}")
                // 캐시 저장 실패는 무시하고 데이터 반환
            }

            products
        } catch (e: Exception) {
            logger.error("상품 목록 조회 중 예외 발생: cacheKey=$cacheKey, error=${e.message}", e)
            // 예외 발생 시 캐시 없이 DB에서 조회
            productRepository.findAll(category, sort)
        }
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
     * 인기 상품 조회 (조회수 및 판매량 기반 순위 계산 + Redis 캐싱)
     *
     * 캐시 전략:
     * - 인기 상품은 자주 조회되지만 실시간성이 크게 중요하지 않음
     * - TTL 5분(300초): 조회수/판매량 변경을 반영하면서도 DB 부하 감소
     * - Cache-Aside 패턴: 캐시 미스 시 DB 조회 후 캐시 저장
     *
     * 인기도 점수 = (판매량 × 10) + 조회수
     * - 판매량에 더 높은 가중치를 부여하여 실제 구매로 이어진 상품을 우선
     */
    fun getTopProducts(limit: Int = 5): TopProductResponse {
        val cacheKey = "${TOP_PRODUCTS_CACHE_KEY}:limit:$limit"

        return try {
            // 1단계: 캐시 조회 시도
            val cachedValue = cacheService?.get(cacheKey)
            if (cachedValue != null) {
                logger.debug("인기 상품 캐시 히트: cacheKey=$cacheKey")
                // JSON 문자열을 TopProductResponse로 역직렬화
                return try {
                    objectMapper.readValue(cachedValue as String, TopProductResponse::class.java)
                } catch (e: Exception) {
                    logger.warn("인기 상품 캐시 역직렬화 실패: cacheKey=$cacheKey, error=${e.message}")
                    // 캐시 삭제 후 DB에서 조회
                    cacheService?.delete(cacheKey)
                    calculateTopProducts(limit)
                }
            }

            // 2단계: 캐시 미스 - DB에서 조회 및 계산
            logger.debug("인기 상품 캐시 미스: cacheKey=$cacheKey, DB에서 조회 중")
            val response = calculateTopProducts(limit)

            // 3단계: DB에서 조회한 데이터를 캐시에 저장
            try {
                val jsonValue = objectMapper.writeValueAsString(response)
                cacheService?.set(cacheKey, jsonValue, TOP_PRODUCTS_CACHE_TTL)
                logger.debug("인기 상품 캐시 저장 완료: cacheKey=$cacheKey, ttl=${TOP_PRODUCTS_CACHE_TTL}s, count=${response.products.size}")
            } catch (e: Exception) {
                logger.warn("인기 상품 캐시 저장 실패: cacheKey=$cacheKey, error=${e.message}")
                // 캐시 저장 실패는 무시하고 데이터 반환
            }

            response
        } catch (e: Exception) {
            logger.error("인기 상품 조회 중 예외 발생: cacheKey=$cacheKey, error=${e.message}", e)
            // 예외 발생 시 캐시 없이 DB에서 조회
            calculateTopProducts(limit)
        }
    }

    /**
     * 인기 상품 계산 로직 (캐싱 로직과 분리)
     */
    private fun calculateTopProducts(limit: Int): TopProductResponse {
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
