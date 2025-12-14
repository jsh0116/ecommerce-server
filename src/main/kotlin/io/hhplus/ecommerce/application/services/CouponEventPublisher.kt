package io.hhplus.ecommerce.application.services

import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/**
 * 쿠폰 이벤트 발행 컴포넌트
 *
 * 쿠폰 관련 이벤트 발행을 담당합니다.
 * Single Responsibility: 이벤트 발행만 집중
 *
 * 현재는 사용하지 않지만, 향후 CouponIssuedEvent 등을
 * 추가할 때 사용할 수 있도록 구조만 준비해둡니다.
 */
@Component
class CouponEventPublisher(
    private val eventPublisher: ApplicationEventPublisher
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 쿠폰 발급 이벤트 발행 (미래 확장용)
     *
     * @param couponId 쿠폰 ID
     * @param userId 사용자 ID
     * @param remainingQuantity 남은 수량
     */
    fun publishCouponIssuedEvent(couponId: Long, userId: Long, remainingQuantity: Long) {
        // TODO: CouponIssuedEvent 구현 시 활성화
        // eventPublisher.publishEvent(
        //     CouponIssuedEvent(
        //         couponId = couponId,
        //         userId = userId,
        //         remainingQuantity = remainingQuantity,
        //         issuedAt = LocalDateTime.now()
        //     )
        // )
        logger.debug("CouponIssuedEvent 발행 (미구현): couponId={}, userId={}, remaining={}", couponId, userId, remainingQuantity)
    }

    /**
     * 쿠폰 소진 이벤트 발행 (미래 확장용)
     *
     * @param couponId 쿠폰 ID
     */
    fun publishCouponExhaustedEvent(couponId: Long) {
        // TODO: CouponExhaustedEvent 구현 시 활성화
        // eventPublisher.publishEvent(CouponExhaustedEvent(couponId = couponId))
        logger.debug("CouponExhaustedEvent 발행 (미구현): couponId={}", couponId)
    }
}
