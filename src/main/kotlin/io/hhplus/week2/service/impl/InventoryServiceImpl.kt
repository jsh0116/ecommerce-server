package io.hhplus.week2.service.impl

import io.hhplus.week2.domain.Inventory
import io.hhplus.week2.domain.StockStatus
import io.hhplus.week2.repository.InventoryRepository
import io.hhplus.week2.service.InventoryService
import io.hhplus.week2.service.ReservationInfo
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * 재고 서비스 구현체
 */
@Service
class InventoryServiceImpl(
    private val inventoryRepository: InventoryRepository
) : InventoryService {

    private val dateFormatter = DateTimeFormatter.ISO_DATE_TIME

    override fun getInventoryBySku(sku: String): Inventory? {
        return inventoryRepository.findBySku(sku)
    }

    override fun reserveInventory(sku: String, quantity: Int, ttlMinutes: Int): ReservationInfo? {
        val inventory = inventoryRepository.findBySku(sku) ?: return null

        if (inventory.available < quantity) {
            return null // 재고 부족
        }

        val reservationId = UUID.randomUUID().toString()
        val expiresAt = LocalDateTime.now().plusMinutes(ttlMinutes.toLong())

        val updatedInventory = inventory.copy(
            reserved = inventory.reserved + quantity,
            available = inventory.available - quantity
        )
        inventoryRepository.update(sku, updatedInventory)

        return ReservationInfo(
            id = reservationId,
            sku = sku,
            quantity = quantity,
            expiresAt = expiresAt.format(dateFormatter),
            reservedAt = LocalDateTime.now().format(dateFormatter)
        )
    }

    override fun deductInventory(sku: String, quantity: Int): Boolean {
        val inventory = inventoryRepository.findBySku(sku) ?: return false

        if (inventory.physical < quantity) {
            return false // 실제 재고 부족
        }

        val newStatus = when {
            inventory.physical - quantity <= 0 -> StockStatus.OUT_OF_STOCK
            inventory.physical - quantity <= 5 -> StockStatus.LOW_STOCK
            else -> StockStatus.IN_STOCK
        }

        val updatedInventory = inventory.copy(
            physical = inventory.physical - quantity,
            available = inventory.physical - quantity - inventory.reserved - inventory.safetyStock,
            status = newStatus,
            lastUpdated = LocalDateTime.now().format(dateFormatter)
        )
        inventoryRepository.update(sku, updatedInventory)

        return true
    }

    override fun cancelReservation(sku: String, quantity: Int): Boolean {
        val inventory = inventoryRepository.findBySku(sku) ?: return false

        val updatedInventory = inventory.copy(
            reserved = maxOf(0, inventory.reserved - quantity),
            available = inventory.available + quantity
        )
        inventoryRepository.update(sku, updatedInventory)

        return true
    }
}