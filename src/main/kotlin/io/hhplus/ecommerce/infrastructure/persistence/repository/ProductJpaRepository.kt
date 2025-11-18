package io.hhplus.ecommerce.infrastructure.persistence.repository

import io.hhplus.ecommerce.infrastructure.persistence.entity.ProductJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional

/**
 * 상품 JPA Repository
 */
@Repository
interface ProductJpaRepository : JpaRepository<ProductJpaEntity, Long> {

    /**
     * 상품 ID로 조회
     */
    override fun findById(id: Long): Optional<ProductJpaEntity>

    /**
     * 카테고리별 상품 조회
     */
    fun findByCategory(category: String): List<ProductJpaEntity>

    /**
     * 판매량 기준 상위 상품 조회
     */
    @Query("""
        SELECT p FROM ProductJpaEntity p
        ORDER BY p.salesCount DESC
        LIMIT :limit
    """)
    fun findTopSellingProducts(@Param("limit") limit: Int): List<ProductJpaEntity>

    /**
     * 전체 상품 조회 (활성화된 것만)
     */
    @Query("""
        SELECT p FROM ProductJpaEntity p
        ORDER BY p.createdAt DESC
    """)
    fun findAllActive(): List<ProductJpaEntity>
}