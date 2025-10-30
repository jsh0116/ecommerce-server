package io.hhplus.week2.service.impl

import io.hhplus.week2.domain.Product
import io.hhplus.week2.domain.ProductVariant
import io.hhplus.week2.repository.ProductRepository
import io.hhplus.week2.service.ProductService
import org.springframework.stereotype.Service

/**
 * 상품 서비스 구현체
 */
@Service
class ProductServiceImpl(
    private val productRepository: ProductRepository
) : ProductService {

    override fun getAllProducts(page: Int, limit: Int): Pair<List<Product>, Int> {
        val offset = (page - 1) * limit
        return productRepository.findAll(offset, limit)
    }

    override fun getProductById(productId: String): Product? {
        return productRepository.findById(productId)
    }

    override fun getProductVariants(productId: String): List<ProductVariant> {
        return productRepository.findVariantsByProductId(productId)
    }

    override fun getVariantById(variantId: String): ProductVariant? {
        return productRepository.findVariantById(variantId)
    }
}