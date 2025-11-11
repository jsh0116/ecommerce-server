package io.hhplus.ecommerce.infrastructure.persistence.entity

import io.hhplus.ecommerce.domain.StockStatus
import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * 재고 JPA Entity
 *
 * 비관적 락(Pessimistic Lock)을 통해 동시성 제어
 */
@Entity
@Table(
    name = "inventory",
    indexes = [
        Index(name = "idx_inventory_sku", columnList = "sku", unique = true),
        Index(name = "idx_inventory_status", columnList = "status")
    ]
)
class InventoryJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, unique = true, length = 100)
    var sku: String,

    @Column(nullable = false)
    var physicalStock: Int = 0,

    @Column(nullable = false)
    var reservedStock: Int = 0,

    @Column(nullable = false)
    var safetyStock: Int = 0,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: StockStatus = StockStatus.OUT_OF_STOCK,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @Version
    val version: Long = 0
) {
    /**
     * 가용 재고 계산
     */
    fun getAvailableStock(): Int {
        return (physicalStock - reservedStock - safetyStock).coerceAtLeast(0)
    }

    /**
     * 재고 상태 업데이트
     */
    fun updateStatus() {
        val available = getAvailableStock()
        status = when {
            available <= 0 -> StockStatus.OUT_OF_STOCK
            available <= 5 -> StockStatus.LOW_STOCK
            else -> StockStatus.IN_STOCK
        }
        updatedAt = LocalDateTime.now()
    }

    /**
     * 재고 예약
     */
    fun reserve(quantity: Int) {
        require(getAvailableStock() >= quantity) {
            "재고 부족: 요청 ${quantity}개, 가용 재고 ${getAvailableStock()}개 (SKU: $sku)"
        }
        reservedStock += quantity
        updateStatus()
    }

    /**
     * 예약 확정 (실제 재고 차감)
     */
    fun confirmReservation(quantity: Int) {
        require(reservedStock >= quantity) {
            "예약 수량 부족: 요청 ${quantity}개, 예약 재고 ${reservedStock}개 (SKU: $sku)"
        }
        physicalStock -= quantity
        reservedStock -= quantity
        updateStatus()
    }

    /**
     * 예약 취소 (예약 재고 복원)
     */
    fun cancelReservation(quantity: Int) {
        require(reservedStock >= quantity) {
            "예약 취소 불가: 요청 ${quantity}개, 예약 재고 ${reservedStock}개 (SKU: $sku)"
        }
        reservedStock -= quantity
        updateStatus()
    }

    /**
     * 재고 복구
     */
    fun restoreStock(quantity: Int) {
        physicalStock += quantity
        updateStatus()
    }
}
