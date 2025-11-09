package io.hhplus.week2.repository.mock

import io.hhplus.week2.domain.Product
import io.hhplus.week2.repository.ProductRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * ProductRepository의 메모리 기반 구현체
 * 테스트용 Mock 데이터로 동작합니다.
 */
@Repository
class ProductRepositoryMock : ProductRepository {

    private val products = ConcurrentHashMap<String, Product>()

    init {
        initializeMockData()
    }

    private fun initializeMockData() {
        val laptop = Product(
            id = "P001",
            name = "노트북",
            description = "고성능 노트북",
            price = 890000,
            category = "electronics",
            createdAt = LocalDateTime.now()
        )
        products["P001"] = laptop

        val monitor = Product(
            id = "P002",
            name = "모니터",
            description = "27인치 모니터",
            price = 350000,
            category = "electronics",
            createdAt = LocalDateTime.now()
        )
        products["P002"] = monitor

        val keyboard = Product(
            id = "P003",
            name = "키보드",
            description = "기계식 키보드",
            price = 150000,
            category = "electronics",
            createdAt = LocalDateTime.now()
        )
        products["P003"] = keyboard
    }

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
