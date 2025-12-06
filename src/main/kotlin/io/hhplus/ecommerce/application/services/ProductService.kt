package io.hhplus.ecommerce.application.services

import io.hhplus.ecommerce.application.usecases.OrderUseCase
import io.hhplus.ecommerce.domain.OrderItem
import io.hhplus.ecommerce.domain.Product
import io.hhplus.ecommerce.exception.ProductException
import io.hhplus.ecommerce.infrastructure.repositories.ProductRepository
import org.springframework.stereotype.Service

@Service
class ProductService(
    private val productRepository: ProductRepository
) {
    fun getById(productId: Long): Product {
        return productRepository.findById(productId)
            ?: throw ProductException.ProductNotFound(productId.toString())
    }

    fun findAllById(productIds: List<Long>): List<Product> {
        return productRepository.findAllById(productIds)
    }

    fun validateAndCreateOrderItems(
        items: List<OrderUseCase.OrderItemRequest>
    ): List<OrderItem> {
        return items.map { request ->
            val product = getById(request.productId)
            OrderItem.create(product, request.quantity)
        }
    }

    fun findAll(category: String?, sort: String): List<Product> {
        return productRepository.findAll(category, sort)
    }

    fun findTopSelling(startTimestamp: Long, limit: Int): List<Product> {
        return productRepository.findTopSelling(startTimestamp, limit)
    }

    fun save(product: Product) {
        productRepository.save(product)
    }

    fun getByIdOrNull(productId: Long): Product? {
        return productRepository.findById(productId)
    }
}
