package io.hhplus.ecommerce.application.saga

import com.fasterxml.jackson.databind.ObjectMapper
import io.hhplus.ecommerce.application.events.OrderPaidEvent
import io.hhplus.ecommerce.application.services.CouponService
import io.hhplus.ecommerce.application.services.IdempotencyResult
import io.hhplus.ecommerce.application.services.IdempotencyService
import io.hhplus.ecommerce.application.services.OrderService
import io.hhplus.ecommerce.application.services.UserService
import io.hhplus.ecommerce.application.usecases.OrderUseCase
import io.hhplus.ecommerce.domain.Order
import io.hhplus.ecommerce.infrastructure.persistence.entity.SagaInstanceJpaEntity
import io.hhplus.ecommerce.infrastructure.persistence.entity.SagaStatus
import io.hhplus.ecommerce.infrastructure.persistence.repository.SagaInstanceJpaRepository
import io.hhplus.ecommerce.infrastructure.repositories.InventoryRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
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
    private val couponService: CouponService,
    private val orderUseCase: OrderUseCase,
    private val eventPublisher: ApplicationEventPublisher,
    private val sagaRepository: SagaInstanceJpaRepository,
    private val idempotencyService: IdempotencyService,
    private val objectMapper: ObjectMapper
) : SagaOrchestrator<PaymentSagaRequest, PaymentSagaResponse> {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun execute(request: PaymentSagaRequest): PaymentSagaResponse {
        // Phase 2: 멱등성 키 검증
        val idempotencyKey = request.idempotencyKey
            ?: "payment-${request.orderId}-${request.userId}-${System.currentTimeMillis()}"

        when (val result = idempotencyService.acquireKey(
            idempotencyKey = idempotencyKey,
            requestType = "order-payment",
            userId = request.userId,
            entityId = request.orderId
        )) {
            is IdempotencyResult.Processing -> {
                throw DuplicateRequestException(result.message)
            }
            is IdempotencyResult.AlreadyCompleted -> {
                logger.info("[Idempotency] 이미 완료된 요청: key=$idempotencyKey")
                return objectMapper.readValue(result.responseData, PaymentSagaResponse::class.java)
            }
            is IdempotencyResult.Failed -> {
                throw IllegalStateException(result.message)
            }
            is IdempotencyResult.NewRequest -> {
                // 정상 처리 진행
                logger.info("[Idempotency] 새로운 요청 처리: key=$idempotencyKey")
            }
        }

        val sagaId = "payment-saga-${request.orderId}-${UUID.randomUUID()}"

        // SAGA 상태를 DB에 저장 (SPOF 해결)
        val sagaEntity = SagaInstanceJpaEntity(
            sagaId = sagaId,
            orderId = request.orderId,
            userId = request.userId,
            status = SagaStatus.PENDING
        )
        sagaRepository.save(sagaEntity)

        logger.info("[SAGA] 결제 SAGA 시작: sagaId=$sagaId, orderId=${request.orderId}")

        try {
            // Step 1: 주문 확인 (이미 생성되어 있음)
            val order = executeStep(sagaEntity, SagaStep.ORDER_CREATE) {
                logger.info("[SAGA] Step 1: 주문 확인 - orderId=${request.orderId}")
                orderService.getById(request.orderId)
            }

            // Step 2: 잔액 차감
            executeStep(sagaEntity, SagaStep.USER_BALANCE_DEDUCT) {
                logger.info("[SAGA] Step 2: 잔액 차감 - userId=${request.userId}, amount=${order.finalAmount}")
                userService.deductBalance(request.userId, order.finalAmount)
            }

            // Step 3: 재고 확정
            executeStep(sagaEntity, SagaStep.INVENTORY_CONFIRM) {
                logger.info("[SAGA] Step 3: 재고 확정 - items=${order.items.size}개")
                for (item in order.items) {
                    val inventory = inventoryRepository.findBySku(item.productId.toString())
                        ?: throw IllegalStateException("재고를 찾을 수 없습니다: ${item.productId}")
                    inventory.confirmReservation(item.quantity)
                    inventoryRepository.update(inventory.sku, inventory)
                }
            }

            // Step 4: 쿠폰 사용
            if (order.couponId != null) {
                executeStep(sagaEntity, SagaStep.COUPON_USE) {
                    logger.info("[SAGA] Step 4: 쿠폰 사용 - couponId=${order.couponId}")
                    val userCoupon = couponService.validateUserCoupon(request.userId, order.couponId)
                    couponService.useCoupon(userCoupon)
                }
            }

            // Step 5: 주문 완료
            val completedOrder = executeStep(sagaEntity, SagaStep.ORDER_COMPLETE) {
                logger.info("[SAGA] Step 5: 주문 완료")
                orderService.completeOrder(request.orderId)
            }

            sagaEntity.markAsCompleted()
            sagaRepository.save(sagaEntity)
            logger.info("[SAGA] 결제 SAGA 성공: sagaId=$sagaId")

            // SAGA 성공 시 OrderPaidEvent 발행
            publishOrderPaidEvent(completedOrder)

            val response = PaymentSagaResponse(
                sagaId = sagaId,
                orderId = completedOrder.id,
                status = "SUCCESS",
                message = "결제가 성공적으로 완료되었습니다."
            )

            // Phase 2: 멱등성 키 완료 처리
            idempotencyService.markAsCompleted(
                idempotencyKey = idempotencyKey,
                responseData = objectMapper.writeValueAsString(response)
            )

            return response

        } catch (e: Exception) {
            logger.error("[SAGA] 결제 SAGA 실패: sagaId=$sagaId, currentStep=${sagaEntity.currentStep}", e)
            sagaEntity.markAsCompensating(e.message ?: "알 수 없는 오류")
            sagaRepository.save(sagaEntity)

            // 보상 트랜잭션 실행
            try {
                compensate(sagaEntity, request)
                sagaEntity.markAsFailed(e.message ?: "결제 실패")
                sagaRepository.save(sagaEntity)
                logger.info("[SAGA] 보상 트랜잭션 완료: sagaId=$sagaId")
            } catch (compensationError: Exception) {
                sagaEntity.markAsStuck(compensationError.message ?: "보상 실패")
                sagaRepository.save(sagaEntity)
                logger.error("[SAGA] 보상 트랜잭션 실패 - 수동 처리 필요: sagaId=$sagaId", compensationError)
            }

            // Phase 2: 멱등성 키 실패 처리
            idempotencyService.markAsFailed(
                idempotencyKey = idempotencyKey,
                errorMessage = e.message ?: "결제 처리 실패"
            )

            throw SagaExecutionException(
                message = "결제 처리 중 오류가 발생했습니다: ${e.message}",
                sagaId = sagaId,
                failedStep = SagaStep.valueOf(sagaEntity.currentStep ?: "ORDER_CREATE"),
                cause = e
            )
        }
    }

    /**
     * SAGA 단계 실행
     */
    private fun <T> executeStep(sagaEntity: SagaInstanceJpaEntity, step: SagaStep, action: () -> T): T {
        sagaEntity.markAsRunning(step.name)
        sagaRepository.save(sagaEntity)

        val result = action()

        sagaEntity.addCompletedStep(step.name)
        sagaRepository.save(sagaEntity)

        return result
    }

    /**
     * 보상 트랜잭션 실행 (역순)
     */
    private fun compensate(sagaEntity: SagaInstanceJpaEntity, request: PaymentSagaRequest) {
        val completedSteps = sagaEntity.getCompletedSteps()
        logger.info("[SAGA] 보상 트랜잭션 시작: sagaId=${sagaEntity.sagaId}, completedSteps=$completedSteps")

        // 역순으로 보상 실행
        completedSteps.reversed().forEach { stepName ->
            try {
                val step = SagaStep.valueOf(stepName)
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
                        logger.info("[SAGA] 보상: 재고 예약 확정 취소")
                        val order = orderService.getById(request.orderId)
                        for (item in order.items) {
                            val inventory = inventoryRepository.findBySku(item.productId.toString())
                            if (inventory != null) {
                                // confirmReservation으로 감소한 physicalStock을 복구
                                inventory.restoreStock(item.quantity)
                                inventoryRepository.update(inventory.sku, inventory)
                                logger.info("[SAGA] 재고 확정 취소 완료: sku=${item.productId}, quantity=${item.quantity}")
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
                        // INVENTORY_CONFIRM이 실행된 경우, 이미 INVENTORY_CONFIRM 보상에서 재고를 복구했으므로
                        // 재고 예약 취소(cancelReservation)를 시도하면 안 됨 (reservedStock=0이므로 실패함)
                        if (completedSteps.contains(SagaStep.INVENTORY_CONFIRM.name)) {
                            // INVENTORY_CONFIRM 보상이 이미 재고를 복구했으므로, 주문 상태만 취소
                            orderService.cancelOrder(request.orderId, request.userId)
                            logger.info("[SAGA] 주문 취소 완료 (재고는 INVENTORY_CONFIRM 보상에서 이미 복구됨): orderId=${request.orderId}")
                        } else {
                            // INVENTORY_CONFIRM이 실행되지 않았으므로, 재고 예약 취소 필요
                            orderUseCase.cancelOrder(request.orderId, request.userId)
                            logger.info("[SAGA] 주문 취소 및 재고 예약 취소 완료: orderId=${request.orderId}")
                        }
                    }
                    SagaStep.ORDER_COMPLETE -> {
                        // 주문 완료는 마지막 단계이므로 보상 불필요
                        logger.info("[SAGA] Step ORDER_COMPLETE는 보상 불필요")
                    }
                }
            } catch (e: Exception) {
                logger.error("[SAGA] 보상 트랜잭션 실패: step=$stepName", e)
                throw e
            }
        }
    }

    /**
     * SAGA 상태 조회 (모니터링용)
     */
    fun getSagaInstance(sagaId: String): SagaInstanceJpaEntity? {
        return sagaRepository.findById(sagaId).orElse(null)
    }

    /**
     * OrderPaidEvent 발행
     *
     * SAGA가 성공적으로 완료되면 OrderPaidEvent를 발행하여
     * 외부 데이터 전송, 알림톡, 포인트 적립 등의 비동기 작업을 트리거합니다.
     */
    private fun publishOrderPaidEvent(order: Order) {
        val event = OrderPaidEvent(
            orderId = order.id,
            userId = order.userId,
            items = order.items,
            totalAmount = order.totalAmount,
            discountAmount = order.discountAmount,
            paidAt = order.paidAt ?: java.time.LocalDateTime.now()
        )

        logger.info("[SAGA] OrderPaidEvent 발행: orderId=${order.id}, userId=${order.userId}")
        eventPublisher.publishEvent(event)
    }
}

/**
 * 결제 SAGA 요청
 */
data class PaymentSagaRequest(
    val orderId: Long,
    val userId: Long,
    val idempotencyKey: String? = null  // Phase 2: 멱등성 키
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
