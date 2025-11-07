package io.hhplus.week2.repository

import io.hhplus.week2.domain.Product

/**
 * 상품 저장소 인터페이스
 */
interface ProductRepository {
    /**
     * 상품 단건 조회
     */
    fun findById(id: String): Product?

    /**
     * 상품 목록 조회
     */
    fun findAll(category: String?, sort: String): List<Product>

    /**
     * 인기 상품 조회
     */
    fun findTopSelling(startTimestamp: Long, limit: Int): List<Product>

    /**
     * 상품 저장
     */
    fun save(product: Product)
}