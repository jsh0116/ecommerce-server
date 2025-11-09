package io.hhplus.ecommerce.domain

import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class SimpleProductTest {

    @Test
    fun testProductCanBeCreated() {
        val product = Product(
            id = "P001",
            name = "노트북",
            description = "고성능 노트북",
            price = 890000,
            category = "electronics",
            createdAt = LocalDateTime.now()
        )

        assert(product.id == "P001")
        assert(product.name == "노트북")
        assert(product.price == 890000L)
        assert(product.category == "electronics")
    }

    @Test
    fun testPriceCalculation() {
        val product = Product(
            id = "P001",
            name = "노트북",
            description = "고성능 노트북",
            price = 890000,
            category = "electronics"
        )

        val totalPrice = product.calculatePrice(3)
        assert(totalPrice == 2_670_000L)
    }

    @Test
    fun testPriceValidation() {
        val product = Product(
            id = "P001",
            name = "노트북",
            description = "고성능 노트북",
            price = 890000,
            category = "electronics"
        )

        assert(product.isValidPrice())
    }
}
