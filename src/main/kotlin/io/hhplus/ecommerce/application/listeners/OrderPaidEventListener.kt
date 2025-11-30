package io.hhplus.ecommerce.application.listeners

import io.hhplus.ecommerce.application.events.OrderPaidEvent
import io.hhplus.ecommerce.application.services.DataTransmissionService
import io.hhplus.ecommerce.application.usecases.OrderUseCase
import io.hhplus.ecommerce.infrastructure.repositories.OrderRepository
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

/**
 * 주문 결제 완료 이벤트 리스너
 *
 * OrderPaidEvent 발행 시 외부 시스템으로 데이터를 전송합니다.
 * @Async를 통해 DB 트랜잭션과 분리된 비동기 스레드에서 실행됩니다.
 * 외부 시스템 전송 실패는 재시도 큐에 저장되어 별도로 처리됩니다.
 */
@Component
class OrderPaidEventListener(
    private val dataTransmissionService: DataTransmissionService?,
    private val orderRepository: OrderRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 주문 결제 완료 시 외부 데이터 전송
     *
     * @param event 주문 결제 완료 이벤트
     */
    @EventListener
    @Async
    fun handleOrderPaidEvent(event: OrderPaidEvent) {
        try {
            logger.debug("주문 결제 완료 이벤트 처리 시작: orderId=${event.orderId}")

            dataTransmissionService?.send(
                OrderUseCase.DataPayload(
                    orderId = event.orderId,
                    userId = event.userId,
                    items = event.items,
                    totalAmount = event.totalAmount,
                    discountAmount = event.discountAmount,
                    paidAt = event.paidAt
                )
            )

            logger.info("주문 결제 완료 외부 전송 성공: orderId=${event.orderId}")
        } catch (e: Exception) {
            logger.error("주문 결제 완료 외부 전송 실패, 재시도 큐 저장: orderId=${event.orderId}, error=${e.message}")
            // 재시도 큐에 저장
            try {
                val order = orderRepository.findById(event.orderId)
                if (order != null) {
                    dataTransmissionService?.addToRetryQueue(order)
                    logger.info("재시도 큐에 저장되었습니다: orderId=${event.orderId}")
                } else {
                    logger.error("주문을 찾을 수 없어 재시도 큐 저장 실패: orderId=${event.orderId}")
                }
            } catch (retryError: Exception) {
                logger.error("재시도 큐 저장도 실패했습니다: orderId=${event.orderId}, error=${retryError.message}")
            }
        }
    }
}
