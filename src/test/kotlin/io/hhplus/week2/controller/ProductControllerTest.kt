package io.hhplus.week2.controller

import io.hhplus.week2.application.ProductUseCase
import io.hhplus.week2.application.InventoryUseCase
import io.hhplus.week2.domain.Product
import io.hhplus.week2.domain.Inventory
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@WebMvcTest(ProductController::class)
@DisplayName("ProductController 통합 테스트")
class ProductControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var productUseCase: ProductUseCase

    @MockkBean
    private lateinit var inventoryUseCase: InventoryUseCase

    @Test
    @DisplayName("상품 목록을 조회할 수 있다")
    fun testGetProducts() {
        // Given
        val products = listOf(
            Product(
                id = "prod1",
                name = "노트북",
                description = "고성능 노트북",
                price = 1_000_000L,
                category = "전자제품"
            ),
            Product(
                id = "prod2",
                name = "마우스",
                description = "무선 마우스",
                price = 50_000L,
                category = "전자제품"
            )
        )

        every { productUseCase.getProducts(any(), any()) } returns products

        // When & Then
        mockMvc.perform(get("/api/v1/products"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data").isArray)
            .andExpect(jsonPath("$.data[0].id").value("prod1"))
            .andExpect(jsonPath("$.data[0].name").value("노트북"))
            .andExpect(jsonPath("$.data[1].id").value("prod2"))
            .andExpect(jsonPath("$.pagination.total").value(2))
    }

    @Test
    @DisplayName("카테고리로 상품을 필터링할 수 있다")
    fun testGetProductsByCategory() {
        // Given
        val products = listOf(
            Product(
                id = "prod1",
                name = "노트북",
                description = "고성능 노트북",
                price = 1_000_000L,
                category = "전자제품"
            )
        )

        every { productUseCase.getProducts("전자제품", any()) } returns products

        // When & Then
        mockMvc.perform(
            get("/api/v1/products")
                .param("category", "전자제품")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data[0].category").value("전자제품"))
    }

    @Test
    @DisplayName("가격 범위로 상품을 필터링할 수 있다")
    fun testGetProductsByPriceRange() {
        // Given
        val products = listOf(
            Product(
                id = "prod1",
                name = "노트북",
                description = "고성능 노트북",
                price = 1_000_000L,
                category = "전자제품"
            ),
            Product(
                id = "prod2",
                name = "마우스",
                description = "무선 마우스",
                price = 50_000L,
                category = "전자제품"
            )
        )

        every { productUseCase.getProducts(null, any()) } returns products

        // When & Then
        mockMvc.perform(
            get("/api/v1/products")
                .param("minPrice", "100000")
                .param("maxPrice", "2000000")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data").isArray)
            .andExpect(jsonPath("$.data[0].basePrice").value(1_000_000L))
    }

    @Test
    @DisplayName("상품 상세 정보를 조회할 수 있다")
    fun testGetProductDetail() {
        // Given
        val productId = "prod1"
        val product = Product(
            id = productId,
            name = "노트북",
            description = "고성능 노트북",
            price = 1_000_000L,
            category = "전자제품"
        )
        val inventory = Inventory(
            sku = productId,
            physicalStock = 100,
            reservedStock = 20,
            safetyStock = 10
        )

        every { productUseCase.getProductById(productId) } returns product
        every { inventoryUseCase.getInventoryBySku(productId) } returns inventory

        // When & Then
        mockMvc.perform(get("/api/v1/products/{productId}", productId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(productId))
            .andExpect(jsonPath("$.name").value("노트북"))
            .andExpect(jsonPath("$.basePrice").value(1_000_000L))
            .andExpect(jsonPath("$.variants[0].stock").value(70)) // 100 - 20 - 10
            .andExpect(jsonPath("$.variants[0].stockStatus").value("IN_STOCK"))
    }

    @Test
    @DisplayName("존재하지 않는 상품 조회 시 404를 반환한다")
    fun testGetProductDetailNotFound() {
        // Given
        val productId = "nonexistent"
        every { productUseCase.getProductById(productId) } returns null

        // When & Then
        mockMvc.perform(get("/api/v1/products/{productId}", productId))
            .andExpect(status().isNotFound)
    }

    @Test
    @DisplayName("재고가 없는 상품의 상세 정보를 조회할 수 있다")
    fun testGetProductDetailOutOfStock() {
        // Given
        val productId = "prod1"
        val product = Product(
            id = productId,
            name = "노트북",
            description = "고성능 노트북",
            price = 1_000_000L,
            category = "전자제품"
        )
        val inventory = Inventory(
            sku = productId,
            physicalStock = 100,
            reservedStock = 90,
            safetyStock = 10
        )

        every { productUseCase.getProductById(productId) } returns product
        every { inventoryUseCase.getInventoryBySku(productId) } returns inventory

        // When & Then
        mockMvc.perform(get("/api/v1/products/{productId}", productId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.variants[0].stock").value(0))
            .andExpect(jsonPath("$.variants[0].stockStatus").value("OUT_OF_STOCK"))
    }

    @Test
    @DisplayName("상품의 변량 목록을 조회할 수 있다")
    fun testGetVariants() {
        // Given
        val productId = "prod1"
        val product = Product(
            id = productId,
            name = "노트북",
            description = "고성능 노트북",
            price = 1_000_000L,
            category = "전자제품"
        )
        val inventory = Inventory(
            sku = productId,
            physicalStock = 100,
            reservedStock = 20,
            safetyStock = 10
        )

        every { productUseCase.getProductById(productId) } returns product
        every { inventoryUseCase.getInventoryBySku(productId) } returns inventory

        // When & Then
        mockMvc.perform(get("/api/v1/products/{productId}/variants", productId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$[0].sku").value("SKU-$productId-001"))
            .andExpect(jsonPath("$[0].stock").value(70))
    }

    @Test
    @DisplayName("재고 있는 변량만 필터링할 수 있다")
    fun testGetVariantsInStockOnly() {
        // Given
        val productId = "prod1"
        val product = Product(
            id = productId,
            name = "노트북",
            description = "고성능 노트북",
            price = 1_000_000L,
            category = "전자제품"
        )
        val inventory = Inventory(
            sku = productId,
            physicalStock = 100,
            reservedStock = 20,
            safetyStock = 10
        )

        every { productUseCase.getProductById(productId) } returns product
        every { inventoryUseCase.getInventoryBySku(productId) } returns inventory

        // When & Then
        mockMvc.perform(
            get("/api/v1/products/{productId}/variants", productId)
                .param("inStock", "true")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$[0].stock").value(70))
    }

    @Test
    @DisplayName("상품을 검색할 수 있다")
    fun testSearchProducts() {
        // Given
        val products = listOf(
            Product(
                id = "prod1",
                name = "노트북",
                description = "고성능 노트북",
                price = 1_000_000L,
                category = "전자제품"
            )
        )

        every { productUseCase.getProducts(null, any()) } returns products

        // When & Then
        mockMvc.perform(
            get("/api/v1/products/search")
                .param("q", "노트북")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data").isArray)
            .andExpect(jsonPath("$.data[0].name").value("노트북"))
    }

    @Test
    @DisplayName("검색 결과가 없으면 빈 배열을 반환한다")
    fun testSearchProductsNoResult() {
        // Given
        val products = listOf(
            Product(
                id = "prod1",
                name = "노트북",
                description = "고성능 노트북",
                price = 1_000_000L,
                category = "전자제품"
            )
        )

        every { productUseCase.getProducts(null, any()) } returns products

        // When & Then
        mockMvc.perform(
            get("/api/v1/products/search")
                .param("q", "존재하지않는상품")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data").isArray)
            .andExpect(jsonPath("$.data").isEmpty)
            .andExpect(jsonPath("$.pagination.total").value(0))
    }
}
