package io.hhplus.ecommerce.application.services

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 결제 처리 Domain Service
 *
 * 결제 프로세스 전체를 조율하는 Domain Service입니다.
 * Validator, Executor, Publisher 패턴을 활용하여 책임을 명확히 분리합니다.
 *
 * 결제 프로세스:
 * 1. 주문 검증 (권한, 결제 가능 상태) → PaymentValidator
 * 2. 잔액 차감 → PaymentExecutor
 * 3. 재고 확정 (예약 → 실제 차감) → PaymentExecutor
 * 4. 판매량 증가 → PaymentExecutor
 * 5. 쿠폰 사용 → PaymentExecutor
 * 6. 주문 완료 처리 → PaymentExecutor
 * 7. 이벤트 발행 (비동기 작업 트리거) → PaymentEventPublisher
 */
@Service
class PaymentProcessingService(
    private val orderService: OrderService,
    private val paymentValidator: PaymentValidator,
    private val paymentExecutor: PaymentExecutor,
    private val paymentEventPublisher: PaymentEventPublisher
) {
    companion object {
        private val logger = LoggerFactory.getLogger(PaymentProcessingService::class.java)
    }

    /**
     * 결제 처리
     *
     * Validator, Executor, Publisher 패턴을 활용한 깔끔한 orchestration
     *
     * @param orderId 주문 ID
     * @param userId 사용자 ID
     * @return 결제 결과
     */
    @Transactional
    fun processPayment(orderId: Long, userId: Long): PaymentResult {
        logger.debug("결제 처리 시작: orderId=$orderId, userId=$userId")

        // 1. 주문 조회
        val order = orderService.getById(orderId)

        // 2. 검증 (PaymentValidator)
        paymentValidator.validatePayment(order, userId)

        // 3. 잔액 차감 (PaymentExecutor)
        val user = paymentExecutor.deductBalance(userId, order.finalAmount)

        // 4. 재고 확정 및 판매량 증가 (PaymentExecutor)
        paymentExecutor.confirmInventoryAndUpdateSales(order)

        // 5. 쿠폰 사용 처리 (PaymentExecutor)
        paymentExecutor.useCouponIfPresent(userId, order.couponId)

        // 6. 주문 완료 처리 (PaymentExecutor)
        val completedOrder = paymentExecutor.completeOrder(orderId)

        // 7. 이벤트 발행 (PaymentEventPublisher)
        paymentEventPublisher.publishOrderPaidEvent(completedOrder)

        // 8. 결제 결과 반환
        return PaymentResult(
            orderId = completedOrder.id,
            paidAmount = completedOrder.finalAmount,
            remainingBalance = user.balance,
            status = "SUCCESS"
        )
    }

    /**
     * 결제 결과 DTO
     */
    data class PaymentResult(
        val orderId: Long,
        val paidAmount: Long,
        val remainingBalance: Long,
        val status: String
    )
}
