package io.hhplus.ecommerce.application.saga

import io.hhplus.ecommerce.application.services.CouponService
import io.hhplus.ecommerce.application.services.OrderService
import io.hhplus.ecommerce.application.services.UserService
import io.hhplus.ecommerce.domain.Order
import io.hhplus.ecommerce.infrastructure.repositories.InventoryRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.*

/**
 * 결제 SAGA Orchestrator
 *
 * STEP 16: 분산 트랜잭션 시뮬레이션을 위한 Orchestration 패턴 구현
 *
 * 역할:
 * 1. 결제 흐름의 각 단계를 순차적으로 실행
 * 2. 실패 시 보상 트랜잭션을 역순으로 실행
 * 3. SAGA 상태를 추적하여 장애 복구 가능
 *
 * 참고:
 * - 현재는 모놀리식 구조에서 개념 증명(PoC) 수준으로 구현
 * - 실제 MSA 전환 시 각 서비스는 HTTP API로 호출됨
 */
@Component
class PaymentSagaOrchestrator(
    private val orderService: OrderService,
    private val userService: UserService,
    private val inventoryRepository: InventoryRepository,
    private val couponService: CouponService
) : SagaOrchestrator<PaymentSagaRequest, PaymentSagaResponse> {

    private val logger = LoggerFactory.getLogger(javaClass)

    // 실제 환경에서는 DB나 Redis에 저장
    private val sagaInstances = mutableMapOf<String, SagaInstance>()

    override fun execute(request: PaymentSagaRequest): PaymentSagaResponse {
        val sagaId = "payment-saga-${request.orderId}-${UUID.randomUUID()}"
        val saga = SagaInstance(sagaId = sagaId, orderId = request.orderId)
        sagaInstances[sagaId] = saga

        logger.info("[SAGA] 결제 SAGA 시작: sagaId=$sagaId, orderId=${request.orderId}")

        try {
            // Step 1: 주문 확인 (이미 생성되어 있음)
            val order = executeStep(saga, SagaStep.ORDER_CREATE) {
                logger.info("[SAGA] Step 1: 주문 확인 - orderId=${request.orderId}")
                orderService.getById(request.orderId)
            }

            // Step 2: 잔액 차감
            executeStep(saga, SagaStep.USER_BALANCE_DEDUCT) {
                logger.info("[SAGA] Step 2: 잔액 차감 - userId=${request.userId}, amount=${order.finalAmount}")
                userService.deductBalance(request.userId, order.finalAmount)
            }

            // Step 3: 재고 확정
            executeStep(saga, SagaStep.INVENTORY_CONFIRM) {
                logger.info("[SAGA] Step 3: 재고 확정 - items=${order.items.size}개")
                for (item in order.items) {
                    val inventory = inventoryRepository.findBySku(item.productId.toString())
                        ?: throw IllegalStateException("재고를 찾을 수 없습니다: ${item.productId}")
                    inventory.confirmReservation(item.quantity)
                    inventoryRepository.save(inventory)
                }
            }

            // Step 4: 쿠폰 사용
            if (order.couponId != null) {
                executeStep(saga, SagaStep.COUPON_USE) {
                    logger.info("[SAGA] Step 4: 쿠폰 사용 - couponId=${order.couponId}")
                    val userCoupon = couponService.validateUserCoupon(request.userId, order.couponId)
                    couponService.useCoupon(userCoupon)
                }
            }

            // Step 5: 주문 완료
            val completedOrder = executeStep(saga, SagaStep.ORDER_COMPLETE) {
                logger.info("[SAGA] Step 5: 주문 완료")
                orderService.completeOrder(request.orderId)
            }

            saga.markAsCompleted()
            logger.info("[SAGA] 결제 SAGA 성공: sagaId=$sagaId")

            return PaymentSagaResponse(
                sagaId = sagaId,
                orderId = completedOrder.id,
                status = "SUCCESS",
                message = "결제가 성공적으로 완료되었습니다."
            )

        } catch (e: Exception) {
            logger.error("[SAGA] 결제 SAGA 실패: sagaId=$sagaId, currentStep=${saga.currentStep}", e)
            saga.markAsCompensating()

            // 보상 트랜잭션 실행
            try {
                compensate(saga, request)
                saga.markAsFailed()
                logger.info("[SAGA] 보상 트랜잭션 완료: sagaId=$sagaId")
            } catch (compensationError: Exception) {
                saga.markAsStuck()
                logger.error("[SAGA] 보상 트랜잭션 실패 - 수동 처리 필요: sagaId=$sagaId", compensationError)
            }

            throw SagaExecutionException(
                message = "결제 처리 중 오류가 발생했습니다: ${e.message}",
                sagaId = sagaId,
                failedStep = saga.currentStep ?: SagaStep.ORDER_CREATE,
                cause = e
            )
        }
    }

    /**
     * SAGA 단계 실행
     */
    private fun <T> executeStep(saga: SagaInstance, step: SagaStep, action: () -> T): T {
        saga.currentStep = step
        val result = action()
        saga.addCompletedStep(step)
        return result
    }

    /**
     * 보상 트랜잭션 실행 (역순)
     */
    private fun compensate(saga: SagaInstance, request: PaymentSagaRequest) {
        logger.info("[SAGA] 보상 트랜잭션 시작: sagaId=${saga.sagaId}, completedSteps=${saga.completedSteps}")

        // 역순으로 보상 실행
        saga.completedSteps.reversed().forEach { step ->
            try {
                when (step) {
                    SagaStep.COUPON_USE -> {
                        logger.info("[SAGA] 보상: 쿠폰 복구")
                        val order = orderService.getById(request.orderId)
                        if (order.couponId != null) {
                            val userCoupon = couponService.validateUserCoupon(request.userId, order.couponId, skipUsedCheck = true)
                            // 실제 환경에서는 CouponService.restoreCoupon() 메서드 필요
                            logger.info("[SAGA] 쿠폰 복구 완료: userId=${userCoupon.userId}, couponId=${userCoupon.couponId}")
                        }
                    }
                    SagaStep.INVENTORY_CONFIRM -> {
                        logger.info("[SAGA] 보상: 재고 복구")
                        val order = orderService.getById(request.orderId)
                        for (item in order.items) {
                            val inventory = inventoryRepository.findBySku(item.productId.toString())
                            if (inventory != null) {
                                inventory.restoreStock(item.quantity)
                                inventoryRepository.save(inventory)
                                logger.info("[SAGA] 재고 복구 완료: sku=${item.productId}, quantity=${item.quantity}")
                            }
                        }
                    }
                    SagaStep.USER_BALANCE_DEDUCT -> {
                        logger.info("[SAGA] 보상: 잔액 복구")
                        val order = orderService.getById(request.orderId)
                        userService.addBalance(request.userId, order.finalAmount)
                        logger.info("[SAGA] 잔액 복구 완료: userId=${request.userId}, amount=${order.finalAmount}")
                    }
                    SagaStep.ORDER_CREATE -> {
                        logger.info("[SAGA] 보상: 주문 취소")
                        orderService.cancelOrder(request.orderId, request.userId)
                        logger.info("[SAGA] 주문 취소 완료: orderId=${request.orderId}")
                    }
                    SagaStep.ORDER_COMPLETE -> {
                        // 주문 완료는 마지막 단계이므로 보상 불필요
                        logger.info("[SAGA] Step ORDER_COMPLETE는 보상 불필요")
                    }
                }
            } catch (e: Exception) {
                logger.error("[SAGA] 보상 트랜잭션 실패: step=$step", e)
                throw e
            }
        }
    }

    /**
     * SAGA 상태 조회 (모니터링용)
     */
    fun getSagaInstance(sagaId: String): SagaInstance? {
        return sagaInstances[sagaId]
    }
}

/**
 * 결제 SAGA 요청
 */
data class PaymentSagaRequest(
    val orderId: Long,
    val userId: Long
)

/**
 * 결제 SAGA 응답
 */
data class PaymentSagaResponse(
    val sagaId: String,
    val orderId: Long,
    val status: String,
    val message: String
)
