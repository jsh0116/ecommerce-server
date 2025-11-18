package io.hhplus.ecommerce.infrastructure.persistence.adapter

import io.hhplus.ecommerce.domain.Order
import io.hhplus.ecommerce.infrastructure.repositories.OrderRepository
import io.hhplus.ecommerce.infrastructure.persistence.repository.OrderJpaRepository
import io.hhplus.ecommerce.infrastructure.persistence.entity.OrderJpaEntity
import io.hhplus.ecommerce.infrastructure.persistence.entity.OrderJpaStatus
import org.springframework.stereotype.Repository

/**
 * OrderRepository JPA 어댑터
 *
 * Domain Order와 JPA Entity OrderJpaEntity 간의 변환을 담당합니다.
 */
@Repository
class OrderRepositoryAdapter(
    private val jpaRepository: OrderJpaRepository
) : OrderRepository {

    override fun save(order: Order): Order {
        val entity = order.toEntity()
        val savedEntity = jpaRepository.save(entity)
        return savedEntity.toDomain()
    }

    override fun findById(id: Long): Order? {
        return jpaRepository.findById(id).orElse(null)?.toDomain()
    }

    override fun findByUserId(userId: Long): List<Order> {
        return jpaRepository.findByUserId(userId).map { it.toDomain() }
    }

    override fun update(order: Order): Order {
        val entity = order.toEntity()
        val updatedEntity = jpaRepository.save(entity)
        return updatedEntity.toDomain()
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
            pointsUsed = 0L,
            createdAt = this.createdAt,
            updatedAt = this.createdAt,
            paidAt = this.paidAt
        )
    }

    /**
     * JPA Entity를 Domain Order로 변환
     */
    private fun OrderJpaEntity.toDomain(): Order {
        return Order(
            id = this.id,
            userId = this.userId,
            items = emptyList(), // 주문 항목은 별도의 리포지토리에서 조회
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
            couponId = null,
            createdAt = this.createdAt,
            paidAt = this.paidAt
        )
    }
}
