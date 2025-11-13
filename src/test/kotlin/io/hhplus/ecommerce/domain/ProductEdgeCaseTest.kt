package io.hhplus.ecommerce.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("Product 엣지 케이스 테스트")
class ProductEdgeCaseTest {

    @Nested
    @DisplayName("상품 가격 테스트")
    inner class ProductPriceTest {
        @Test
        fun `상품의 가격이 올바르게 설정된다`() {
            // Given
            val product = Product(id = 1L, name = "청바지", description = null, price = 50000L, category = "바지")

            // Then
            assertThat(product.price).isEqualTo(50000L)
        }

        @Test
        fun `0원 상품을 생성할 수 있다`() {
            // Given
            val product = Product(id = 1L, name = "무료 상품", description = null, price = 0L, category = "샘플")

            // Then
            assertThat(product.price).isEqualTo(0L)
        }

        @Test
        fun `높은 가격의 상품을 생성할 수 있다`() {
            // Given
            val product = Product(id = 1L, name = "럭셔리 상품", description = null, price = 10000000L, category = "명품")

            // Then
            assertThat(product.price).isEqualTo(10000000L)
        }
    }

    @Nested
    @DisplayName("상품 카테고리 테스트")
    inner class ProductCategoryTest {
        @Test
        fun `상품의 카테고리를 올바르게 설정할 수 있다`() {
            // Given
            val categories = listOf("바지", "상의", "신발", "악세서리", "외출용품")

            // When & Then
            for (category in categories) {
                val product = Product(id = 1L, name = "상품", description = null, price = 50000L, category = category)
                assertThat(product.category).isEqualTo(category)
            }
        }

        @Test
        fun `특수 문자가 포함된 카테고리를 생성할 수 있다`() {
            // Given
            val product = Product(id = 1L, name = "상품", description = null, price = 50000L, category = "대/소/분류")

            // Then
            assertThat(product.category).isEqualTo("대/소/분류")
        }
    }

    @Nested
    @DisplayName("상품 설명 테스트")
    inner class ProductDescriptionTest {
        @Test
        fun `상품 설명이 없을 수 있다`() {
            // Given
            val product = Product(id = 1L, name = "상품", description = null, price = 50000L, category = "바지")

            // Then
            assertThat(product.description).isNull()
        }

        @Test
        fun `긴 상품 설명을 저장할 수 있다`() {
            // Given
            val longDescription = "이 상품은 ".repeat(100)
            val product = Product(id = 1L, name = "상품", description = longDescription, price = 50000L, category = "바지")

            // Then
            assertThat(product.description).isEqualTo(longDescription)
            assertThat(product.description?.length).isGreaterThan(500)
        }

        @Test
        fun `특수 문자가 포함된 설명을 저장할 수 있다`() {
            // Given
            val description = "10% 할인! 🎁 한정판 상품입니다. <특수문자> & \"따옴표\""
            val product = Product(id = 1L, name = "상품", description = description, price = 50000L, category = "바지")

            // Then
            assertThat(product.description).isEqualTo(description)
        }
    }

    @Nested
    @DisplayName("상품 통계 테스트")
    inner class ProductStatisticsTest {
        @Test
        fun `상품의 조회수를 증가시킬 수 있다`() {
            // Given
            val product = Product(id = 1L, name = "상품", description = null, price = 50000L, category = "바지", viewCount = 0L)

            // When
            product.viewCount = 100L

            // Then
            assertThat(product.viewCount).isEqualTo(100L)
        }

        @Test
        fun `상품의 판매량을 증가시킬 수 있다`() {
            // Given
            val product = Product(id = 1L, name = "상품", description = null, price = 50000L, category = "바지", salesCount = 0L)

            // When
            product.salesCount = 50L

            // Then
            assertThat(product.salesCount).isEqualTo(50L)
        }

        @Test
        fun `상품의 통계를 누적할 수 있다`() {
            // Given
            val product = Product(id = 1L, name = "상품", description = null, price = 50000L, category = "바지", viewCount = 1000L, salesCount = 500L)

            // Then
            assertThat(product.viewCount).isEqualTo(1000L)
            assertThat(product.salesCount).isEqualTo(500L)
        }
    }
}
