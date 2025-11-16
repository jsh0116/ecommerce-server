package io.hhplus.ecommerce.presentation.dto

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Tag
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("DTO 직렬화 테스트")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Tag("integration")
@org.springframework.test.context.ActiveProfiles("test")
class DtoSerializationTest {

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    fun `ProductDto를 JSON으로 직렬화할 수 있다`() {
        // Given
        val productDto = ProductDto(
            id = "1",
            name = "청바지",
            brand = "LEVI'S",
            category = "바지",
            basePrice = 50000,
            salePrice = 45000,
            discountRate = 10,
            images = listOf("https://example.com/image.jpg"),
            variantCount = 5,
            rating = 4.5,
            reviewCount = 100,
            tags = listOf("청바지", "남성")
        )

        // When
        val json = objectMapper.writeValueAsString(productDto)
        val deserialized = objectMapper.readValue(json, ProductDto::class.java)

        // Then
        assertThat(deserialized.id).isEqualTo("1")
        assertThat(deserialized.name).isEqualTo("청바지")
        assertThat(deserialized.basePrice).isEqualTo(50000)
        assertThat(deserialized.tags).hasSize(2)
    }

    @Test
    fun `ProductListResponse를 JSON으로 직렬화할 수 있다`() {
        // Given
        val productDtos = listOf(
            ProductDto(
                id = "1", name = "청바지", brand = "LEVI'S", category = "바지",
                basePrice = 50000, salePrice = 45000, discountRate = 10,
                images = emptyList(), variantCount = 1, rating = 4.5, reviewCount = 100, tags = emptyList()
            )
        )
        val response = ProductListResponse(
            data = productDtos,
            pagination = PaginationInfo(page = 1, limit = 20, total = 1, totalPages = 1)
        )

        // When
        val json = objectMapper.writeValueAsString(response)
        val deserialized = objectMapper.readValue(json, ProductListResponse::class.java)

        // Then
        assertThat(deserialized.data).hasSize(1)
        assertThat(deserialized.pagination.page).isEqualTo(1)
        assertThat(deserialized.pagination.total).isEqualTo(1)
    }

    @Test
    fun `ProductDetailResponse를 JSON으로 직렬화할 수 있다`() {
        // Given
        val variant = ProductVariantDto(
            id = "var-1", sku = "SKU-001", color = "Black", colorHex = "#000000",
            size = "M", length = "Regular", price = 50000, originalPrice = 50000,
            stock = 100, stockStatus = "IN_STOCK"
        )
        val response = ProductDetailResponse(
            id = "1", name = "청바지", brand = "LEVI'S", category = "바지",
            description = "편한 청바지", basePrice = 50000, salePrice = 50000,
            discountRate = 0, images = listOf("https://example.com/image.jpg"),
            variants = listOf(variant), rating = 4.5, reviewCount = 100
        )

        // When
        val json = objectMapper.writeValueAsString(response)
        val deserialized = objectMapper.readValue(json, ProductDetailResponse::class.java)

        // Then
        assertThat(deserialized.name).isEqualTo("청바지")
        assertThat(deserialized.variants).hasSize(1)
        assertThat(deserialized.variants[0].sku).isEqualTo("SKU-001")
        assertThat(deserialized.variants[0].stock).isEqualTo(100)
    }

    @Test
    fun `ProductVariantDto를 JSON으로 직렬화할 수 있다`() {
        // Given
        val variant = ProductVariantDto(
            id = "var-1", sku = "SKU-001", color = "White", colorHex = "#FFFFFF",
            size = "L", length = "Regular", price = 45000, originalPrice = 50000,
            stock = 50, stockStatus = "IN_STOCK"
        )

        // When
        val json = objectMapper.writeValueAsString(variant)
        val deserialized = objectMapper.readValue(json, ProductVariantDto::class.java)

        // Then
        assertThat(deserialized.color).isEqualTo("White")
        assertThat(deserialized.size).isEqualTo("L")
        assertThat(deserialized.price).isEqualTo(45000)
    }

    @Test
    fun `PaginationInfo를 JSON으로 직렬화할 수 있다`() {
        // Given
        val pagination = PaginationInfo(page = 2, limit = 20, total = 100, totalPages = 5)

        // When
        val json = objectMapper.writeValueAsString(pagination)
        val deserialized = objectMapper.readValue(json, PaginationInfo::class.java)

        // Then
        assertThat(deserialized.page).isEqualTo(2)
        assertThat(deserialized.limit).isEqualTo(20)
        assertThat(deserialized.total).isEqualTo(100)
        assertThat(deserialized.totalPages).isEqualTo(5)
    }
}
