package io.hhplus.ecommerce.application.services

import io.hhplus.ecommerce.exception.InventoryException
import io.hhplus.ecommerce.infrastructure.persistence.entity.InventoryJpaEntity
import io.hhplus.ecommerce.infrastructure.persistence.repository.InventoryJpaRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.PessimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class InventoryService(
    private val inventoryRepository: InventoryJpaRepository
) {
    @Transactional
    fun reserveStock(sku: String, quantity: Int): InventoryJpaEntity {
        return try {
            logger.debug("재고 예약 시작: sku=$sku, quantity=$quantity")

            val inventory = inventoryRepository.findBySkuForUpdate(sku)
                ?: throw InventoryException.InventoryNotFound(sku)

            if (!inventory.canReserve(quantity)) {
                val available = inventory.getAvailableStock()
                logger.warn("재고 부족: sku=$sku, available=$available, required=$quantity")
                throw InventoryException.InsufficientStock(
                    productName = sku,
                    available = available,
                    required = quantity
                )
            }

            inventory.reserve(quantity)
            logger.info("재고 예약 완료: sku=$sku, reservedQuantity=$quantity, remainingAvailable=${inventory.getAvailableStock()}")

            inventoryRepository.save(inventory)
        } catch (e: PessimisticLockingFailureException) {
            logger.error("재고 락 타임아웃 (데드락 가능성): sku=$sku, cause=${e.message}")
            throw InventoryException.CannotReserveStock(sku)
        } catch (e: DataIntegrityViolationException) {
            logger.error("데이터 무결성 위반 (음수 재고 방지): sku=$sku, cause=${e.message}")
            throw InventoryException.CannotReserveStock(sku)
        }
    }

    @Transactional
    fun confirmReservation(sku: String, quantity: Int): InventoryJpaEntity {
        return try {
            logger.debug("예약 확정 시작: sku=$sku, quantity=$quantity")

            val inventory = inventoryRepository.findBySkuForUpdate(sku)
                ?: throw InventoryException.InventoryNotFound(sku)

            if (!inventory.canConfirmReservation(quantity)) {
                logger.warn("예약 확정 불가: sku=$sku, reserved=${inventory.reservedStock}, required=$quantity")
                throw InventoryException.CannotReserveStock(sku)
            }

            inventory.confirmReservation(quantity)
            logger.info("예약 확정 완료: sku=$sku, confirmedQuantity=$quantity, physicalStock=${inventory.physicalStock}")

            inventoryRepository.save(inventory)
        } catch (e: PessimisticLockingFailureException) {
            logger.error("재고 락 타임아웃 (데드락 가능성): sku=$sku, cause=${e.message}")
            throw InventoryException.CannotReserveStock(sku)
        } catch (e: DataIntegrityViolationException) {
            logger.error("데이터 무결성 위반 (음수 재고 방지): sku=$sku, cause=${e.message}")
            throw InventoryException.CannotReserveStock(sku)
        }
    }

    @Transactional
    fun cancelReservation(sku: String, quantity: Int): InventoryJpaEntity {
        return try {
            logger.debug("예약 취소 시작: sku=$sku, quantity=$quantity")

            val inventory = inventoryRepository.findBySkuForUpdate(sku)
                ?: throw InventoryException.InventoryNotFound(sku)

            if (!inventory.canCancelReservation(quantity)) {
                logger.warn("예약 취소 불가: sku=$sku, reserved=${inventory.reservedStock}, required=$quantity")
                throw InventoryException.CannotReserveStock(sku)
            }

            inventory.cancelReservation(quantity)
            logger.info("예약 취소 완료: sku=$sku, canceledQuantity=$quantity, releasedAvailable=${inventory.getAvailableStock()}")

            inventoryRepository.save(inventory)
        } catch (e: PessimisticLockingFailureException) {
            logger.error("재고 락 타임아웃 (데드락 가능성): sku=$sku, cause=${e.message}")
            throw InventoryException.CannotReserveStock(sku)
        } catch (e: DataIntegrityViolationException) {
            logger.error("데이터 무결성 위반: sku=$sku, cause=${e.message}")
            throw InventoryException.CannotReserveStock(sku)
        }
    }

    @Transactional
    fun restoreStock(sku: String, quantity: Int): InventoryJpaEntity {
        return try {
            logger.debug("재고 복구 시작: sku=$sku, quantity=$quantity")

            val inventory = inventoryRepository.findBySkuForUpdate(sku)
                ?: throw InventoryException.InventoryNotFound(sku)

            if (!inventory.canRestoreStock(quantity)) {
                logger.warn("재고 복구 불가: sku=$sku, physicalStock=${inventory.physicalStock}, required=$quantity")
                throw InventoryException.CannotReserveStock(sku)
            }

            inventory.restoreStock(quantity)
            logger.info("재고 복구 완료: sku=$sku, restoredQuantity=$quantity, totalStock=${inventory.physicalStock}")

            inventoryRepository.save(inventory)
        } catch (e: PessimisticLockingFailureException) {
            logger.error("재고 락 타임아웃 (데드락 가능성): sku=$sku, cause=${e.message}")
            throw InventoryException.CannotReserveStock(sku)
        } catch (e: DataIntegrityViolationException) {
            logger.error("데이터 무결성 위반: sku=$sku, cause=${e.message}")
            throw InventoryException.CannotReserveStock(sku)
        }
    }

    @Transactional(readOnly = true)
    fun getInventory(sku: String): InventoryJpaEntity? {
        return inventoryRepository.findBySku(sku)
    }

    @Transactional
    fun getInventoryWithLock(sku: String): InventoryJpaEntity? {
        return try {
            inventoryRepository.findBySkuForUpdate(sku)
        } catch (e: PessimisticLockingFailureException) {
            logger.error("재고 조회 락 타임아웃: sku=$sku")
            null
        }
    }

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
        logger.info("재고 생성 완료: sku=$sku, physicalStock=$physicalStock, safetyStock=$safetyStock")
        return inventoryRepository.save(inventory)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(InventoryService::class.java)
    }
}
