package io.hhplus.ecommerce.application.usecases

import io.hhplus.ecommerce.domain.Inventory
import io.hhplus.ecommerce.domain.Product
import io.hhplus.ecommerce.infrastructure.repositories.InventoryRepository
import io.hhplus.ecommerce.infrastructure.repositories.ProductRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

@DisplayName("ProductUseCase 테스트")
class ProductUseCaseTest {

    private val productRepository = mockk<ProductRepository>()
    private val inventoryRepository = mockk<InventoryRepository>()
    // Spring Cache 어노테이션을 사용하므로 CacheService는 제거
    private val useCase = ProductUseCase(productRepository, inventoryRepository)

    @Nested
    @DisplayName("상품 조회 테스트")
    inner class GetProductTest {
        @Test
        fun `상품을 ID로 조회할 수 있다`() {
            // Given
            val product = Product(id = 1L, name = "테스트 상품", description = null, price = 50000L, category = "의류")
            every { productRepository.findById(1L) } returns product

            // When
            val result = useCase.getProductById(1L)

            // Then
            assertThat(result).isNotNull
            assertThat(result?.name).isEqualTo("테스트 상품")
            assertThat(result?.price).isEqualTo(50000L)
        }

        @Test
        fun `존재하지 않는 상품은 null을 반환한다`() {
            // Given
            every { productRepository.findById(999L) } returns null

            // When
            val result = useCase.getProductById(999L)

            // Then
            assertThat(result).isNull()
        }
    }

    @Nested
    @DisplayName("상품 목록 조회 테스트")
    inner class GetProductsTest {
        @Test
        fun `전체 상품을 조회할 수 있다`() {
            // Given
            val products = listOf(
                Product(id = 1L, name = "상품1", description = null, price = 10000L, category = "의류"),
                Product(id = 2L, name = "상품2", description = null, price = 20000L, category = "신발")
            )
            every { productRepository.findAll(null, "newest") } returns products

            // When
            val result = useCase.getProducts(null, "newest")

            // Then
            assertThat(result).hasSize(2)
            assertThat(result.map { it.name }).containsExactlyInAnyOrder("상품1", "상품2")
        }

        @Test
        fun `카테고리별 상품을 조회할 수 있다`() {
            // Given
            val categoryProducts = listOf(
                Product(id = 1L, name = "셔츠", description = null, price = 30000L, category = "의류")
            )
            every { productRepository.findAll("의류", "newest") } returns categoryProducts

            // When
            val result = useCase.getProducts("의류", "newest")

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].category).isEqualTo("의류")
        }

        @Test
        fun `캐시 미스 시 DB에서 조회한다`() {
            // Given
            val products = listOf(
                Product(id = 1L, name = "상품", description = null, price = 50000L, category = "의류")
            )
            every { productRepository.findAll(null, "newest") } returns products

            // When
            val result = useCase.getProducts(null, "newest")

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].name).isEqualTo("상품")
            verify { productRepository.findAll(null, "newest") }
        }
    }

    @Nested
    @DisplayName("상품 조회 (조회수 증가) 테스트")
    inner class ViewProductTest {
        @Test
        fun `상품을 조회하면 조회수가 증가한다`() {
            // Given
            val product = Product(id = 1L, name = "테스트", description = null, price = 50000L, category = "의류", viewCount = 0L)
            every { productRepository.findById(1L) } returns product
            every { productRepository.save(product) } returns Unit

            // When
            val result = useCase.viewProduct(1L)

            // Then
            assertThat(result.viewCount).isEqualTo(1L)
            verify { productRepository.save(product) }
        }

        @Test
        fun `존재하지 않는 상품을 조회하면 예외가 발생한다`() {
            // Given
            every { productRepository.findById(999L) } returns null

            // When/Then
            assertThatThrownBy { useCase.viewProduct(999L) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("상품을 찾을 수 없습니다")
        }
    }

    @Nested
    @DisplayName("판매량 기록 테스트")
    inner class RecordSaleTest {
        @Test
        fun `상품 판매 시 판매량이 증가한다`() {
            // Given
            val product = Product(id = 1L, name = "상품", description = null, price = 50000L, category = "의류", salesCount = 0L)
            every { productRepository.findById(1L) } returns product
            every { productRepository.save(product) } returns Unit

            // When
            useCase.recordSale(1L, 5)

            // Then
            assertThat(product.salesCount).isEqualTo(5L)
            verify { productRepository.save(product) }
        }

        @Test
        fun `여러 번 판매를 기록하면 누적된다`() {
            // Given
            val product = Product(id = 1L, name = "상품", description = null, price = 50000L, category = "의류", salesCount = 0L)
            every { productRepository.findById(1L) } returns product
            every { productRepository.save(product) } returns Unit

            // When
            useCase.recordSale(1L, 3)
            useCase.recordSale(1L, 7)

            // Then
            assertThat(product.salesCount).isEqualTo(10L)
        }

        @Test
        fun `존재하지 않는 상품의 판매를 기록하면 예외가 발생한다`() {
            // Given
            every { productRepository.findById(999L) } returns null

            // When/Then
            assertThatThrownBy { useCase.recordSale(999L, 5) }
                .isInstanceOf(IllegalStateException::class.java)
        }
    }

    @Nested
    @DisplayName("인기 상품 조회 테스트")
    inner class GetTopProductsTest {
        @Test
        fun `인기 상품을 조회할 수 있다`() {
            // Given
            val products = listOf(
                Product(id = 1L, name = "인기1", description = null, price = 50000L, category = "의류", viewCount = 100L, salesCount = 50L),
                Product(id = 2L, name = "인기2", description = null, price = 40000L, category = "신발", viewCount = 80L, salesCount = 30L),
                Product(id = 3L, name = "인기3", description = null, price = 30000L, category = "악세서리", viewCount = 60L, salesCount = 10L)
            )
            every { productRepository.findAll(null, "newest") } returns products

            // When
            val result = useCase.getTopProducts(3)

            // Then
            assertThat(result.products).hasSize(3)
            assertThat(result.products[0].product.id).isEqualTo(1L)
            assertThat(result.products[0].rank).isEqualTo(1)
            assertThat(result.products[0].popularityScore).isEqualTo(600L)
        }

        @Test
        fun `제한된 개수만 상위 상품을 반환한다`() {
            // Given
            val products = (1..10).map { i ->
                Product(id = i.toLong(), name = "상품$i", description = null, price = 50000L, category = "의류",
                    viewCount = (100-i*5).toLong(), salesCount = (50-i*3).toLong())
            }
            every { productRepository.findAll(null, "newest") } returns products

            // When
            val result = useCase.getTopProducts(5)

            // Then
            assertThat(result.products).hasSize(5)
        }
    }

    @Nested
    @DisplayName("최근 판매 상품 조회 테스트")
    inner class GetTopSellingProductsTest {
        @Test
        fun `최근 판매 상품을 조회할 수 있다`() {
            // Given
            val sellingProducts = listOf(
                Product(id = 1L, name = "베스트셀러1", description = null, price = 50000L, category = "의류", salesCount = 100L),
                Product(id = 2L, name = "베스트셀러2", description = null, price = 40000L, category = "신발", salesCount = 80L)
            )
            every { productRepository.findTopSelling(any(), 5) } returns sellingProducts

            // When
            val result = useCase.getTopSellingProducts(7, 5)

            // Then
            assertThat(result.products).hasSize(2)
            assertThat(result.products[0].product.salesCount).isEqualTo(100L)
        }
    }

    @Nested
    @DisplayName("재고 확인 테스트")
    inner class CheckStockTest {
        @Test
        fun `상품의 재고 확인을 할 수 있다`() {
            // Given
            val product = Product(id = 1L, name = "상품", description = null, price = 50000L, category = "의류")
            val inventory = Inventory(sku = "1", physicalStock = 100, reservedStock = 20)
            every { productRepository.findById(1L) } returns product
            every { inventoryRepository.findBySku("1") } returns inventory

            // When
            val result = useCase.checkStock(1L, 50)

            // Then
            assertThat(result.available).isTrue
            assertThat(result.currentStock).isEqualTo(80)
            assertThat(result.requested).isEqualTo(50)
        }

        @Test
        fun `재고가 부족하면 available이 false이다`() {
            // Given
            val product = Product(id = 1L, name = "상품", description = null, price = 50000L, category = "의류")
            val inventory = Inventory(sku = "1", physicalStock = 30, reservedStock = 20)
            every { productRepository.findById(1L) } returns product
            every { inventoryRepository.findBySku("1") } returns inventory

            // When
            val result = useCase.checkStock(1L, 50)

            // Then
            assertThat(result.available).isFalse
            assertThat(result.currentStock).isEqualTo(10)
            assertThat(result.requested).isEqualTo(50)
        }

        @Test
        fun `존재하지 않는 상품의 재고 확인은 예외가 발생한다`() {
            // Given
            every { productRepository.findById(999L) } returns null

            // When/Then
            assertThatThrownBy { useCase.checkStock(999L, 10) }
                .isInstanceOf(IllegalStateException::class.java)
        }
    }
}
