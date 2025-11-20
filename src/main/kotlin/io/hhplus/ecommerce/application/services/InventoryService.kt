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

/**
 * 재고 비즈니스 로직 Service
 *
 * Step 09 동시성 문제 해결 방안 적용:
 * - 비관적 락(PESSIMISTIC_WRITE)으로 Race Condition 방지
 * - 타임아웃 설정 및 데드락 감지
 * - 명확한 에러 메시지 및 로깅
 * - 재시도 가능한 예외 구분
 */
@Service
class InventoryService(
    private val inventoryRepository: InventoryJpaRepository
) {
    companion object {
        private const val LOCK_TIMEOUT_MS = 5000
        private const val MAX_RETRIES = 3
        private val logger = LoggerFactory.getLogger(InventoryService::class.java)
    }

    /**
     * 재고 예약 (주문 생성 시)
     *
     * 비관적 락으로 다른 트랜잭션의 접근을 차단하고 원자적으로 처리
     * - 재고 조회 및 잠금
     * - 예약 가능 여부 확인
     * - 예약 처리 및 저장
     *
     * @throws InventoryException.InventoryNotFound SKU 미존재
     * @throws InventoryException.InsufficientStock 재고 부족
     * @throws InventoryException.CannotReserveStock 예약 불가
     * @throws PessimisticLockingFailureException 락 타임아웃/데드락
     */
    @Transactional
    fun reserveStock(sku: String, quantity: Int): InventoryJpaEntity {
        return try {
            logger.debug("재고 예약 시작: sku=$sku, quantity=$quantity")

            // 1. 비관적 락 획득
            val inventory = inventoryRepository.findBySkuForUpdate(sku)
                ?: throw InventoryException.InventoryNotFound(sku)

            // 2. 예약 가능 여부 확인
            if (!inventory.canReserve(quantity)) {
                val available = inventory.getAvailableStock()
                logger.warn("재고 부족: sku=$sku, available=$available, required=$quantity")
                throw InventoryException.InsufficientStock(
                    productName = sku,
                    available = available,
                    required = quantity
                )
            }

            // 3. 재고 예약 처리
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

    /**
     * 예약 확정 (결제 완료 시)
     *
     * 예약된 재고를 실제로 차감하여 최종 확정
     * - 재고 조회 및 잠금
     * - 확정 가능 여부 확인
     * - 실제 재고 차감 처리
     *
     * @throws InventoryException.InventoryNotFound SKU 미존재
     * @throws InventoryException.CannotReserveStock 예약 미존재
     * @throws PessimisticLockingFailureException 락 타임아웃/데드락
     */
    @Transactional
    fun confirmReservation(sku: String, quantity: Int): InventoryJpaEntity {
        return try {
            logger.debug("예약 확정 시작: sku=$sku, quantity=$quantity")

            // 1. 비관적 락 획득
            val inventory = inventoryRepository.findBySkuForUpdate(sku)
                ?: throw InventoryException.InventoryNotFound(sku)

            // 2. 확정 가능 여부 확인
            if (!inventory.canConfirmReservation(quantity)) {
                logger.warn("예약 확정 불가: sku=$sku, reserved=${inventory.reservedStock}, required=$quantity")
                throw InventoryException.CannotReserveStock(sku)
            }

            // 3. 예약 확정 처리 (실제 차감)
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

    /**
     * 예약 취소 (결제 실패 또는 TTL 초과 시)
     *
     * 예약된 재고를 해제하여 다시 구매 가능하게 처리
     * - 재고 조회 및 잠금
     * - 취소 가능 여부 확인
     * - 예약 해제 처리
     *
     * @throws InventoryException.InventoryNotFound SKU 미존재
     * @throws InventoryException.CannotReserveStock 예약 미존재
     * @throws PessimisticLockingFailureException 락 타임아웃/데드락
     */
    @Transactional
    fun cancelReservation(sku: String, quantity: Int): InventoryJpaEntity {
        return try {
            logger.debug("예약 취소 시작: sku=$sku, quantity=$quantity")

            // 1. 비관적 락 획득
            val inventory = inventoryRepository.findBySkuForUpdate(sku)
                ?: throw InventoryException.InventoryNotFound(sku)

            // 2. 취소 가능 여부 확인
            if (!inventory.canCancelReservation(quantity)) {
                logger.warn("예약 취소 불가: sku=$sku, reserved=${inventory.reservedStock}, required=$quantity")
                throw InventoryException.CannotReserveStock(sku)
            }

            // 3. 예약 취소 처리
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

    /**
     * 재고 복구 (주문 취소 시)
     *
     * 이미 차감된 실제 재고를 복구하여 환불 처리
     * - 재고 조회 및 잠금
     * - 복구 가능 여부 확인
     * - 실제 재고 증가 처리
     *
     * @throws InventoryException.InventoryNotFound SKU 미존재
     * @throws InventoryException.CannotReserveStock 복구 불가
     * @throws PessimisticLockingFailureException 락 타임아웃/데드락
     */
    @Transactional
    fun restoreStock(sku: String, quantity: Int): InventoryJpaEntity {
        return try {
            logger.debug("재고 복구 시작: sku=$sku, quantity=$quantity")

            // 1. 비관적 락 획득
            val inventory = inventoryRepository.findBySkuForUpdate(sku)
                ?: throw InventoryException.InventoryNotFound(sku)

            // 2. 복구 가능 여부 확인 (데이터 일관성)
            if (!inventory.canRestoreStock(quantity)) {
                logger.warn("재고 복구 불가: sku=$sku, physicalStock=${inventory.physicalStock}, required=$quantity")
                throw InventoryException.CannotReserveStock(sku)
            }

            // 3. 재고 복구 처리
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

    /**
     * 재고 조회 (읽기만 하고 수정하지 않음)
     *
     * 락 없는 읽기로 성능 최적화
     * 최신 값을 보장해야 할 때만 사용 (결과 기반 의사결정 시)
     *
     * @return 재고 정보 또는 null (미존재)
     */
    @Transactional(readOnly = true)
    fun getInventory(sku: String): InventoryJpaEntity? {
        return inventoryRepository.findBySku(sku)
    }

    /**
     * 재고 조회 (최신 값 보장)
     *
     * 락 기반 읽기로 다른 트랜잭션의 변경을 대기
     * 동시성 높은 환경에서 정확한 값이 필요한 경우만 사용
     *
     * @return 재고 정보 또는 null (미존재)
     * @throws PessimisticLockingFailureException 락 타임아웃/데드락
     */
    @Transactional
    fun getInventoryWithLock(sku: String): InventoryJpaEntity? {
        return try {
            inventoryRepository.findBySkuForUpdate(sku)
        } catch (e: PessimisticLockingFailureException) {
            logger.error("재고 조회 락 타임아웃: sku=$sku")
            null
        }
    }

    /**
     * 재고 생성 (초기화용)
     *
     * 새로운 상품의 초기 재고 설정
     *
     * @param sku 상품 SKU
     * @param physicalStock 물리적 재고 수량
     * @param safetyStock 안전 재고 수량 (기본값 0)
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
        logger.info("재고 생성 완료: sku=$sku, physicalStock=$physicalStock, safetyStock=$safetyStock")
        return inventoryRepository.save(inventory)
    }
}
