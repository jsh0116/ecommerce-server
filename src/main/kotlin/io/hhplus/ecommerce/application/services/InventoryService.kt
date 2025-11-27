package io.hhplus.ecommerce.application.services

import io.hhplus.ecommerce.exception.InventoryException
import io.hhplus.ecommerce.infrastructure.cache.CacheService
import io.hhplus.ecommerce.infrastructure.persistence.entity.InventoryJpaEntity
import io.hhplus.ecommerce.infrastructure.persistence.repository.InventoryJpaRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.PessimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class InventoryService(
    private val inventoryRepository: InventoryJpaRepository,
    private val cacheService: CacheService
) {
    private val objectMapper = ObjectMapper()

    companion object {
        private val logger = LoggerFactory.getLogger(InventoryService::class.java)
        private const val INVENTORY_CACHE_PREFIX = "inventory:"
        private const val INVENTORY_CACHE_TTL_SECONDS = 60 // 60초 TTL (재고는 자주 변경될 수 있음)
    }
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

            val saved = inventoryRepository.save(inventory)

            // 캐시 무효화: 재고 정보가 변경되었으므로 캐시 삭제
            cacheService.delete("$INVENTORY_CACHE_PREFIX$sku")
            logger.debug("캐시 무효화: sku=$sku (재고 예약 후)")

            saved
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

            val saved = inventoryRepository.save(inventory)

            // 캐시 무효화: 재고 정보가 변경되었으므로 캐시 삭제
            cacheService.delete("$INVENTORY_CACHE_PREFIX$sku")
            logger.debug("캐시 무효화: sku=$sku (예약 확정 후)")

            saved
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

            val saved = inventoryRepository.save(inventory)

            // 캐시 무효화: 재고 정보가 변경되었으므로 캐시 삭제
            cacheService.delete("$INVENTORY_CACHE_PREFIX$sku")
            logger.debug("캐시 무효화: sku=$sku (예약 취소 후)")

            saved
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

            val saved = inventoryRepository.save(inventory)

            // 캐시 무효화: 재고 정보가 변경되었으므로 캐시 삭제
            cacheService.delete("$INVENTORY_CACHE_PREFIX$sku")
            logger.debug("캐시 무효화: sku=$sku (재고 복구 후)")

            saved
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
        return try {
            logger.debug("재고 조회 시작 (캐시 포함): sku=$sku")

            val cacheKey = "$INVENTORY_CACHE_PREFIX$sku"

            // 1단계: 캐시에서 조회 시도 (Cache-Aside 패턴)
            val cachedValue = cacheService.get(cacheKey)
            if (cachedValue != null) {
                logger.debug("캐시 히트: sku=$sku")
                // JSON 문자열을 InventoryJpaEntity로 역직렬화
                return try {
                    objectMapper.readValue(cachedValue as String, InventoryJpaEntity::class.java)
                } catch (e: Exception) {
                    logger.warn("캐시 역직렬화 실패: sku=$sku, error=${e.message}")
                    // 캐시 삭제 후 DB에서 조회
                    cacheService.delete(cacheKey)
                    inventoryRepository.findBySku(sku)
                }
            }

            // 2단계: 캐시 미스 - DB에서 조회
            logger.debug("캐시 미스: sku=$sku, DB에서 조회 중")
            val inventory = inventoryRepository.findBySku(sku)

            // 3단계: DB에서 조회한 데이터를 캐시에 저장
            if (inventory != null) {
                try {
                    val jsonValue = objectMapper.writeValueAsString(inventory)
                    cacheService.set(cacheKey, jsonValue, INVENTORY_CACHE_TTL_SECONDS)
                    logger.debug("캐시 저장 완료: sku=$sku, ttl=${INVENTORY_CACHE_TTL_SECONDS}s")
                } catch (e: Exception) {
                    logger.warn("캐시 저장 실패: sku=$sku, error=${e.message}")
                    // 캐시 저장 실패는 무시하고 데이터 반환
                }
            }

            inventory
        } catch (e: Exception) {
            logger.error("재고 조회 중 예외 발생: sku=$sku, error=${e.message}", e)
            null
        }
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
}
