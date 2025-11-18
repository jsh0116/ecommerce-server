package io.hhplus.ecommerce.domain

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("Product 도메인 테스트")
class ProductTest {

    @Test
    @DisplayName("가격을 계산할 수 있다")
    fun testCalculatePrice() {
        // Given
        val product = Product(
            id = "P001",
            name = "노트북",
            description = "고급 노트북",
            price = 1_000_000L,
            category = "전자제품"
        )

        // When
        val totalPrice = product.calculatePrice(3)

        // Then
        assert(totalPrice == 3_000_000L)
    }

    @Test
    @DisplayName("수량이 0 이하면 예외를 발생시킨다")
    fun testCalculatePriceWithInvalidQuantity() {
        // Given
        val product = Product(
            id = "P001",
            name = "노트북",
            description = "고급 노트북",
            price = 1_000_000L,
            category = "전자제품"
        )

        // When & Then
        assertThrows<IllegalArgumentException> {
            product.calculatePrice(0)
        }

        assertThrows<IllegalArgumentException> {
            product.calculatePrice(-1)
        }
    }

    @Test
    @DisplayName("가격 유효성을 검증할 수 있다")
    fun testIsValidPrice() {
        // Given
        val validProduct = Product(
            id = "P001",
            name = "노트북",
            description = "고급 노트북",
            price = 1_000_000L,
            category = "전자제품"
        )

        val invalidProduct = Product(
            id = "P002",
            name = "무료 상품",
            description = "무료",
            price = 0L,
            category = "기타"
        )

        // When & Then
        assert(validProduct.isValidPrice())
        assert(!invalidProduct.isValidPrice())
    }
}
