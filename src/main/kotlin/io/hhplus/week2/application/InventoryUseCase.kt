package io.hhplus.week2.application

import io.hhplus.week2.domain.Inventory
import io.hhplus.week2.repository.InventoryRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.UUID

/**
 * 재고 유스케이스
 */
@Service
class InventoryUseCase(
    private val inventoryRepository: InventoryRepository
) {
    /**
     * SKU 코드로 재고 정보를 조회합니다.
     */
    fun getInventoryBySku(sku: String): Inventory? {
        return inventoryRepository.findBySku(sku)
    }

    /**
     * 재고를 예약합니다.
     */
    fun reserveInventory(sku: String, quantity: Int, ttlMinutes: Int): ReservationInfo? {
        val inventory = inventoryRepository.findBySku(sku)
            ?: return null

        // 재고 예약 가능 여부 확인
        if (!inventory.canReserve(quantity)) {
            return null
        }

        // 재고 예약
        inventory.reserve(quantity)
        inventoryRepository.save(inventory)

        // 예약 정보 반환
        val now = LocalDateTime.now()
        return ReservationInfo(
            id = UUID.randomUUID().toString(),
            sku = sku,
            quantity = quantity,
            expiresAt = now.plusMinutes(ttlMinutes.toLong()).toString(),
            reservedAt = now.toString()
        )
    }

    /**
     * 실제 재고를 차감합니다. (결제 승인 후)
     */
    fun deductInventory(sku: String, quantity: Int): Boolean {
        val inventory = inventoryRepository.findBySku(sku)
            ?: return false

        return try {
            // 예약 확정 (실제 재고 차감)
            inventory.confirmReservation(quantity)
            inventoryRepository.save(inventory)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 예약된 재고를 취소하고 가용 재고를 복구합니다.
     */
    fun cancelReservation(sku: String, quantity: Int): Boolean {
        val inventory = inventoryRepository.findBySku(sku)
            ?: return false

        return try {
            inventory.cancelReservation(quantity)
            inventoryRepository.save(inventory)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 재고를 복구합니다. (주문 취소 시)
     */
    fun restoreInventory(sku: String, quantity: Int): Boolean {
        val inventory = inventoryRepository.findBySku(sku)
            ?: return false

        inventory.restoreStock(quantity)
        inventoryRepository.save(inventory)
        return true
    }
}

/**
 * 재고 예약 정보
 */
data class ReservationInfo(
    val id: String,
    val sku: String,
    val quantity: Int,
    val expiresAt: String,
    val reservedAt: String
)