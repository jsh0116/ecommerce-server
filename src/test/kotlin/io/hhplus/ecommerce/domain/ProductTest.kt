package io.hhplus.ecommerce.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

@DisplayName("Product 도메인 모델 테스트")
class ProductTest {

    @Nested
    @DisplayName("calculatePrice 테스트")
    inner class CalculatePriceTest {
        @Test
        fun `가격에 수량을 곱한 값을 반환한다`() {
            val product = Product(id = 1L, name = "상품", description = null, price = 10000L, category = "의류")
            assertThat(product.calculatePrice(3)).isEqualTo(30000L)
        }

        @Test
        fun `수량이 0이면 예외 발생`() {
            val product = Product(id = 1L, name = "상품", description = null, price = 10000L, category = "의류")
            assertThrows<IllegalArgumentException> { product.calculatePrice(0) }
        }

        @Test
        fun `수량이 음수이면 예외 발생`() {
            val product = Product(id = 1L, name = "상품", description = null, price = 10000L, category = "의류")
            assertThrows<IllegalArgumentException> { product.calculatePrice(-5) }
        }
    }

    @Nested
    @DisplayName("isValidPrice 테스트")
    inner class IsValidPriceTest {
        @Test
        fun `가격이 0보다 크면 true`() {
            val product = Product(id = 1L, name = "상품", description = null, price = 10000L, category = "의류")
            assertThat(product.isValidPrice()).isTrue
        }

        @Test
        fun `가격이 0이면 false`() {
            val product = Product(id = 1L, name = "상품", description = null, price = 0L, category = "의류")
            assertThat(product.isValidPrice()).isFalse
        }

        @Test
        fun `가격이 음수이면 false`() {
            val product = Product(id = 1L, name = "상품", description = null, price = -1000L, category = "의류")
            assertThat(product.isValidPrice()).isFalse
        }
    }

    @Nested
    @DisplayName("incrementViewCount 테스트")
    inner class IncrementViewCountTest {
        @Test
        fun `조회수가 1 증가한다`() {
            val product = Product(id = 1L, name = "상품", description = null, price = 10000L, category = "의류", viewCount = 0L)
            product.incrementViewCount()
            assertThat(product.viewCount).isEqualTo(1L)
        }

        @Test
        fun `여러 번 호출 시 누적된다`() {
            val product = Product(id = 1L, name = "상품", description = null, price = 10000L, category = "의류", viewCount = 5L)
            product.incrementViewCount()
            product.incrementViewCount()
            product.incrementViewCount()
            assertThat(product.viewCount).isEqualTo(8L)
        }
    }

    @Nested
    @DisplayName("incrementSalesCount 테스트")
    inner class IncrementSalesCountTest {
        @Test
        fun `판매량이 증가한다`() {
            val product = Product(id = 1L, name = "상품", description = null, price = 10000L, category = "의류", salesCount = 0L)
            product.incrementSalesCount(5)
            assertThat(product.salesCount).isEqualTo(5L)
        }

        @Test
        fun `여러 번 호출 시 누적된다`() {
            val product = Product(id = 1L, name = "상품", description = null, price = 10000L, category = "의류", salesCount = 10L)
            product.incrementSalesCount(3)
            product.incrementSalesCount(2)
            assertThat(product.salesCount).isEqualTo(15L)
        }

        @Test
        fun `수량이 0이면 예외 발생`() {
            val product = Product(id = 1L, name = "상품", description = null, price = 10000L, category = "의류", salesCount = 0L)
            assertThrows<IllegalArgumentException> { product.incrementSalesCount(0) }
        }

        @Test
        fun `수량이 음수이면 예외 발생`() {
            val product = Product(id = 1L, name = "상품", description = null, price = 10000L, category = "의류", salesCount = 0L)
            assertThrows<IllegalArgumentException> { product.incrementSalesCount(-5) }
        }
    }

    @Nested
    @DisplayName("calculatePopularityScore 테스트")
    inner class CalculatePopularityScoreTest {
        @Test
        fun `인기도 = 판매수 * 10 + 조회수`() {
            val product = Product(id = 1L, name = "상품", description = null, price = 10000L, category = "의류", salesCount = 10L, viewCount = 50L)
            assertThat(product.calculatePopularityScore()).isEqualTo(150L)
        }

        @Test
        fun `판매가 없으면 조회수만 반영`() {
            val product = Product(id = 1L, name = "상품", description = null, price = 10000L, category = "의류", salesCount = 0L, viewCount = 100L)
            assertThat(product.calculatePopularityScore()).isEqualTo(100L)
        }

        @Test
        fun `조회가 없으면 판매만 반영`() {
            val product = Product(id = 1L, name = "상품", description = null, price = 10000L, category = "의류", salesCount = 20L, viewCount = 0L)
            assertThat(product.calculatePopularityScore()).isEqualTo(200L)
        }

        @Test
        fun `판매와 조회가 모두 없으면 0`() {
            val product = Product(id = 1L, name = "상품", description = null, price = 10000L, category = "의류", salesCount = 0L, viewCount = 0L)
            assertThat(product.calculatePopularityScore()).isEqualTo(0L)
        }

        @Test
        fun `많은 판매와 조회 시 높은 점수`() {
            val product = Product(id = 1L, name = "상품", description = null, price = 10000L, category = "의류", salesCount = 100L, viewCount = 5000L)
            assertThat(product.calculatePopularityScore()).isEqualTo(6000L)
        }
    }
}
