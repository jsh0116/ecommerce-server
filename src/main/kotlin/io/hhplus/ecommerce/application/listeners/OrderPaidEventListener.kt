package io.hhplus.ecommerce.application.listeners

import io.hhplus.ecommerce.application.events.OrderPaidEvent
import io.hhplus.ecommerce.application.services.DataTransmissionService
import io.hhplus.ecommerce.application.services.TransmissionLogService
import io.hhplus.ecommerce.dto.DataPayload
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 주문 결제 완료 이벤트 리스너
 *
 * OrderPaidEvent 발행 시 외부 시스템으로 데이터를 전송합니다.
 * @TransactionalEventListener(phase = AFTER_COMMIT)을 통해
 * 트랜잭션이 성공적으로 커밋된 이후에만 이벤트가 처리됩니다.
 * @Async를 통해 DB 트랜잭션과 분리된 비동기 스레드에서 실행됩니다.
 * 외부 시스템 전송 실패는 재시도 큐에 저장되어 별도로 처리됩니다.
 */
@Component
class OrderPaidEventListener(
    private val dataTransmissionService: DataTransmissionService?,
    private val transmissionLogService: TransmissionLogService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 주문 결제 완료 시 외부 데이터 전송하는 리스너
     *
     * 트랜잭션 커밋 이후(AFTER_COMMIT)에 실행되어
     * 외부 API 실패가 주문 트랜잭션을 롤백시키지 않습니다.
     *
     * @param event 주문 결제 완료 이벤트
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    fun handleDataTransmission(event: OrderPaidEvent) {
        // 1. 전송 로그 생성 (PENDING 상태)
        val log = transmissionLogService.createPendingLog(event.orderId, event.userId)

        try {
            logger.debug("주문 결제 완료 이벤트 처리 시작: orderId=${event.orderId}")

            // 2. 외부 데이터 전송
            dataTransmissionService?.send(
                DataPayload(
                    orderId = event.orderId,
                    userId = event.userId,
                    items = event.items,
                    totalAmount = event.totalAmount,
                    discountAmount = event.discountAmount,
                    paidAt = event.paidAt
                )
            )

            // 3. 성공 시 로그 업데이트
            transmissionLogService.markAsSuccess(log)
            logger.info("주문 결제 완료 외부 전송 성공: orderId=${event.orderId}")
        } catch (e: Exception) {
            logger.error("주문 결제 완료 외부 전송 실패, 재시도 큐 저장: orderId=${event.orderId}, error=${e.message}")

            // 4. 실패 시 로그 업데이트
            transmissionLogService.markAsFailed(log, e.message ?: "알 수 없는 오류")

            // 5. 재시도 큐에 저장
            try {
                dataTransmissionService?.addToRetryQueue(event.order)
                transmissionLogService.markAsRetrying(log)
                logger.info("재시도 큐에 저장되었습니다: orderId=${event.orderId}")
            } catch (retryError: Exception) {
                logger.error("재시도 큐 저장도 실패했습니다: orderId=${event.orderId}, error=${retryError.message}")
            }
        }
    }

    /**
     * 리스너 2: 주문 완료 알림톡 발송
     *
     * 주문이 성공적으로 완료되면 고객에게 알림톡을 발송합니다.
     * 알림 실패가 주문 트랜잭션에 영향을 주지 않도록 분리되어 있습니다.
     *
     * @param event 주문 결제 완료 이벤트
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    fun handleOrderNotification(event: OrderPaidEvent) {
        try {
            logger.debug("주문 완료 알림톡 발송 시작: orderId=${event.orderId}, userId=${event.userId}")

            // TODO: 실제 알림톡 서비스 연동
            // notificationService.sendOrderCompletedNotification(
            //     userId = event.userId,
            //     orderId = event.orderId,
            //     totalAmount = event.totalAmount,
            //     itemCount = event.items.size
            // )

            logger.info("[알림톡] 주문 완료 알림 발송 성공: orderId=${event.orderId}, userId=${event.userId}")
        } catch (e: Exception) {
            // 알림 실패는 로그만 남기고 재시도하지 않음 (중요도가 낮은 부가 기능)
            logger.warn("[알림톡] 주문 완료 알림 발송 실패: orderId=${event.orderId}, error=${e.message}")
        }
    }

    /**
     * 리스너 3: 구매 포인트 적립
     *
     * 주문 금액의 일정 비율을 포인트로 적립합니다.
     * 포인트 적립 실패가 주문 트랜잭션에 영향을 주지 않습니다.
     *
     * @param event 주문 결제 완료 이벤트
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    fun handlePointReward(event: OrderPaidEvent) {
        try {
            logger.debug("구매 포인트 적립 시작: orderId=${event.orderId}, userId=${event.userId}")

            // 포인트 적립률: 결제 금액의 1% (할인 전 금액 기준)
            val rewardPoints = (event.totalAmount * 0.01).toLong()

            if (rewardPoints > 0) {
                // TODO: 실제 포인트 적립 서비스 연동
                // userService.addPoints(
                //     userId = event.userId,
                //     points = rewardPoints,
                //     reason = "주문 완료 적립 (orderId: ${event.orderId})"
                // )

                logger.info("[포인트] 구매 포인트 적립 성공: orderId=${event.orderId}, userId=${event.userId}, points=${rewardPoints}")
            } else {
                logger.debug("[포인트] 적립할 포인트 없음: orderId=${event.orderId}")
            }
        } catch (e: Exception) {
            logger.error("[포인트] 구매 포인트 적립 실패: orderId=${event.orderId}, error=${e.message}", e)
            // 포인트 적립 실패 시 별도 보상 로직 또는 수동 처리 필요
            // TODO: 포인트 적립 실패 알림 또는 재시도 큐 추가
        }
    }
}
