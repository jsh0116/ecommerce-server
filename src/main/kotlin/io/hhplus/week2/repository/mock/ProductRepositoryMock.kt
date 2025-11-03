package io.hhplus.week2.repository.mock

import io.hhplus.week2.domain.Product
import io.hhplus.week2.domain.ProductVariant
import io.hhplus.week2.domain.StockStatus
import io.hhplus.week2.repository.ProductRepository
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

/**
 * ProductRepository의 메모리 기반 구현체
 * MockData로 동작합니다.
 */
@Repository
class ProductRepositoryMock : ProductRepository {

    private val products = ConcurrentHashMap<String, Product>()
    private val variants = ConcurrentHashMap<String, ProductVariant>()

    init {
        initializeMockData()
    }

    private fun initializeMockData() {
        // 상품 1: LEVI'S 청바지
        val product1 = Product(
            id = "prod_001",
            name = "슬림핏 청바지",
            brand = "LEVI'S",
            category = "pants",
            description = "클래식한 핏의 청바지입니다.",
            basePrice = 89000,
            salePrice = 79000,
            discountRate = 11,
            images = listOf("https://cdn.fashionstore.com/prod_001_1.jpg"),
            tags = listOf("베스트셀러", "신상품"),
            rating = 4.5,
            reviewCount = 128,
            createdAt = "2025-10-31T00:00:00Z"
        )
        products["prod_001"] = product1

        // 상품 1 변량
        val variant1 = ProductVariant(
            id = "var_001",
            productId = "prod_001",
            sku = "LEVI-501-BLK-32-REG",
            color = "black",
            colorHex = "#000000",
            size = "32",
            length = "regular",
            price = 79000,
            originalPrice = 89000,
            stock = 15,
            stockStatus = StockStatus.IN_STOCK,
            images = null
        )
        variants["var_001"] = variant1

        val variant2 = ProductVariant(
            id = "var_002",
            productId = "prod_001",
            sku = "LEVI-501-BLK-34-REG",
            color = "black",
            colorHex = "#000000",
            size = "34",
            length = "regular",
            price = 79000,
            originalPrice = 89000,
            stock = 3,
            stockStatus = StockStatus.LOW_STOCK,
            images = null
        )
        variants["var_002"] = variant2

        // 상품 2: Nike 운동화
        val product2 = Product(
            id = "prod_002",
            name = "에어 맥스 270",
            brand = "NIKE",
            category = "shoes",
            description = "경량 쿠션감이 뛰어난 운동화입니다.",
            basePrice = 189000,
            salePrice = 149000,
            discountRate = 21,
            images = listOf("https://cdn.fashionstore.com/prod_002_1.jpg"),
            tags = listOf("인기상품"),
            rating = 4.8,
            reviewCount = 256,
            createdAt = "2025-10-31T00:00:00Z"
        )
        products["prod_002"] = product2

        val variant3 = ProductVariant(
            id = "var_003",
            productId = "prod_002",
            sku = "NIKE-270-WHT-270-REG",
            color = "white",
            colorHex = "#FFFFFF",
            size = "270",
            length = null,
            price = 149000,
            originalPrice = 189000,
            stock = 8,
            stockStatus = StockStatus.IN_STOCK,
            images = null
        )
        variants["var_003"] = variant3
    }

    override fun findAll(offset: Int, limit: Int): Pair<List<Product>, Int> {
        val allProducts = products.values.toList()
        val totalCount = allProducts.size
        val start = offset
        val end = minOf(start + limit, allProducts.size)

        return if (start < allProducts.size) {
            Pair(allProducts.subList(start, end), totalCount)
        } else {
            Pair(emptyList(), totalCount)
        }
    }

    override fun findById(id: String): Product? {
        return products[id]
    }

    override fun findVariantsByProductId(productId: String): List<ProductVariant> {
        return variants.values.filter { it.productId == productId }
    }

    override fun findVariantById(variantId: String): ProductVariant? {
        return variants[variantId]
    }

    override fun save(product: Product) {
        products[product.id] = product
    }

    override fun saveVariant(variant: ProductVariant) {
        variants[variant.id] = variant
    }
}
