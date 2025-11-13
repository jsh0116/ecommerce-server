package io.hhplus.ecommerce.infrastructure.persistence.adapter

import io.hhplus.ecommerce.domain.Product
import io.hhplus.ecommerce.infrastructure.repositories.ProductRepository
import org.springframework.stereotype.Repository

/**
 * ProductRepository JPA 어댑터
 */
@Repository
class ProductRepositoryAdapter : ProductRepository {

    override fun findById(id: Long): Product? {
        // TODO: 구현 필요
        return null
    }

    override fun findAll(category: String?, sort: String): List<Product> {
        // TODO: 구현 필요
        return emptyList()
    }

    override fun findTopSelling(startTimestamp: Long, limit: Int): List<Product> {
        // TODO: 구현 필요
        return emptyList()
    }

    override fun save(product: Product) {
        // TODO: 구현 필요
    }
}
