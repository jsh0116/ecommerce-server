package io.hhplus.week2.domain

import java.time.LocalDateTime

/**
 * 재고 도메인 모델
 *
 * 재고 계산 공식: availableStock = physicalStock - reservedStock - safetyStock
 */
data class Inventory(
    val sku: String,
    var physicalStock: Int,
    var reservedStock: Int = 0,
    val safetyStock: Int = 0,
    val lastUpdated: LocalDateTime = LocalDateTime.now()
) {
    /**
     * 가용 재고 계산
     */
    fun getAvailableStock(): Int {
        return (physicalStock - reservedStock - safetyStock).coerceAtLeast(0)
    }

    /**
     * 재고 상태 반환
     */
    fun getStatus(): StockStatus {
        val available = getAvailableStock()
        return when {
            available <= 0 -> StockStatus.OUT_OF_STOCK
            available <= 5 -> StockStatus.LOW_STOCK
            else -> StockStatus.IN_STOCK
        }
    }

    /**
     * 재고 예약 가능 여부 확인
     */
    fun canReserve(quantity: Int): Boolean {
        return getAvailableStock() >= quantity
    }

    /**
     * 재고 예약
     */
    fun reserve(quantity: Int) {
        if (!canReserve(quantity)) {
            throw IllegalStateException(
                "재고 부족: 요청 ${quantity}개, 가용 재고 ${getAvailableStock()}개 (SKU: $sku)"
            )
        }
        reservedStock += quantity
    }

    /**
     * 예약 확정 (실제 재고 차감)
     */
    fun confirmReservation(quantity: Int) {
        if (reservedStock < quantity) {
            throw IllegalStateException(
                "예약 수량 부족: 요청 ${quantity}개, 예약 재고 ${reservedStock}개 (SKU: $sku)"
            )
        }
        physicalStock -= quantity
        reservedStock -= quantity
    }

    /**
     * 예약 취소 (예약 재고 복원)
     */
    fun cancelReservation(quantity: Int) {
        if (reservedStock < quantity) {
            throw IllegalStateException(
                "예약 취소 불가: 요청 ${quantity}개, 예약 재고 ${reservedStock}개 (SKU: $sku)"
            )
        }
        reservedStock -= quantity
    }

    /**
     * 재고 복구 (실제 재고 증가)
     */
    fun restoreStock(quantity: Int) {
        physicalStock += quantity
    }

    /**
     * 재고 조정 (직접 재고 설정)
     */
    fun adjustStock(newPhysicalStock: Int) {
        if (newPhysicalStock < 0) {
            throw IllegalArgumentException("재고는 0 이상이어야 합니다")
        }
        physicalStock = newPhysicalStock
    }
}

/**
 * 재고 상태 열거형
 */
enum class StockStatus {
    IN_STOCK,      // 재고 있음 (가용 재고 > 5)
    LOW_STOCK,     // 재고 부족 임박 (1 <= 가용 재고 <= 5)
    OUT_OF_STOCK   // 품절 (가용 재고 <= 0)
}