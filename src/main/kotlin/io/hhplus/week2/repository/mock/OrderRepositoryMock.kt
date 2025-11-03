package io.hhplus.week2.repository.mock

import io.hhplus.week2.domain.Order
import io.hhplus.week2.repository.OrderRepository
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

/**
 * OrderRepository의 메모리 기반 구현체
 * MockData로 동작합니다.
 */
@Repository
class OrderRepositoryMock : OrderRepository {

    private val orders = ConcurrentHashMap<String, Order>()

    override fun save(order: Order): Order {
        orders[order.id] = order
        return order
    }

    override fun findById(id: String): Order? {
        return orders[id]
    }

    override fun findByUserId(userId: String): List<Order> {
        return orders.values.filter { it.userId == userId }
    }

    override fun update(order: Order): Order {
        orders[order.id] = order
        return order
    }
}
