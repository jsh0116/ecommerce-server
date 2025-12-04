package io.hhplus.ecommerce.application.usecases

import io.hhplus.ecommerce.application.events.OrderPaidEvent
import io.hhplus.ecommerce.application.services.ProductRankingService
import io.hhplus.ecommerce.domain.Order
import io.hhplus.ecommerce.domain.OrderItem
import io.hhplus.ecommerce.domain.UserCoupon
import io.hhplus.ecommerce.infrastructure.repositories.OrderRepository
import io.hhplus.ecommerce.infrastructure.repositories.ProductRepository
import io.hhplus.ecommerce.infrastructure.repositories.UserRepository
import io.hhplus.ecommerce.infrastructure.repositories.CouponRepository
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
    private val orderRepository: OrderRepository,
    private val productRepository: ProductRepository,
    private val userRepository: UserRepository,
    private val couponRepository: CouponRepository,
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
        val user = userRepository.findById(userId)
            ?: throw UserException.UserNotFound(userId.toString())

        // 상품 및 재고 확인
        val orderItems = mutableListOf<OrderItem>()
        for (req in items) {
            val product = productRepository.findById(req.productId)
                ?: throw ProductException.ProductNotFound(req.productId.toString())

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
        var userCoupon: UserCoupon? = null
        if (couponId != null) {
            userCoupon = couponRepository.findUserCoupon(userId, couponId)
            if (userCoupon == null || !userCoupon.isValid()) {
                throw CouponException.InvalidCoupon()
            }
        }

        // 주문 생성
        val totalAmount = orderItems.sumOf { it.subtotal }
        val discount = if (userCoupon != null) {
            (totalAmount * userCoupon.discountRate / 100.0).toLong()
        } else {
            0L
        }
        val finalAmount = totalAmount - discount

        val order = Order(
            id = System.currentTimeMillis(),
            userId = userId,
            items = orderItems,
            totalAmount = totalAmount,
            discountAmount = discount,
            finalAmount = finalAmount,
            couponId = couponId
        )

        return orderRepository.save(order)
    }

    /**
     * 주문 조회
     */
    fun getOrderById(orderId: Long): Order? {
        return orderRepository.findById(orderId)
    }

    /**
     * 사용자 주문 목록 조회
     */
    fun getOrdersByUserId(userId: Long): List<Order> {
        return orderRepository.findByUserId(userId)
    }

    /**
     * 주문 상태 업데이트
     */
    fun updateOrderStatus(orderId: Long, newStatus: String): Order? {
        val order = orderRepository.findById(orderId) ?: return null

        // Order 도메인에 상태 변경 메서드가 있다면 사용
        when (newStatus) {
            "CANCELLED" -> {
                if (order.canCancel()) {
                    order.cancel()
                } else {
                    throw OrderException.CannotCancelOrder(order.status)
                }
            }
            else -> {
                // 일반적인 상태 업데이트
                order.status = newStatus
            }
        }

        return orderRepository.save(order)
    }

    /**
     * 주문 취소 및 재고 복구
     */
    fun cancelOrder(orderId: Long, userId: Long): Order {
        val order = orderRepository.findById(orderId)
            ?: throw OrderException.OrderNotFound(orderId.toString())

        if (order.userId != userId) {
            throw OrderException.UnauthorizedOrderAccess()
        }

        if (!order.canCancel()) {
            throw OrderException.CannotCancelOrder(order.status)
        }

        // 주문 취소
        order.cancel()

        // 재고 복구
        for (item in order.items) {
            val inventory = inventoryRepository.findBySku(item.productId.toString())
            if (inventory != null) {
                inventory.restoreStock(item.quantity)
                inventoryRepository.save(inventory)
            }
        }

        return orderRepository.save(order)
    }

    /**
     * 결제 처리
     */
    fun processPayment(orderId: Long, userId: Long): PaymentResult {
        // 주문 확인
        val order = orderRepository.findById(orderId)
            ?: throw OrderException.OrderNotFound(orderId.toString())

        if (order.userId != userId) {
            throw OrderException.UnauthorizedOrderAccess()
        }

        if (!order.canPay()) {
            throw OrderException.CannotPayOrder()
        }

        // 잔액 확인 및 차감
        val user = userRepository.findById(userId)
            ?: throw UserException.UserNotFound(userId.toString())

        if (user.balance < order.finalAmount) {
            throw UserException.InsufficientBalance(
                required = order.finalAmount,
                current = user.balance
            )
        }

        user.balance = user.balance - order.finalAmount
        userRepository.save(user)

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
            val userCoupon = couponRepository.findUserCoupon(userId, order.couponId)
            if (userCoupon != null) {
                userCoupon.use()
                couponRepository.saveUserCoupon(userCoupon)
            }
        }

        // 주문 완료 처리
        order.complete()
        orderRepository.save(order)

        // 주문 결제 완료 이벤트 발행 (비동기 처리)
        // OrderPaidEventListener에서 비동기로 외부 데이터 전송을 처리합니다.
        // DB 트랜잭션 완료 후 별도의 스레드에서 실행되므로 트랜잭션 내 네트워크 대기가 없습니다.
        eventPublisher.publishEvent(OrderPaidEvent.from(order))

        // 결제 결과 반환
        return PaymentResult(
            orderId = order.id,
            paidAmount = order.finalAmount,
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