package io.hhplus.week2.application

import io.hhplus.week2.domain.Product
import io.hhplus.week2.domain.Inventory
import io.hhplus.week2.repository.ProductRepository
import io.hhplus.week2.repository.InventoryRepository
import io.hhplus.week2.infrastructure.cache.CacheService
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

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
        val days = 7
        val limit = 5
        val topProducts = listOf(
            Product("prod1", "인기상품1", "설명1", 500_000L, "전자제품"),
            Product("prod2", "인기상품2", "설명2", 300_000L, "전자제품")
        )

        every { productRepository.findTopSelling(any(), limit) } returns topProducts

        // When
        val result = productUseCase.getTopProducts(days, limit)

        // Then
        assert(result.period == "7days")
        assert(result.products.size == 2)
        assert(result.products[0].id == "prod1")
        verify { productRepository.findTopSelling(any(), limit) }
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
}
