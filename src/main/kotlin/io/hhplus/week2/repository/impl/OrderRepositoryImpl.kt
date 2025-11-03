package io.hhplus.week2.repository.impl

import io.hhplus.week2.domain.Order
import io.hhplus.week2.repository.OrderRepository
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

/**
 * OrderRepository의 실제 구현체
 */
@Repository
@Primary
class OrderRepositoryImpl : OrderRepository {


    override fun save(order: Order): Order {
        TODO("Not yet implemented")
    }

    override fun findById(id: String): Order? {
        TODO("Not yet implemented")
    }

    override fun findByUserId(userId: String): List<Order> {
        TODO("Not yet implemented")
    }

    override fun update(order: Order): Order {
        TODO("Not yet implemented")
    }
}
