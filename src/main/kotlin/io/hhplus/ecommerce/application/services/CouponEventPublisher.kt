package io.hhplus.ecommerce.application.services

import io.hhplus.ecommerce.application.events.CouponExhaustedEvent
import io.hhplus.ecommerce.application.events.CouponIssuedEvent
import io.hhplus.ecommerce.domain.Coupon
import io.hhplus.ecommerce.domain.UserCoupon
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/**
 * 쿠폰 이벤트 발행 컴포넌트
 *
 * 쿠폰 관련 이벤트 발행을 담당합니다.
 * Single Responsibility: 이벤트 발행만 집중
 *
 * 발행하는 이벤트:
 * - CouponIssuedEvent: 쿠폰 발급 시
 * - CouponExhaustedEvent: 쿠폰 소진 시
 */
@Component
class CouponEventPublisher(
    private val eventPublisher: ApplicationEventPublisher
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 쿠폰 발급 이벤트 발행
     *
     * @param userCoupon 발급된 사용자 쿠폰
     * @param remainingQuantity 남은 수량
     */
    fun publishCouponIssuedEvent(userCoupon: UserCoupon, remainingQuantity: Long) {
        eventPublisher.publishEvent(CouponIssuedEvent.from(userCoupon, remainingQuantity))
        logger.debug("CouponIssuedEvent 발행: couponId={}, userId={}, remaining={}",
            userCoupon.couponId, userCoupon.userId, remainingQuantity)
    }

    /**
     * 쿠폰 소진 이벤트 발행
     *
     * @param coupon 소진된 쿠폰
     */
    fun publishCouponExhaustedEvent(coupon: Coupon) {
        eventPublisher.publishEvent(CouponExhaustedEvent.from(coupon))
        logger.debug("CouponExhaustedEvent 발행: couponId={}", coupon.id)
    }
}
