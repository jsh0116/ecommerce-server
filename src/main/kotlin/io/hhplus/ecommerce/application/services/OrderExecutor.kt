package io.hhplus.ecommerce.application.services

import io.hhplus.ecommerce.domain.Order
import io.hhplus.ecommerce.domain.OrderItem
import io.hhplus.ecommerce.domain.Product
import io.hhplus.ecommerce.domain.User
import io.hhplus.ecommerce.domain.UserCoupon
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 주문 실행 컴포넌트
 *
 * 주문 생성 및 취소 프로세스의 실제 실행 로직을 담당합니다.
 * Single Responsibility: 비즈니스 로직 실행만 집중
 */
@Component
class OrderExecutor(
    private val inventoryService: InventoryService,
    private val orderService: OrderService
) {
    companion object {
        private val logger = LoggerFactory.getLogger(OrderExecutor::class.java)
    }

    /**
     * 재고 예약 및 주문 아이템 생성
     *
     * @param product 상품
     * @param quantity 수량
     * @return 생성된 주문 아이템
     * @throws InventoryException.InsufficientStock 재고가 부족한 경우
     */
    fun reserveStockAndCreateOrderItem(product: Product, quantity: Int): OrderItem {
        // 재고 예약 (Product ID를 SKU로 사용)
        inventoryService.reserveStock(product.id.toString(), quantity)
        logger.debug("재고 예약 완료: productId=${product.id}, quantity=$quantity")

        return OrderItem.create(product, quantity)
    }

    /**
     * 주문 생성 실행
     *
     * @param user 사용자
     * @param orderItems 주문 아이템 목록
     * @param userCoupon 사용자 쿠폰 (nullable)
     * @return 생성된 주문
     */
    fun createOrder(user: User, orderItems: List<OrderItem>, userCoupon: UserCoupon?): Order {
        val order = orderService.createOrder(user, orderItems, userCoupon)
        logger.info("주문 생성 완료: orderId=${order.id}, userId=${user.id}, finalAmount=${order.finalAmount}")
        return order
    }

    /**
     * 재고 예약 취소 실행
     *
     * @param orderItems 주문 아이템 목록
     */
    fun cancelReservations(orderItems: List<OrderItem>) {
        for (item in orderItems) {
            inventoryService.cancelReservation(item.productId.toString(), item.quantity)
            logger.debug("재고 예약 취소 완료: productId=${item.productId}, quantity=${item.quantity}")
        }
    }

    /**
     * 주문 취소 실행
     *
     * @param orderId 주문 ID
     * @param userId 사용자 ID
     * @return 취소된 주문
     */
    fun cancelOrder(orderId: Long, userId: Long): Order {
        val order = orderService.cancelOrder(orderId, userId)
        logger.info("주문 취소 완료: orderId=$orderId")
        return order
    }
}
