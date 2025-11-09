package io.hhplus.ecommerce.application

import io.hhplus.ecommerce.application.usecases.ProductUseCase
import io.hhplus.ecommerce.domain.Product
import io.hhplus.ecommerce.domain.Inventory
import io.hhplus.ecommerce.infrastructure.repositories.ProductRepository
import io.hhplus.ecommerce.infrastructure.repositories.InventoryRepository
import io.hhplus.ecommerce.infrastructure.cache.CacheService
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("ProductUseCase 테스트")
class ProductUseCaseTest {

    private lateinit var productUseCase: ProductUseCase
    private lateinit var productRepository: ProductRepository
    private lateinit var inventoryRepository: InventoryRepository
    private lateinit var cacheService: CacheService

    @BeforeEach
    fun setUp() {
        productRepository = mockk()
        inventoryRepository = mockk()
        cacheService = mockk()
        productUseCase = ProductUseCase(productRepository, inventoryRepository, cacheService)
    }

    @Test
    @DisplayName("상품을 단건 조회할 수 있다")
    fun testGetProductById() {
        // Given
        val productId = "prod1"
        val product = Product(
            id = productId,
            name = "노트북",
            description = "고성능 노트북",
            price = 1_000_000L,
            category = "전자제품"
        )

        every { productRepository.findById(productId) } returns product

        // When
        val result = productUseCase.getProductById(productId)

        // Then
        assert(result != null)
        assert(result?.id == productId)
        assert(result?.name == "노트북")
        verify { productRepository.findById(productId) }
    }

    @Test
    @DisplayName("존재하지 않는 상품 조회 시 null을 반환한다")
    fun testGetProductByIdNotFound() {
        // Given
        val productId = "nonexistent"
        every { productRepository.findById(productId) } returns null

        // When
        val result = productUseCase.getProductById(productId)

        // Then
        assert(result == null)
    }

    @Test
    @DisplayName("상품 목록을 조회할 수 있다 (캐시 미스)")
    fun testGetProductsWithCacheMiss() {
        // Given
        val category = "전자제품"
        val sort = "price"
        val products = listOf(
            Product("prod1", "노트북", "설명1", 1_000_000L, category),
            Product("prod2", "마우스", "설명2", 50_000L, category)
        )
        val cacheKey = "products:$category:$sort"

        every { cacheService.get(cacheKey) } returns null
        every { productRepository.findAll(category, sort) } returns products
        every { cacheService.set(cacheKey, products, 60) } just Runs

        // When
        val result = productUseCase.getProducts(category, sort)

        // Then
        assert(result.size == 2)
        assert(result[0].id == "prod1")
        verify { cacheService.get(cacheKey) }
        verify { productRepository.findAll(category, sort) }
        verify { cacheService.set(cacheKey, products, 60) }
    }

    @Test
    @DisplayName("상품 목록을 조회할 수 있다 (캐시 히트)")
    fun testGetProductsWithCacheHit() {
        // Given
        val category = "전자제품"
        val sort = "price"
        val cachedProducts = listOf(
            Product("prod1", "노트북", "설명1", 1_000_000L, category)
        )
        val cacheKey = "products:$category:$sort"

        every { cacheService.get(cacheKey) } returns cachedProducts

        // When
        val result = productUseCase.getProducts(category, sort)

        // Then
        assert(result.size == 1)
        assert(result[0].id == "prod1")
        verify { cacheService.get(cacheKey) }
        verify(exactly = 0) { productRepository.findAll(any(), any()) }
        verify(exactly = 0) { cacheService.set(any(), any(), any()) }
    }

    @Test
    @DisplayName("인기 상품을 조회할 수 있다")
    fun testGetTopProducts() {
        // Given
        val limit = 5
        val allProducts = listOf(
            Product("prod1", "인기상품1", "설명1", 500_000L, "전자제품", viewCount = 100, salesCount = 50),
            Product("prod2", "인기상품2", "설명2", 300_000L, "전자제품", viewCount = 80, salesCount = 30),
            Product("prod3", "인기상품3", "설명3", 400_000L, "전자제품", viewCount = 50, salesCount = 10)
        )

        every { productRepository.findAll(null, "newest") } returns allProducts

        // When
        val result = productUseCase.getTopProducts(limit)

        // Then
        assert(result.products.size == 3)
        assert(result.products[0].rank == 1)
        assert(result.products[0].product.id == "prod1")  // 가장 높은 인기도
        verify { productRepository.findAll(null, "newest") }
    }

    @Test
    @DisplayName("재고를 확인할 수 있다 - 재고 충분")
    fun testCheckStockAvailable() {
        // Given
        val productId = "prod1"
        val quantity = 5
        val product = Product(
            id = productId,
            name = "노트북",
            description = "설명",
            price = 1_000_000L,
            category = "전자제품"
        )
        val inventory = Inventory(
            sku = productId,
            physicalStock = 100,
            reservedStock = 20,
            safetyStock = 10
        )

        every { productRepository.findById(productId) } returns product
        every { inventoryRepository.findBySku(productId) } returns inventory

        // When
        val result = productUseCase.checkStock(productId, quantity)

        // Then
        assert(result.available == true)
        assert(result.currentStock == 70) // 100 - 20 - 10
        assert(result.requested == quantity)
    }

    @Test
    @DisplayName("재고를 확인할 수 있다 - 재고 부족")
    fun testCheckStockNotAvailable() {
        // Given
        val productId = "prod1"
        val quantity = 80
        val product = Product(
            id = productId,
            name = "노트북",
            description = "설명",
            price = 1_000_000L,
            category = "전자제품"
        )
        val inventory = Inventory(
            sku = productId,
            physicalStock = 100,
            reservedStock = 20,
            safetyStock = 10
        )

        every { productRepository.findById(productId) } returns product
        every { inventoryRepository.findBySku(productId) } returns inventory

        // When
        val result = productUseCase.checkStock(productId, quantity)

        // Then
        assert(result.available == false)
        assert(result.currentStock == 70)
        assert(result.requested == quantity)
    }

    @Test
    @DisplayName("존재하지 않는 상품의 재고 확인 시 예외를 발생시킨다")
    fun testCheckStockProductNotFound() {
        // Given
        val productId = "nonexistent"
        val quantity = 5

        every { productRepository.findById(productId) } returns null

        // When & Then
        val exception = assertThrows<IllegalStateException> {
            productUseCase.checkStock(productId, quantity)
        }
        assert(exception.message?.contains("상품을 찾을 수 없습니다") ?: false)
    }

    @Test
    @DisplayName("재고 정보가 없는 상품 조회 시 예외를 발생시킨다")
    fun testCheckStockInventoryNotFound() {
        // Given
        val productId = "prod1"
        val quantity = 5
        val product = Product(
            id = productId,
            name = "노트북",
            description = "설명",
            price = 1_000_000L,
            category = "전자제품"
        )

        every { productRepository.findById(productId) } returns product
        every { inventoryRepository.findBySku(productId) } returns null

        // When & Then
        val exception = assertThrows<IllegalStateException> {
            productUseCase.checkStock(productId, quantity)
        }
        assert(exception.message?.contains("재고 정보를 찾을 수 없습니다") ?: false)
    }

    @Test
    @DisplayName("상품 조회 시 조회수가 증가한다")
    fun testViewProduct() {
        // Given
        val productId = "prod1"
        val product = Product(
            id = productId,
            name = "노트북",
            description = "고성능 노트북",
            price = 1_000_000L,
            category = "전자제품",
            viewCount = 10
        )

        every { productRepository.findById(productId) } returns product
        every { productRepository.save(any()) } returnsArgument 0

        // When
        val result = productUseCase.viewProduct(productId)

        // Then
        assert(result.id == productId)
        assert(result.viewCount == 11L) // 조회수 증가 확인
        verify { productRepository.save(any()) }
    }

    @Test
    @DisplayName("존재하지 않는 상품 조회 시 예외를 발생시킨다")
    fun testViewProductNotFound() {
        // Given
        val productId = "nonexistent"

        every { productRepository.findById(productId) } returns null

        // When & Then
        val exception = assertThrows<IllegalStateException> {
            productUseCase.viewProduct(productId)
        }
        assert(exception.message?.contains("상품을 찾을 수 없습니다") ?: false)
    }

    @Test
    @DisplayName("판매량 기록 - 판매량이 증가한다")
    fun testRecordSale() {
        // Given
        val productId = "prod1"
        val quantity = 5
        val product = Product(
            id = productId,
            name = "노트북",
            description = "고성능 노트북",
            price = 1_000_000L,
            category = "전자제품",
            salesCount = 10
        )

        every { productRepository.findById(productId) } returns product
        every { productRepository.save(any()) } returnsArgument 0

        // When
        productUseCase.recordSale(productId, quantity)

        // Then
        assert(product.salesCount == 15L) // 판매량 증가 확인
        verify { productRepository.save(any()) }
    }

    @Test
    @DisplayName("판매량 기록 - 존재하지 않는 상품 시 예외를 발생시킨다")
    fun testRecordSaleProductNotFound() {
        // Given
        val productId = "nonexistent"
        val quantity = 5

        every { productRepository.findById(productId) } returns null

        // When & Then
        val exception = assertThrows<IllegalStateException> {
            productUseCase.recordSale(productId, quantity)
        }
        assert(exception.message?.contains("상품을 찾을 수 없습니다") ?: false)
    }

    @Test
    @DisplayName("기간별 최상위 판매 상품 조회")
    fun testGetTopSellingProducts() {
        // Given
        val days = 7
        val limit = 5
        val topSellingProducts = listOf(
            Product("prod1", "베스트셀러1", "설명1", 100_000L, "의류", viewCount = 50, salesCount = 100),
            Product("prod2", "베스트셀러2", "설명2", 80_000L, "의류", viewCount = 40, salesCount = 80),
            Product("prod3", "베스트셀러3", "설명3", 60_000L, "의류", viewCount = 30, salesCount = 60)
        )

        every { productRepository.findTopSelling(any(), limit) } returns topSellingProducts

        // When
        val result = productUseCase.getTopSellingProducts(days, limit)

        // Then
        assert(result.products.size == 3)
        assert(result.products[0].rank == 1)
        assert(result.products[0].product.id == "prod1")
        assert(result.products[0].salesCount == 100L)
        verify { productRepository.findTopSelling(any(), limit) }
    }

    @Test
    @DisplayName("기간별 최상위 판매 상품 조회 - 결과 없음")
    fun testGetTopSellingProductsEmpty() {
        // Given
        val days = 7
        val limit = 5

        every { productRepository.findTopSelling(any(), limit) } returns emptyList()

        // When
        val result = productUseCase.getTopSellingProducts(days, limit)

        // Then
        assert(result.products.isEmpty())
        verify { productRepository.findTopSelling(any(), limit) }
    }
}
