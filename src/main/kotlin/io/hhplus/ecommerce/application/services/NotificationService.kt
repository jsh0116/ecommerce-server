package io.hhplus.ecommerce.application.services

/**
 * 알림 서비스 인터페이스
 *
 * 다양한 알림 전송을 담당합니다.
 */
interface NotificationService {
    /**
     * 주문 완료 알림 발송
     *
     * @param userId 사용자 ID
     * @param orderId 주문 ID
     * @param totalAmount 결제 금액
     * @param itemCount 상품 개수
     */
    fun sendOrderCompletedNotification(
        userId: Long,
        orderId: Long,
        totalAmount: Long,
        itemCount: Int
    )

    /**
     * 주문 취소 알림 발송
     *
     * @param userId 사용자 ID
     * @param orderId 주문 ID
     */
    fun sendOrderCancelledNotification(
        userId: Long,
        orderId: Long
    )

    /**
     * 쿠폰 발급 알림 발송
     *
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID
     * @param couponName 쿠폰명
     */
    fun sendCouponIssuedNotification(
        userId: Long,
        couponId: Long,
        couponName: String
    )
}
