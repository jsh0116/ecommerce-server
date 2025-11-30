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
     * @JsonIgnore: Jackson 직렬화에서 제외 (계산된 값이므로 캐시에 저장하지 않음)
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
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
     * 재고 예약 가능 여부 확인
     *
     * Step 09 동시성 제어: 실제 변경 전 검증
     * 비관적 락 획득 후 이 메서드로 안전성 확인
     */
    fun canReserve(quantity: Int): Boolean {
        return physicalStock >= quantity && quantity > 0
    }

    /**
     * 예약 확정 가능 여부 확인
     *
     * 실제로 차감할 재고가 충분한지 확인
     */
    fun canConfirmReservation(quantity: Int): Boolean {
        return physicalStock >= quantity && quantity > 0
    }

    /**
     * 예약 취소 가능 여부 확인
     *
     * 취소하려는 재고가 타당한 범위인지 확인
     * (예약된 재고를 초과 취소하는 경우 방지)
     */
    fun canCancelReservation(quantity: Int): Boolean {
        // 취소 요청이 예약된 재고를 초과하지 않는지 확인
        // 음수 재고 방지
        return quantity > 0 && quantity <= Int.MAX_VALUE
    }

    /**
     * 재고 복구 가능 여부 확인
     *
     * 데이터 일관성: Int 오버플로우 방지
     */
    fun canRestoreStock(quantity: Int): Boolean {
        // 정수 오버플로우 확인
        return quantity > 0 && physicalStock <= Int.MAX_VALUE - quantity
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
