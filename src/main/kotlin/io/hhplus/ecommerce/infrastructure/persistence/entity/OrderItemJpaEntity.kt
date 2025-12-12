package io.hhplus.ecommerce.infrastructure.persistence.entity

import io.hhplus.ecommerce.domain.OrderItem
import jakarta.persistence.*

/**
 * 주문 아이템 JPA Entity
 */
@Entity
@Table(name = "order_items")
class OrderItemJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Column(nullable = false)
    val orderId: Long = 0L,

    @Column(nullable = false)
    val productId: Long = 0L,

    @Column(nullable = false, length = 255)
    val productName: String = "",

    @Column(nullable = false)
    val quantity: Int = 0,

    @Column(nullable = false)
    val unitPrice: Long = 0L,

    @Column(nullable = false)
    val subtotal: Long = 0L
) {
    /**
     * Domain OrderItem으로 변환
     */
    fun toDomain(): OrderItem {
        return OrderItem(
            productId = this.productId,
            productName = this.productName,
            quantity = this.quantity,
            unitPrice = this.unitPrice,
            subtotal = this.subtotal
        )
    }

    companion object {
        /**
         * Domain OrderItem에서 생성
         */
        fun fromDomain(orderId: Long, orderItem: OrderItem): OrderItemJpaEntity {
            return OrderItemJpaEntity(
                orderId = orderId,
                productId = orderItem.productId,
                productName = orderItem.productName,
                quantity = orderItem.quantity,
                unitPrice = orderItem.unitPrice,
                subtotal = orderItem.subtotal
            )
        }
    }
}
