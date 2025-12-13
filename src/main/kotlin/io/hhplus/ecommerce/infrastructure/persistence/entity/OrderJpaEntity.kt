package io.hhplus.ecommerce.infrastructure.persistence.entity

import io.hhplus.ecommerce.infrastructure.util.toUuid
import io.hhplus.ecommerce.infrastructure.util.uuidToLong
import jakarta.persistence.*
import java.time.LocalDateTime

enum class OrderJpaStatus {
    PENDING_PAYMENT,
    PAID,
    PREPARING,
    SHIPPED,
    DELIVERED,
    CANCELLED
}

/**
 * 주문 JPA Entity
 */
@Entity
@Table(
    name = "orders",
    indexes = [
        Index(name = "idx_orders_user_id", columnList = "user_id"),
        Index(name = "idx_orders_status", columnList = "status"),
        Index(name = "idx_orders_created_at", columnList = "created_at"),
        Index(name = "idx_orders_user_status_date", columnList = "user_id,status,created_at")
    ]
)
class OrderJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Column(nullable = false, length = 50)
    var orderNumber: String = "",

    @Column(nullable = false)
    var userId: Long = 0L,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: OrderJpaStatus = OrderJpaStatus.PENDING_PAYMENT,

    @Column(nullable = false)
    var totalAmount: Long = 0,

    @Column(nullable = false)
    var discountAmount: Long = 0,

    @Column(nullable = false)
    var finalAmount: Long = 0,

    @Column(length = 50)
    var couponCode: String? = null,

    @Column
    var couponId: Long? = null,

    @Column(nullable = false)
    var pointsUsed: Long = 0,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @Column
    var paidAt: LocalDateTime? = null,

    @Column
    var reservationExpiresAt: LocalDateTime? = null
) {
    /**
     * 결제 완료 처리
     */
    fun markAsPaid() {
        require(status == OrderJpaStatus.PENDING_PAYMENT) { "결제 대기 상태 주문만 가능합니다" }
        status = OrderJpaStatus.PAID
        paidAt = LocalDateTime.now()
        updatedAt = LocalDateTime.now()
    }

    /**
     * 주문 취소
     */
    fun cancel() {
        require(status in listOf(OrderJpaStatus.PENDING_PAYMENT, OrderJpaStatus.PAID, OrderJpaStatus.PREPARING)) {
            "배송 중이거나 완료된 주문은 취소할 수 없습니다"
        }
        status = OrderJpaStatus.CANCELLED
        updatedAt = LocalDateTime.now()
    }

    /**
     * 주문 ID를 UUID 문자열로 변환
     */
    fun getUuid(): String = id.toUuid()

    /**
     * 사용자 ID를 UUID 문자열로 변환
     */
    fun getUserUuid(): String = userId.toUuid()
}
