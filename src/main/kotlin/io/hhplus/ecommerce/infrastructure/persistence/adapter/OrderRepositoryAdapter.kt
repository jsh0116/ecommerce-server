package io.hhplus.ecommerce.infrastructure.persistence.adapter

import io.hhplus.ecommerce.domain.Order
import io.hhplus.ecommerce.infrastructure.repositories.OrderRepository
import io.hhplus.ecommerce.infrastructure.persistence.repository.OrderJpaRepository
import org.springframework.stereotype.Repository

/**
 * OrderRepository JPA 어댑터
 */
@Repository
class OrderRepositoryAdapter(
    private val jpaRepository: OrderJpaRepository
) : OrderRepository {

    override fun save(order: Order): Order {
        // TODO: Entity 변환 로직 필요
        return order
    }

    override fun findById(id: Long): Order? {
        // TODO: Entity 변환 로직 필요
        return null
    }

    override fun findByUserId(userId: Long): List<Order> {
        // TODO: Entity 변환 로직 필요
        return emptyList()
    }

    override fun update(order: Order): Order {
        // TODO: Entity 변환 로직 필요
        return order
    }
}
