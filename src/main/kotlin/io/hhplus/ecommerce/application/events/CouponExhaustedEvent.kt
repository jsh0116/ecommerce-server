package io.hhplus.ecommerce.application.events

import io.hhplus.ecommerce.domain.Coupon
import org.springframework.context.ApplicationEvent
import java.time.LocalDateTime

/**
 * 쿠폰 소진 이벤트
 *
 * 쿠폰의 발급 가능 수량이 모두 소진되었을 때 발행됩니다.
 * 쿠폰 소진 알림, 통계 기록 등의 비동기 작업을 처리합니다.
 */
class CouponExhaustedEvent(
    val couponId: Long,
    val couponName: String,
    val totalQuantity: Int,
    val exhaustedAt: LocalDateTime,
    val coupon: Coupon,
    source: Any = Object()
) : ApplicationEvent(source) {
    companion object {
        fun from(coupon: Coupon): CouponExhaustedEvent {
            return CouponExhaustedEvent(
                couponId = coupon.id,
                couponName = coupon.name,
                totalQuantity = coupon.totalQuantity,
                exhaustedAt = LocalDateTime.now(),
                coupon = coupon
            )
        }
    }
}
