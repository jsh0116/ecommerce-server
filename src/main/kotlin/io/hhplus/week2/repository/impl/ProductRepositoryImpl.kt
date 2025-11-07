package io.hhplus.week2.repository.impl

import io.hhplus.week2.domain.Product
import io.hhplus.week2.repository.ProductRepository
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

/**
 * ProductRepository의 실제 구현체 (DB 연동 필요)
 */
@Repository
@Primary
class ProductRepositoryImpl : ProductRepository {

    private val products = ConcurrentHashMap<String, Product>()

    override fun findById(id: String): Product? {
        return products[id]
    }

    override fun findAll(category: String?, sort: String): List<Product> {
        return products.values.toList()
    }

    override fun findTopSelling(startTimestamp: Long, limit: Int): List<Product> {
        return products.values.take(limit)
    }

    override fun save(product: Product) {
        products[product.id] = product
    }
}
