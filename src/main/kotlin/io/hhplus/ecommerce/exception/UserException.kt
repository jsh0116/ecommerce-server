package io.hhplus.ecommerce.exception

/**
 * 사용자 관련 예외
 */
object UserException {
    class UserNotFound(userId: String) : ResourceNotFoundException(
        errorCode = "USER_NOT_FOUND",
        message = "사용자를 찾을 수 없습니다: $userId"
    )

    class InsufficientBalance(required: Long, current: Long) : BusinessRuleViolationException(
        errorCode = "INSUFFICIENT_BALANCE",
        message = "잔액이 부족합니다. 필요: ${required}원, 현재: ${current}원"
    )
}