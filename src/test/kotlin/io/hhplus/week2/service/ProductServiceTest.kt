package io.hhplus.week2.service

import io.hhplus.week2.repository.mock.ProductRepositoryMock
import io.hhplus.week2.service.impl.ProductServiceImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("ProductServiceImpl 테스트")
class ProductServiceTest {

    private lateinit var productRepository: ProductRepositoryMock
    private lateinit var productService: ProductService

    @BeforeEach
    fun setUp() {
        productRepository = ProductRepositoryMock()
        productService = ProductServiceImpl(productRepository)
    }

    @Test
    @DisplayName("전체 상품을 페이지네이션으로 조회할 수 있다")
    fun testGetAllProducts() {
        // when
        val result = productService.getAllProducts(page = 1, limit = 20)

        // then
        assertThat(result.first).isNotEmpty
        assertThat(result.second).isGreaterThan(0)
        // MockData가 있으므로 prod_001이 있어야 함
        assertThat(result.first).anyMatch { it.id == "prod_001" }
    }

    @Test
    @DisplayName("페이지 번호를 이용해 올바른 오프셋으로 조회할 수 있다")
    fun testGetAllProductsWithPagination() {
        // when
        val result = productService.getAllProducts(page = 1, limit = 1)

        // then
        assertThat(result.first).hasSize(1)
        assertThat(result.second).isGreaterThanOrEqualTo(1)
    }

    @Test
    @DisplayName("상품 ID로 상품을 조회할 수 있다")
    fun testGetProductById() {
        // when
        val result = productService.getProductById("prod_001")

        // then
        assertThat(result).isNotNull
        assertThat(result?.id).isEqualTo("prod_001")
        assertThat(result?.name).isEqualTo("슬림핏 청바지")
    }

    @Test
    @DisplayName("존재하지 않는 상품 ID로 조회하면 null을 반환한다")
    fun testGetProductByIdNotFound() {
        // when
        val result = productService.getProductById("nonexistent_id")

        // then
        assertThat(result).isNull()
    }

    @Test
    @DisplayName("상품 ID로 변량 목록을 조회할 수 있다")
    fun testGetProductVariants() {
        // when
        val result = productService.getProductVariants("prod_001")

        // then
        assertThat(result).isNotEmpty
        assertThat(result).anyMatch { it.productId == "prod_001" }
    }

    @Test
    @DisplayName("변량 ID로 변량을 조회할 수 있다")
    fun testGetVariantById() {
        // when
        val result = productService.getVariantById("var_001")

        // then
        assertThat(result).isNotNull
        assertThat(result?.id).isEqualTo("var_001")
        assertThat(result?.sku).isEqualTo("LEVI-501-BLK-32-REG")
    }

    @Test
    @DisplayName("존재하지 않는 변량 ID로 조회하면 null을 반환한다")
    fun testGetVariantByIdNotFound() {
        // when
        val result = productService.getVariantById("nonexistent_variant")

        // then
        assertThat(result).isNull()
    }
}
