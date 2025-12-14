package io.hhplus.ecommerce.application.services.impl

import io.hhplus.ecommerce.application.services.NotificationService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 알림 서비스 Mock 구현
 *
 * 실제 알림톡 API 연동 전까지 사용하는 Mock 서비스입니다.
 * 로그만 출력하고 성공 처리합니다.
 */
@Service
class MockNotificationService : NotificationService {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun sendOrderCompletedNotification(
        userId: Long,
        orderId: Long,
        totalAmount: Long,
        itemCount: Int
    ) {
        logger.info(
            "[알림톡 Mock] 주문 완료 알림 - userId: {}, orderId: {}, 결제금액: {}원, 상품수: {}개",
            userId, orderId, totalAmount, itemCount
        )
        // TODO: 실제 알림톡 API 연동 시 구현
        // 예: kakaoNotificationClient.sendOrderCompleted(...)
    }

    override fun sendOrderCancelledNotification(userId: Long, orderId: Long) {
        logger.info(
            "[알림톡 Mock] 주문 취소 알림 - userId: {}, orderId: {}",
            userId, orderId
        )
        // TODO: 실제 알림톡 API 연동 시 구현
    }

    override fun sendCouponIssuedNotification(userId: Long, couponId: Long, couponName: String) {
        logger.info(
            "[알림톡 Mock] 쿠폰 발급 알림 - userId: {}, couponId: {}, 쿠폰명: {}",
            userId, couponId, couponName
        )
        // TODO: 실제 알림톡 API 연동 시 구현
    }
}
