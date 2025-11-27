package io.hhplus.ecommerce.exception

/**
 * 결제 관련 예외
 */
object PaymentException {
    class PaymentNotFound(paymentId: String) : ResourceNotFoundException(
        errorCode = "PAYMENT_NOT_FOUND",
        message = "결제를 찾을 수 없습니다: $paymentId"
    )

    class PaymentLockTimeout(message: String = "결제 처리 대기 시간 초과") : BusinessRuleViolationException(
        errorCode = "PAYMENT_LOCK_TIMEOUT",
        message = message
    )

    class InvalidPaymentMethod : BusinessRuleViolationException(
        errorCode = "INVALID_PAYMENT_METHOD",
        message = "유효하지 않은 결제 방법입니다"
    )

    class PaymentAlreadyProcessed : BusinessRuleViolationException(
        errorCode = "PAYMENT_ALREADY_PROCESSED",
        message = "이미 처리된 결제입니다"
    )
}
