package io.hhplus.ecommerce.exception

/**
 * 주문 관련 예외
 */
object OrderException {
    class OrderNotFound(orderId: String) : ResourceNotFoundException(
        errorCode = "ORDER_NOT_FOUND",
        message = "주문을 찾을 수 없습니다: $orderId"
    )

    class CannotPayOrder : BusinessRuleViolationException(
        errorCode = "CANNOT_PAY_ORDER",
        message = "결제할 수 없는 주문입니다"
    )

    class CannotCancelOrder(status: String) : BusinessRuleViolationException(
        errorCode = "CANNOT_CANCEL_ORDER",
        message = "취소할 수 없는 주문 상태입니다: $status"
    )

    class UnauthorizedOrderAccess : UnauthorizedException(
        errorCode = "UNAUTHORIZED_ORDER_ACCESS",
        message = "접근 권한이 없는 주문입니다"
    )
}
