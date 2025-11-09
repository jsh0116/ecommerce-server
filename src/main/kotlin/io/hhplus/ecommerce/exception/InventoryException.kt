package io.hhplus.ecommerce.exception

/**
 * 재고 관련 예외
 */
object InventoryException {
    class InventoryNotFound(sku: String) : ResourceNotFoundException(
        errorCode = "INVENTORY_NOT_FOUND",
        message = "재고 정보를 찾을 수 없습니다: $sku"
    )

    class InsufficientStock(productName: String, available: Int, required: Int) : BusinessRuleViolationException(
        errorCode = "INSUFFICIENT_STOCK",
        message = "재고 부족: $productName (가용 재고 ${available}개, 요청 ${required}개)"
    )

    class CannotReserveStock(productName: String) : BusinessRuleViolationException(
        errorCode = "CANNOT_RESERVE_STOCK",
        message = "재고를 예약할 수 없습니다: $productName"
    )
}
