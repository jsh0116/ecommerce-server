package io.hhplus.ecommerce.application.services

import io.hhplus.ecommerce.domain.Order
import io.hhplus.ecommerce.domain.OrderItem
import io.hhplus.ecommerce.domain.User
import io.hhplus.ecommerce.domain.UserCoupon
import io.hhplus.ecommerce.exception.OrderException
import io.hhplus.ecommerce.infrastructure.repositories.OrderRepository
import org.springframework.stereotype.Service

@Service
class OrderService(
    private val orderRepository: OrderRepository
) {
    fun createOrder(
        user: User,
        orderItems: List<OrderItem>,
        coupon: UserCoupon?
    ): Order {
        val totalAmount = orderItems.sumOf { it.subtotal }
        val discountAmount = coupon?.let {
            (totalAmount * it.discountRate / 100.0).toLong()
        } ?: 0L
        val finalAmount = totalAmount - discountAmount

        val order = Order(
            id = System.currentTimeMillis(),
            userId = user.id,
            items = orderItems,
            totalAmount = totalAmount,
            discountAmount = discountAmount,
            finalAmount = finalAmount,
            couponId = coupon?.couponId
        )

        return orderRepository.save(order)
    }

    fun getById(orderId: Long): Order {
        return orderRepository.findById(orderId)
            ?: throw OrderException.OrderNotFound(orderId.toString())
    }

    fun getByUserId(userId: Long): List<Order> {
        return orderRepository.findByUserId(userId)
    }

    fun updateOrderStatus(orderId: Long, newStatus: String): Order {
        val order = getById(orderId)

        when (newStatus) {
            "CANCELLED" -> {
                if (!order.canCancel()) {
                    throw OrderException.CannotCancelOrder(order.status)
                }
                order.cancel()
            }
            else -> {
                order.status = newStatus
            }
        }

        return orderRepository.save(order)
    }

    fun completeOrder(orderId: Long): Order {
        val order = getById(orderId)
        order.complete()
        return orderRepository.save(order)
    }

    fun cancelOrder(orderId: Long, userId: Long): Order {
        val order = getById(orderId)

        if (order.userId != userId) {
            throw OrderException.UnauthorizedOrderAccess()
        }

        if (!order.canCancel()) {
            throw OrderException.CannotCancelOrder(order.status)
        }

        order.cancel()
        return orderRepository.save(order)
    }
}
