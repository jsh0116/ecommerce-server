package io.hhplus.ecommerce.exception

/**
 * 상품 관련 예외
 */
object ProductException {
    class ProductNotFound(productId: String) : ResourceNotFoundException(
        errorCode = "PRODUCT_NOT_FOUND",
        message = "상품을 찾을 수 없습니다: $productId"
    )
}
