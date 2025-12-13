package io.hhplus.ecommerce.infrastructure.persistence.adapter

import io.hhplus.ecommerce.domain.Order
import io.hhplus.ecommerce.infrastructure.repositories.OrderRepository
import io.hhplus.ecommerce.infrastructure.persistence.repository.OrderJpaRepository
import io.hhplus.ecommerce.infrastructure.persistence.repository.OrderItemJpaRepository
import io.hhplus.ecommerce.infrastructure.persistence.entity.OrderJpaEntity
import io.hhplus.ecommerce.infrastructure.persistence.entity.OrderItemJpaEntity
import io.hhplus.ecommerce.infrastructure.persistence.entity.OrderJpaStatus
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

/**
 * OrderRepository JPA 어댑터
 *
 * Domain Order와 JPA Entity OrderJpaEntity 간의 변환을 담당합니다.
 */
@Repository
class OrderRepositoryAdapter(
    private val jpaRepository: OrderJpaRepository,
    private val orderItemJpaRepository: OrderItemJpaRepository
) : OrderRepository {

    @Transactional
    override fun save(order: Order): Order {
        val entity = order.toEntity()
        val savedEntity = jpaRepository.save(entity)

        // OrderItem 저장 (기존 아이템 삭제 후 새로 저장)
        orderItemJpaRepository.deleteByOrderId(savedEntity.id)
        val orderItems = order.items.map { OrderItemJpaEntity.fromDomain(savedEntity.id, it) }
        orderItemJpaRepository.saveAll(orderItems)

        return savedEntity.toDomain(orderItems.map { it.toDomain() })
    }

    override fun findById(id: Long): Order? {
        val entity = jpaRepository.findById(id).orElse(null) ?: return null
        val items = orderItemJpaRepository.findByOrderId(id).map { it.toDomain() }
        return entity.toDomain(items)
    }

    override fun findByUserId(userId: Long): List<Order> {
        return jpaRepository.findByUserId(userId).map { entity ->
            val items = orderItemJpaRepository.findByOrderId(entity.id).map { it.toDomain() }
            entity.toDomain(items)
        }
    }

    @Transactional
    override fun update(order: Order): Order {
        val entity = order.toEntity()
        val updatedEntity = jpaRepository.save(entity)

        // OrderItem 업데이트
        orderItemJpaRepository.deleteByOrderId(updatedEntity.id)
        val orderItems = order.items.map { OrderItemJpaEntity.fromDomain(updatedEntity.id, it) }
        orderItemJpaRepository.saveAll(orderItems)

        return updatedEntity.toDomain(orderItems.map { it.toDomain() })
    }

    /**
     * Domain Order를 JPA Entity로 변환
     */
    private fun Order.toEntity(): OrderJpaEntity {
        return OrderJpaEntity(
            id = this.id,
            orderNumber = "", // 주문 번호는 별도의 생성 로직에서 처리
            userId = this.userId,
            status = when (this.status) {
                "PENDING" -> OrderJpaStatus.PENDING_PAYMENT
                "PAID" -> OrderJpaStatus.PAID
                "PREPARING" -> OrderJpaStatus.PREPARING
                "SHIPPED" -> OrderJpaStatus.SHIPPED
                "DELIVERED" -> OrderJpaStatus.DELIVERED
                "CANCELLED" -> OrderJpaStatus.CANCELLED
                else -> OrderJpaStatus.PENDING_PAYMENT
            },
            totalAmount = this.totalAmount,
            discountAmount = this.discountAmount,
            finalAmount = this.finalAmount,
            couponCode = null,
            couponId = this.couponId,
            pointsUsed = 0L,
            createdAt = this.createdAt,
            updatedAt = this.createdAt,
            paidAt = this.paidAt
        )
    }

    /**
     * JPA Entity를 Domain Order로 변환
     */
    private fun OrderJpaEntity.toDomain(items: List<io.hhplus.ecommerce.domain.OrderItem>): Order {
        return Order(
            id = this.id,
            userId = this.userId,
            items = items,
            totalAmount = this.totalAmount,
            discountAmount = this.discountAmount,
            finalAmount = this.finalAmount,
            status = when (this.status) {
                OrderJpaStatus.PENDING_PAYMENT -> "PENDING"
                OrderJpaStatus.PAID -> "PAID"
                OrderJpaStatus.PREPARING -> "PREPARING"
                OrderJpaStatus.SHIPPED -> "SHIPPED"
                OrderJpaStatus.DELIVERED -> "DELIVERED"
                OrderJpaStatus.CANCELLED -> "CANCELLED"
            },
            couponId = this.couponId,
            createdAt = this.createdAt,
            paidAt = this.paidAt
        )
    }
}
