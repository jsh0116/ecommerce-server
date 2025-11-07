package io.hhplus.week2.application

import io.hhplus.week2.domain.Order
import io.hhplus.week2.domain.OrderItem
import io.hhplus.week2.domain.Product
import io.hhplus.week2.domain.UserCoupon
import io.hhplus.week2.repository.OrderRepository
import io.hhplus.week2.repository.ProductRepository
import io.hhplus.week2.repository.UserRepository
import io.hhplus.week2.repository.CouponRepository
import io.hhplus.week2.repository.InventoryRepository
import io.hhplus.week2.infrastructure.service.DataTransmissionService
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.UUID

/**
 * 주문 유스케이스
 */
@Service
class OrderUseCase(
    private val orderRepository: OrderRepository,
    private val productRepository: ProductRepository,
    private val userRepository: UserRepository,
    private val couponRepository: CouponRepository,
    private val inventoryRepository: InventoryRepository,
    private val dataTransmissionService: DataTransmissionService?,
    private val productUseCase: ProductUseCase
) {
    /**
     * 주문 생성
     */
    fun createOrder(
        userId: String,
        items: List<OrderItemRequest>,
        couponId: String?
    ): Order {
        // 사용자 확인
        val user = userRepository.findById(userId)
            ?: throw IllegalStateException("사용자를 찾을 수 없습니다")

        // 상품 및 재고 확인
        val orderItems = mutableListOf<OrderItem>()
        for (req in items) {
            val product = productRepository.findById(req.productId)
                ?: throw IllegalStateException("상품을 찾을 수 없습니다: ${req.productId}")

            // 재고 확인 (Product ID를 SKU로 사용)
            val inventory = inventoryRepository.findBySku(product.id)
                ?: throw IllegalStateException("재고 정보를 찾을 수 없습니다: ${product.id}")

            if (!inventory.canReserve(req.quantity)) {
                throw IllegalStateException(
                    "재고 부족: ${product.name} (가용 재고 ${inventory.getAvailableStock()}개)"
                )
            }

            orderItems.add(OrderItem.create(product, req.quantity))
        }

        // 쿠폰 확인
        var userCoupon: UserCoupon? = null
        if (couponId != null) {
            userCoupon = couponRepository.findUserCoupon(userId, couponId)
            if (userCoupon == null || !userCoupon.isValid()) {
                throw IllegalStateException("유효하지 않은 쿠폰입니다")
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
            id = UUID.randomUUID().toString(),
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
    fun getOrderById(orderId: String): Order? {
        return orderRepository.findById(orderId)
    }

    /**
     * 사용자 주문 목록 조회
     */
    fun getOrdersByUserId(userId: String): List<Order> {
        return orderRepository.findByUserId(userId)
    }

    /**
     * 주문 상태 업데이트
     */
    fun updateOrderStatus(orderId: String, newStatus: String): Order? {
        val order = orderRepository.findById(orderId) ?: return null

        // Order 도메인에 상태 변경 메서드가 있다면 사용
        when (newStatus) {
            "CANCELLED" -> {
                if (order.canCancel()) {
                    order.cancel()
                } else {
                    throw IllegalStateException("취소할 수 없는 주문 상태입니다")
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
    fun cancelOrder(orderId: String, userId: String): Order {
        val order = orderRepository.findById(orderId)
            ?: throw IllegalStateException("주문을 찾을 수 없습니다")

        if (order.userId != userId) {
            throw IllegalStateException("주문을 찾을 수 없습니다")
        }

        if (!order.canCancel()) {
            throw IllegalStateException("취소할 수 없는 주문입니다")
        }

        // 주문 취소
        order.cancel()

        // 재고 복구
        for (item in order.items) {
            val inventory = inventoryRepository.findBySku(item.productId)
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
    fun processPayment(orderId: String, userId: String): PaymentResult {
        // 주문 확인
        val order = orderRepository.findById(orderId)
            ?: throw IllegalStateException("주문을 찾을 수 없습니다")

        if (order.userId != userId) {
            throw IllegalStateException("주문을 찾을 수 없습니다")
        }

        if (!order.canPay()) {
            throw IllegalStateException("결제할 수 없는 주문입니다")
        }

        // 잔액 확인 및 차감
        val user = userRepository.findById(userId)
            ?: throw IllegalStateException("사용자를 찾을 수 없습니다")

        if (user.balance < order.finalAmount) {
            throw IllegalStateException(
                "잔액 부족: 필요 ${order.finalAmount}원, 현재 ${user.balance}원"
            )
        }

        user.balance = user.balance - order.finalAmount
        userRepository.save(user)

        // 재고 차감 및 판매량 증가 (Product ID를 SKU로 사용)
        for (item in order.items) {
            val inventory = inventoryRepository.findBySku(item.productId)
                ?: throw IllegalStateException("재고 정보를 찾을 수 없습니다: ${item.productId}")

            // 실제 재고 차감
            inventory.confirmReservation(item.quantity)
            inventoryRepository.save(inventory)

            // 판매량 증가 (인기 상품 집계용)
            productUseCase.recordSale(item.productId, item.quantity)
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

        // 외부 데이터 전송 (실패해도 주문은 완료)
        try {
            dataTransmissionService?.send(
                DataPayload(
                    orderId = order.id,
                    userId = userId,
                    items = order.items,
                    totalAmount = order.totalAmount,
                    discountAmount = order.discountAmount,
                    paidAt = order.paidAt
                )
            )
        } catch (e: Exception) {
            System.err.println("데이터 전송 실패: ${e.message}")
            dataTransmissionService?.addToRetryQueue(order)
        }

        // 결제 결과 반환
        return PaymentResult(
            orderId = order.id,
            paidAmount = order.finalAmount,
            remainingBalance = user.balance,
            status = "SUCCESS"
        )
    }

    // 내부 DTO 정의
    data class OrderItemRequest(val productId: String, val quantity: Int)
    data class PaymentResult(
        val orderId: String,
        val paidAmount: Long,
        val remainingBalance: Long,
        val status: String
    )

    data class DataPayload(
        val orderId: String,
        val userId: String,
        val items: List<OrderItem>,
        val totalAmount: Long,
        val discountAmount: Long,
        val paidAt: LocalDateTime?
    )
}