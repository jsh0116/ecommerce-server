package io.hhplus.week2.service

import io.hhplus.week2.domain.Product
import io.hhplus.week2.domain.ProductVariant

/**
 * 상품 관련 도메인 서비스
 */
interface ProductService {

    /**
     * 페이지네이션과 함께 모든 상품을 조회합니다.
     *
     * @param page 페이지 번호 (1부터 시작)
     * @param limit 페이지당 항목 수
     * @return Pair<상품 목록, 전체 상품 수>
     */
    fun getAllProducts(page: Int, limit: Int): Pair<List<Product>, Int>

    /**
     * 상품 ID로 상품을 조회합니다.
     *
     * @param productId 상품 ID
     * @return 상품 정보 또는 null
     */
    fun getProductById(productId: String): Product?

    /**
     * 상품의 모든 변량(SKU)을 조회합니다.
     *
     * @param productId 상품 ID
     * @return 변량 목록
     */
    fun getProductVariants(productId: String): List<ProductVariant>

    /**
     * 변량 ID로 변량 정보를 조회합니다.
     *
     * @param variantId 변량 ID
     * @return 변량 정보 또는 null
     */
    fun getVariantById(variantId: String): ProductVariant?
}
