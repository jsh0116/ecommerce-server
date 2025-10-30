package io.hhplus.week2.repository

import io.hhplus.week2.domain.Inventory

/**
 * 재고 관련 저장소 인터페이스
 */
interface InventoryRepository {

    /**
     * SKU 코드로 재고 정보를 조회합니다.
     *
     * @param sku SKU 코드
     * @return 재고 정보 또는 null
     */
    fun findBySku(sku: String): Inventory?

    /**
     * 재고를 저장합니다.
     *
     * @param inventory 재고 정보
     */
    fun save(inventory: Inventory)

    /**
     * SKU 코드로 재고를 업데이트합니다.
     *
     * @param sku SKU 코드
     * @param inventory 새로운 재고 정보
     */
    fun update(sku: String, inventory: Inventory)
}