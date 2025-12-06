package io.hhplus.ecommerce.application.usecases

import io.hhplus.ecommerce.application.events.OrderPaidEvent
import io.hhplus.ecommerce.application.services.ProductRankingService
import io.hhplus.ecommerce.domain.Order
import io.hhplus.ecommerce.domain.OrderItem
import io.hhplus.ecommerce.application.services.OrderService
import io.hhplus.ecommerce.application.services.ProductService
import io.hhplus.ecommerce.application.services.UserService
import io.hhplus.ecommerce.application.services.CouponService
import io.hhplus.ecommerce.infrastructure.repositories.InventoryRepository
import io.hhplus.ecommerce.exception.*
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.UUID

/**
 * 주문 유스케이스
 *
 * 외부 데이터 전송은 OrderPaidEvent 발행을 통해 비동기로 처리됩니다.
 * 이를 통해 DB 트랜잭션 완료 후 별도의 스레드에서 외부 시스템과의 통신이 이루어지며,
 * DB 트랜잭션 중 네트워크 대기 시간이 발생하지 않습니다.
 */
@Service
class OrderUseCase(
    private val orderService: OrderService,
    private val productService: ProductService,
    private val userService: UserService,
    private val couponService: CouponService,
    private val inventoryRepository: InventoryRepository,
    private val productUseCase: ProductUseCase,
    private val productRankingService: ProductRankingService,
    private val eventPublisher: ApplicationEventPublisher
) {
    /**
     * 주문 생성
     */
    fun createOrder(
        userId: Long,
        items: List<OrderItemRequest>,
        couponId: Long?
    ): Order {
        // 사용자 확인
        val user = userService.getById(userId)

        // 상품 및 재고 확인
        val orderItems = mutableListOf<OrderItem>()
        for (req in items) {
            val product = productService.getById(req.productId)

            // 재고 확인 (Product ID를 SKU로 사용)
            val inventory = inventoryRepository.findBySku(product.id.toString())
                ?: throw InventoryException.InventoryNotFound(product.id.toString())

            if (!inventory.canReserve(req.quantity)) {
                throw InventoryException.InsufficientStock(
                    productName = product.name,
                    available = inventory.getAvailableStock(),
                    required = req.quantity
                )
            }

            orderItems.add(OrderItem.create(product, req.quantity))
        }

        // 쿠폰 확인
        val userCoupon = couponId?.let { couponService.validateUserCoupon(userId, it) }

        // 주문 생성
        return orderService.createOrder(user, orderItems, userCoupon)
    }

    /**
     * 주문 조회
     */
    fun getOrderById(orderId: Long): Order? {
        return try {
            orderService.getById(orderId)
        } catch (e: OrderException.OrderNotFound) {
            null
        }
    }

    /**
     * 사용자 주문 목록 조회
     */
    fun getOrdersByUserId(userId: Long): List<Order> {
        return orderService.getByUserId(userId)
    }

    /**
     * 주문 상태 업데이트
     */
    fun updateOrderStatus(orderId: Long, newStatus: String): Order? {
        return try {
            orderService.updateOrderStatus(orderId, newStatus)
        } catch (e: OrderException.OrderNotFound) {
            null
        }
    }

    /**
     * 주문 취소 및 재고 복구
     */
    fun cancelOrder(orderId: Long, userId: Long): Order {
        val order = orderService.cancelOrder(orderId, userId)

        // 재고 복구
        for (item in order.items) {
            val inventory = inventoryRepository.findBySku(item.productId.toString())
            if (inventory != null) {
                inventory.restoreStock(item.quantity)
                inventoryRepository.save(inventory)
            }
        }

        return order
    }

    /**
     * 결제 처리
     */
    fun processPayment(orderId: Long, userId: Long): PaymentResult {
        // 주문 확인
        val order = orderService.getById(orderId)

        if (order.userId != userId) {
            throw OrderException.UnauthorizedOrderAccess()
        }

        if (!order.canPay()) {
            throw OrderException.CannotPayOrder()
        }

        // 잔액 차감
        val user = userService.deductBalance(userId, order.finalAmount)

        // 재고 차감 및 판매량 증가 (Product ID를 SKU로 사용)
        for (item in order.items) {
            val inventory = inventoryRepository.findBySku(item.productId.toString())
                ?: throw InventoryException.InventoryNotFound(item.productId.toString())

            // 실제 재고 차감
            inventory.confirmReservation(item.quantity)
            inventoryRepository.save(inventory)

            // 판매량 증가 (인기 상품 집계용)
            productUseCase.recordSale(item.productId, item.quantity)

            // Redis 기반 실시간 랭킹 업데이트 (STEP 13)
            productRankingService.incrementSales(item.productId, item.quantity)
        }

        // 쿠폰 사용 처리
        if (order.couponId != null) {
            val userCoupon = couponService.validateUserCoupon(userId, order.couponId)
            couponService.useCoupon(userCoupon)
        }

        // 주문 완료 처리
        val completedOrder = orderService.completeOrder(orderId)

        // 주문 결제 완료 이벤트 발행 (비동기 처리)
        eventPublisher.publishEvent(OrderPaidEvent.from(completedOrder))

        // 결제 결과 반환
        return PaymentResult(
            orderId = completedOrder.id,
            paidAmount = completedOrder.finalAmount,
            remainingBalance = user.balance,
            status = "SUCCESS"
        )
    }

    // 내부 DTO 정의
    data class OrderItemRequest(val productId: Long, val quantity: Int)
    data class PaymentResult(
        val orderId: Long,
        val paidAmount: Long,
        val remainingBalance: Long,
        val status: String
    )

    data class DataPayload(
        val orderId: Long,
        val userId: Long,
        val items: List<OrderItem>,
        val totalAmount: Long,
        val discountAmount: Long,
        val paidAt: LocalDateTime?
    )
}