package io.hhplus.ecommerce.application.events

import io.hhplus.ecommerce.domain.Coupon
import io.hhplus.ecommerce.domain.UserCoupon
import org.springframework.context.ApplicationEvent
import java.time.LocalDateTime

/**
 * 쿠폰 발급 이벤트
 *
 * 쿠폰이 사용자에게 발급되었을 때 발행되며, 쿠폰 발급 알림 등의 비동기 작업을 처리합니다.
 */
class CouponIssuedEvent(
    val couponId: Long,
    val couponName: String,
    val userId: Long,
    val issuedAt: LocalDateTime,
    val remainingQuantity: Long,
    val userCoupon: UserCoupon,
    source: Any = Object()
) : ApplicationEvent(source) {
    companion object {
        fun from(userCoupon: UserCoupon, remainingQuantity: Long): CouponIssuedEvent {
            return CouponIssuedEvent(
                couponId = userCoupon.couponId,
                couponName = userCoupon.couponName,
                userId = userCoupon.userId,
                issuedAt = userCoupon.issuedAt,
                remainingQuantity = remainingQuantity,
                userCoupon = userCoupon
            )
        }
    }
}
