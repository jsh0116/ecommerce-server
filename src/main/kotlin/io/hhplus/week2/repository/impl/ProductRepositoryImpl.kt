package io.hhplus.week2.repository.impl

import io.hhplus.week2.domain.Product
import io.hhplus.week2.domain.ProductVariant
import io.hhplus.week2.domain.StockStatus
import io.hhplus.week2.repository.ProductRepository
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

/**
 * ProductRepository의 실제 구현체
 */
@Repository
@Primary
class ProductRepositoryImpl : ProductRepository {

    override fun findAll(offset: Int, limit: Int): Pair<List<Product>, Int> {
        TODO("Not yet implemented")
    }

    override fun findById(id: String): Product? {
        TODO("Not yet implemented")
    }

    override fun findVariantsByProductId(productId: String): List<ProductVariant> {
        TODO("Not yet implemented")
    }

    override fun findVariantById(variantId: String): ProductVariant? {
        TODO("Not yet implemented")
    }

    override fun save(product: Product) {
        TODO("Not yet implemented")
    }

    override fun saveVariant(variant: ProductVariant) {
        TODO("Not yet implemented")
    }
}
