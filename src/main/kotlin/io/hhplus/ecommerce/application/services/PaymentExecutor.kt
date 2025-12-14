package io.hhplus.ecommerce.application.services

import io.hhplus.ecommerce.domain.Order
import io.hhplus.ecommerce.domain.User
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 결제 실행 컴포넌트
 *
 * 결제 프로세스의 실제 실행 로직을 담당합니다.
 * Single Responsibility: 비즈니스 로직 실행만 집중
 */
@Component
class PaymentExecutor(
    private val userService: UserService,
    private val inventoryService: InventoryService,
    private val couponService: CouponService,
    private val productRankingService: ProductRankingService,
    private val orderService: OrderService
) {
    companion object {
        private val logger = LoggerFactory.getLogger(PaymentExecutor::class.java)
    }

    /**
     * 잔액 차감 실행
     *
     * @param userId 사용자 ID
     * @param amount 차감할 금액
     * @return 차감 후 사용자 정보
     */
    fun deductBalance(userId: Long, amount: Long): User {
        val user = userService.deductBalance(userId, amount)
        logger.debug("잔액 차감 완료: userId=$userId, amount=$amount, remainingBalance=${user.balance}")
        return user
    }

    /**
     * 재고 확정 및 판매량 증가 실행
     *
     * @param order 주문
     */
    fun confirmInventoryAndUpdateSales(order: Order) {
        for (item in order.items) {
            // 재고 예약 확정 (reservedStock → physicalStock 차감)
            inventoryService.confirmReservation(item.productId.toString(), item.quantity)
            logger.debug("재고 확정 완료: productId=${item.productId}, quantity=${item.quantity}")

            // Redis 기반 실시간 랭킹 업데이트
            productRankingService.incrementSales(item.productId, item.quantity)
            logger.debug("판매량 업데이트 완료: productId=${item.productId}, quantity=${item.quantity}")
        }
    }

    /**
     * 쿠폰 사용 처리 실행
     *
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID (nullable)
     */
    fun useCouponIfPresent(userId: Long, couponId: Long?) {
        if (couponId != null) {
            val userCoupon = couponService.validateUserCoupon(userId, couponId)
            couponService.useCoupon(userCoupon)
            logger.debug("쿠폰 사용 완료: couponId=$couponId")
        }
    }

    /**
     * 주문 완료 처리 실행
     *
     * @param orderId 주문 ID
     * @return 완료된 주문
     */
    fun completeOrder(orderId: Long): Order {
        val completedOrder = orderService.completeOrder(orderId)
        logger.info("주문 완료 처리: orderId=$orderId, status=${completedOrder.status}")
        return completedOrder
    }
}
