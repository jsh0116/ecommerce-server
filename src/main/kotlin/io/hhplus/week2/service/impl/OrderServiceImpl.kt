package io.hhplus.week2.service.impl

import io.hhplus.week2.domain.Order
import io.hhplus.week2.domain.OrderStatus
import io.hhplus.week2.repository.OrderRepository
import io.hhplus.week2.service.OrderService
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicInteger

/**
 * 주문 서비스 구현체
 */
@Service
class OrderServiceImpl(
    private val orderRepository: OrderRepository
) : OrderService {

    companion object {
        private val orderSequence = AtomicInteger(0)
    }

    override fun createOrder(order: Order): Order {
        return orderRepository.save(order)
    }

    override fun getOrderById(orderId: String): Order? {
        return orderRepository.findById(orderId)
    }

    override fun getOrdersByUserId(userId: String): List<Order> {
        return orderRepository.findByUserId(userId)
    }

    override fun updateOrderStatus(orderId: String, newStatus: OrderStatus): Order? {
        val order = orderRepository.findById(orderId) ?: return null
        val updatedOrder = order.copy(status = newStatus)
        return orderRepository.update(updatedOrder)
    }

    override fun generateOrderNumber(): String {
        val today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        val sequence = orderSequence.incrementAndGet()
        return "$today${String.format("%05d", sequence)}"
    }
}