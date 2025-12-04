package io.hhplus.ecommerce.infrastructure.persistence.adapter

import io.hhplus.ecommerce.domain.Product
import io.hhplus.ecommerce.infrastructure.repositories.ProductRepository
import io.hhplus.ecommerce.infrastructure.persistence.repository.ProductJpaRepository
import io.hhplus.ecommerce.infrastructure.persistence.entity.ProductJpaEntity
import org.springframework.stereotype.Repository

/**
 * ProductRepository JPA 어댑터
 *
 * Domain Product와 JPA Entity ProductJpaEntity 간의 변환을 담당합니다.
 */
@Repository
class ProductRepositoryAdapter(
    private val jpaRepository: ProductJpaRepository
) : ProductRepository {

    override fun findById(id: Long): Product? {
        return jpaRepository.findById(id).map { it.toDomain() }.orElse(null)
    }

    override fun findAll(category: String?, sort: String): List<Product> {
        val entities = if (category != null) {
            jpaRepository.findByCategory(category)
        } else {
            jpaRepository.findAllActive()
        }
        return entities.map { it.toDomain() }
    }

    override fun findAllById(ids: List<Long>): List<Product> {
        val entities = jpaRepository.findAllById(ids)
        return entities.map { it.toDomain() }
    }

    override fun findTopSelling(startTimestamp: Long, limit: Int): List<Product> {
        val entities = jpaRepository.findTopSellingProducts(limit)
        return entities.map { it.toDomain() }
    }

    override fun save(product: Product) {
        val entity = product.toEntity()
        jpaRepository.save(entity)
    }

    /**
     * Domain Product를 JPA Entity로 변환
     */
    private fun Product.toEntity(): ProductJpaEntity {
        return ProductJpaEntity(
            id = this.id,
            name = this.name,
            description = this.description,
            price = this.price,
            category = this.category,
            viewCount = this.viewCount,
            salesCount = this.salesCount,
            createdAt = this.createdAt,
            updatedAt = this.createdAt
        )
    }

    /**
     * JPA Entity를 Domain Product로 변환
     */
    private fun ProductJpaEntity.toDomain(): Product {
        return Product(
            id = this.id,
            name = this.name,
            description = this.description,
            price = this.price,
            category = this.category,
            viewCount = this.viewCount,
            salesCount = this.salesCount,
            createdAt = this.createdAt
        )
    }
}
