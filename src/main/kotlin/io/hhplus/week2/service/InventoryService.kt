package io.hhplus.week2.service

import io.hhplus.week2.domain.Inventory

/**
 * 재고 관련 도메인 서비스
 */
interface InventoryService {

    /**
     * SKU 코드로 재고 정보를 조회합니다.
     *
     * @param sku SKU 코드
     * @return 재고 정보 또는 null
     */
    fun getInventoryBySku(sku: String): Inventory?

    /**
     * 재고를 예약합니다. 예약된 재고는 가용 재고에서 차감됩니다.
     *
     * @param sku SKU 코드
     * @param quantity 예약 수량
     * @param ttlMinutes 예약 유효 시간
     * @return 예약 정보 또는 null (재고 부족 시)
     */
    fun reserveInventory(sku: String, quantity: Int, ttlMinutes: Int): ReservationInfo?

    /**
     * 실제 재고를 차감합니다. (결제 승인 후)
     *
     * @param sku SKU 코드
     * @param quantity 차감 수량
     * @return 성공 여부
     */
    fun deductInventory(sku: String, quantity: Int): Boolean

    /**
     * 예약된 재고를 취소하고 가용 재고를 복구합니다.
     *
     * @param sku SKU 코드
     * @param quantity 취소 수량
     * @return 성공 여부
     */
    fun cancelReservation(sku: String, quantity: Int): Boolean
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
