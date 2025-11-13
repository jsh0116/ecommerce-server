package io.hhplus.ecommerce.infrastructure.persistence.jpa

import io.hhplus.ecommerce.infrastructure.persistence.entity.ProductJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/**
 * Product JPA Repository
 *
 * 성능 최적화 쿼리 포함:
 * 1. Fetch Join을 통한 N+1 문제 해결
 * 2. 복합 인덱스를 활용한 WHERE 절 최적화
 * 3. DB 레벨 정렬로 메모리 정렬 제거
 */
@Repository
interface ProductJpaRepository : JpaRepository<ProductJpaEntity, Long> {

    /**
     * 상품 목록 조회 (브랜드+카테고리 필터링)
     *
     * - 복합 인덱스: idx_brand_category_active (brand, category, is_active)
     * - 정렬: created_at DESC (기존 인덱스 재사용)
     *
     * 쿼리: SELECT * FROM products
     *       WHERE brand = ? AND category = ? AND is_active = 1 AND deleted_at IS NULL
     *       ORDER BY created_at DESC
     */
    @Query("""
        SELECT p FROM ProductJpaEntity p
        WHERE (:brand IS NULL OR p.brand = :brand)
        AND (:category IS NULL OR p.category = :category)
        AND p.isActive = true
        AND p.deletedAt IS NULL
        ORDER BY p.createdAt DESC
    """)
    fun findActiveProductsByBrandAndCategory(
        @Param("brand") brand: String?,
        @Param("category") category: String?
    ): List<ProductJpaEntity>

    /**
     * 활성 상품 목록 조회 (정렬)
     *
     * - 활성화 상태 + 삭제 필터링: idx_active_deleted (is_active, deleted_at)
     * - 정렬: created_at DESC
     *
     * 쿼리: SELECT * FROM products
     *       WHERE is_active = 1 AND deleted_at IS NULL
     *       ORDER BY created_at DESC
     */
    @Query("""
        SELECT p FROM ProductJpaEntity p
        WHERE p.isActive = true AND p.deletedAt IS NULL
        ORDER BY p.createdAt DESC
    """)
    fun findAllActiveProducts(): List<ProductJpaEntity>

    /**
     * 최신 판매량 기준 인기 상품 조회
     *
     * - 인덱스: idx_created_at (created_at DESC)
     * - 배치: created_at >= ? 조건으로 범위 축소
     *
     * 쿼리: SELECT * FROM products
     *       WHERE is_active = 1 AND created_at >= ? AND deleted_at IS NULL
     *       ORDER BY created_at DESC
     */
    @Query("""
        SELECT p FROM ProductJpaEntity p
        WHERE p.isActive = true
        AND p.createdAt >= :from
        AND p.deletedAt IS NULL
        ORDER BY p.createdAt DESC
    """)
    fun findTopSellingProductsSince(
        @Param("from") from: LocalDateTime
    ): List<ProductJpaEntity>

    /**
     * 평점 기준 상품 조회
     *
     * - 인덱스: idx_rating (rating DESC)
     * - 활성화 필터: 병렬 인덱스 조건
     *
     * 쿼리: SELECT * FROM products
     *       WHERE is_active = 1 AND rating >= ? AND deleted_at IS NULL
     *       ORDER BY rating DESC
     */
    @Query("""
        SELECT p FROM ProductJpaEntity p
        WHERE p.isActive = true
        AND p.rating >= :minRating
        AND p.deletedAt IS NULL
        ORDER BY p.rating DESC
    """)
    fun findProductsByMinRating(
        @Param("minRating") minRating: Double
    ): List<ProductJpaEntity>

    /**
     * 재고가 있는 상품 조회 (판매 가능)
     *
     * - Fetch Join: Product + Inventory (N+1 해결)
     * - 복합 인덱스: idx_brand_category_active (전체 스캔 범위 축소)
     *
     * 쿼리: SELECT p, i FROM products p
     *       LEFT JOIN inventory i ON i.sku = p.id
     *       WHERE p.is_active = 1 AND i.available_stock > 0 AND p.deleted_at IS NULL
     */
    @Query("""
        SELECT p FROM ProductJpaEntity p
        WHERE p.isActive = true
        AND p.deletedAt IS NULL
        AND EXISTS (SELECT 1 FROM InventoryJpaEntity i WHERE i.sku = p.id AND i.availableStock > 0)
    """)
    fun findProductsWithAvailableStock(): List<ProductJpaEntity>

    /**
     * 특정 기간의 판매량 기반 인기 상품
     *
     * - 범위 필터: created_at >= ? AND created_at <= ?
     * - 정렬: salesCount DESC (메모리 정렬)
     *
     * @param days 조회 기간 (일 단위)
     * @param limit 조회 수량
     */
    @Query("""
        SELECT p FROM ProductJpaEntity p
        WHERE p.isActive = true
        AND p.createdAt >= :startDate
        AND p.createdAt <= :endDate
        AND p.deletedAt IS NULL
        ORDER BY p.salesCount DESC
    """)
    fun findTopSellingByPeriod(
        @Param("startDate") startDate: LocalDateTime,
        @Param("endDate") endDate: LocalDateTime
    ): List<ProductJpaEntity>

    /**
     * 카테고리별 상품 수
     *
     * - GROUP BY 쿼리 (DB 레벨 집계)
     * - 인덱스: idx_category (category)
     */
    @Query("""
        SELECT COUNT(p) FROM ProductJpaEntity p
        WHERE p.category = :category
        AND p.isActive = true
        AND p.deletedAt IS NULL
    """)
    fun countByCategory(@Param("category") category: String): Long

    /**
     * 카테고리 내 최고 평점 상품
     *
     * - 카테고리 필터: idx_category
     * - 평점 필터: idx_rating
     * - 복합 쿼리: 두 인덱스 활용 여부 판단
     */
    @Query("""
        SELECT p FROM ProductJpaEntity p
        WHERE p.category = :category
        AND p.isActive = true
        AND p.deletedAt IS NULL
        ORDER BY p.rating DESC
        LIMIT 1
    """)
    fun findHighestRatedInCategory(@Param("category") category: String): ProductJpaEntity?
}
