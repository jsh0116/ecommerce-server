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
    val id: Long = 0L,

    @Column(nullable = false, unique = true, length = 100)
    var sku: String = "",

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
     *
     * 주문 생성 시 즉시 physicalStock을 감소시킴
     * TTL 만료 시 복구됨
     */
    fun reserve(quantity: Int) {
        require(physicalStock >= quantity) {
            "재고 부족: 요청 ${quantity}개, 실제 재고 ${physicalStock}개 (SKU: $sku)"
        }
        physicalStock -= quantity
        updateStatus()
    }

    /**
     * 예약 확정 (이미 reserve()에서 physicalStock이 감소했으므로 상태만 업데이트)
     */
    fun confirmReservation(@Suppress("UNUSED_PARAMETER") quantity: Int) {
        // reserve()에서 이미 physicalStock이 감소했으므로 추가 감소 불필요
        // 확정 시 reservedStock도 자동으로 0이 됨 (이미 reserve에서 처리됨)
        updateStatus()
    }

    /**
     * 예약 취소 (reserve()에서 감소한 physicalStock 복구)
     *
     * 결제 실패 또는 TTL 만료 시 호출
     */
    fun cancelReservation(quantity: Int) {
        // reserve()에서 감소했던 physicalStock을 복구
        physicalStock += quantity
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
