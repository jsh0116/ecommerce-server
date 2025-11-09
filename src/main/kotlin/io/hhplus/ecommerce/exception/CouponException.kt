package io.hhplus.ecommerce.exception

/**
 * 쿠폰 관련 예외
 */
object CouponException {
    class CouponNotFound(couponId: String) : ResourceNotFoundException(
        errorCode = "COUPON_NOT_FOUND",
        message = "쿠폰을 찾을 수 없습니다: $couponId"
    )

    class AlreadyIssuedCoupon : BusinessRuleViolationException(
        errorCode = "ALREADY_ISSUED_COUPON",
        message = "이미 발급받은 쿠폰입니다"
    )

    class CouponExhausted : BusinessRuleViolationException(
        errorCode = "COUPON_EXHAUSTED",
        message = "쿠폰이 모두 소진되었습니다"
    )

    class InvalidCoupon : BusinessRuleViolationException(
        errorCode = "INVALID_COUPON",
        message = "유효하지 않은 쿠폰입니다"
    )

    class CannotIssueCoupon : BusinessRuleViolationException(
        errorCode = "CANNOT_ISSUE_COUPON",
        message = "쿠폰을 발급할 수 없습니다"
    )

    class CannotUseCoupon : BusinessRuleViolationException(
        errorCode = "CANNOT_USE_COUPON",
        message = "사용할 수 없는 쿠폰입니다"
    )
}
