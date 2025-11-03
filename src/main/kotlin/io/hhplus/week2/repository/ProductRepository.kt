package io.hhplus.week2.repository

import io.hhplus.week2.domain.Product
import io.hhplus.week2.domain.ProductVariant

/**
 * 상품 관련 저장소 인터페이스
 */
interface ProductRepository {

    /**
     * 모든 상품을 조회합니다.
     *
     * @param offset 오프셋
     * @param limit 개수 제한
     * @return Pair<상품 목록, 전체 상품 수>
     */
    fun findAll(offset: Int = 0, limit: Int = 20): Pair<List<Product>, Int>

    /**
     * 상품 ID로 상품을 조회합니다.
     *
     * @param id 상품 ID
     * @return 상품 또는 null
     */
    fun findById(id: String): Product?

    /**
     * 상품 ID로 변량 목록을 조회합니다.
     *
     * @param productId 상품 ID
     * @return 변량 목록
     */
    fun findVariantsByProductId(productId: String): List<ProductVariant>

    /**
     * 변량 ID로 변량을 조회합니다.
     *
     * @param variantId 변량 ID
     * @return 변량 또는 null
     */
    fun findVariantById(variantId: String): ProductVariant?

    /**
     * 상품을 저장합니다.
     *
     * @param product 상품
     */
    fun save(product: Product)

    /**
     * 변량을 저장합니다.
     *
     * @param variant 변량
     */
    fun saveVariant(variant: ProductVariant)
}