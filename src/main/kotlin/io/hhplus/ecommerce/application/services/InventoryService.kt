package io.hhplus.ecommerce.application.services

import io.hhplus.ecommerce.infrastructure.persistence.entity.InventoryJpaEntity
import io.hhplus.ecommerce.infrastructure.persistence.repository.InventoryJpaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 재고 비즈니스 로직 Service
 *
 * 비관적 락을 통해 동시성 제어
 * - reserve: 재고 예약 (주문 생성 시)
 * - confirmReservation: 예약 확정 (결제 완료 시)
 * - cancelReservation: 예약 취소
 * - restoreStock: 재고 복구 (주문 취소 시)
 */
@Service
class InventoryService(
    private val inventoryRepository: InventoryJpaRepository
) {
    /**
     * 재고 예약 (주문 생성 시)
     *
     * 비관적 락으로 다른 트랜잭션의 접근을 차단하고 원자적으로 처리
     */
    @Transactional
    fun reserveStock(sku: String, quantity: Int): InventoryJpaEntity {
        // 비관적 락 획득 - 다른 트랜잭션은 대기
        val inventory = inventoryRepository.findBySkuForUpdate(sku)
            ?: throw IllegalArgumentException("상품을 찾을 수 없습니다: $sku")

        // 재고 예약
        inventory.reserve(quantity)

        return inventoryRepository.save(inventory)
    }

    /**
     * 예약 확정 (결제 완료 시)
     *
     * 예약된 재고를 실제로 차감
     */
    @Transactional
    fun confirmReservation(sku: String, quantity: Int): InventoryJpaEntity {
        val inventory = inventoryRepository.findBySkuForUpdate(sku)
            ?: throw IllegalArgumentException("상품을 찾을 수 없습니다: $sku")

        inventory.confirmReservation(quantity)

        return inventoryRepository.save(inventory)
    }

    /**
     * 예약 취소 (결제 실패 또는 TTL 초과 시)
     *
     * 예약된 재고를 해제
     */
    @Transactional
    fun cancelReservation(sku: String, quantity: Int): InventoryJpaEntity {
        val inventory = inventoryRepository.findBySkuForUpdate(sku)
            ?: throw IllegalArgumentException("상품을 찾을 수 없습니다: $sku")

        inventory.cancelReservation(quantity)

        return inventoryRepository.save(inventory)
    }

    /**
     * 재고 복구 (주문 취소 시)
     *
     * 이미 차감된 실제 재고를 복구
     */
    @Transactional
    fun restoreStock(sku: String, quantity: Int): InventoryJpaEntity {
        val inventory = inventoryRepository.findBySkuForUpdate(sku)
            ?: throw IllegalArgumentException("상품을 찾을 수 없습니다: $sku")

        inventory.restoreStock(quantity)

        return inventoryRepository.save(inventory)
    }

    /**
     * 재고 조회 (읽기만 하고 수정하지 않음)
     */
    @Transactional(readOnly = true)
    fun getInventory(sku: String): InventoryJpaEntity? {
        return inventoryRepository.findBySku(sku)
    }

    /**
     * 재고 생성 (초기화용)
     */
    @Transactional
    fun createInventory(
        sku: String,
        physicalStock: Int,
        safetyStock: Int = 0
    ): InventoryJpaEntity {
        val inventory = InventoryJpaEntity(
            sku = sku,
            physicalStock = physicalStock,
            safetyStock = safetyStock
        )
        inventory.updateStatus()
        return inventoryRepository.save(inventory)
    }
}
