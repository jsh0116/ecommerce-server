package io.hhplus.ecommerce.infrastructure.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import io.hhplus.ecommerce.application.services.DataPlatformService
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component
import java.util.concurrent.CopyOnWriteArrayList

/**
 * ORDER_PAID 이벤트 Kafka Consumer
 *
 * Debezium CDC를 통해 발행된 ORDER_PAID 이벤트를 수신하여 처리합니다.
 *
 * 주요 역할:
 * 1. outbox.event.ORDER 토픽 구독
 * 2. ORDER_PAID 이벤트 파싱
 * 3. 후속 처리 (데이터 웨어하우스 전송, 알림 발송 등)
 * 4. 수동 ACK로 at-least-once 보장
 *
 * CDC 흐름:
 * 1. PaymentSagaOrchestrator → outbox_events 테이블 INSERT
 * 2. Debezium → MySQL Binlog 감지
 * 3. Debezium → Kafka 발행 (outbox.event.ORDER)
 * 4. OrderPaidEventConsumer → 이벤트 수신 및 처리
 */
@Component
class OrderPaidEventConsumer(
    private val objectMapper: ObjectMapper,
    private val dataPlatformService: DataPlatformService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    // 테스트용: 처리된 이벤트 저장 (실제 운영 환경에서는 제거 필요)
    private val processedEvents = CopyOnWriteArrayList<String>()

    /**
     * ORDER_PAID 이벤트 수신 및 처리
     *
     * @param payload Kafka 메시지 본문 (JSON)
     * @param key Kafka 메시지 키 (userId - 파티션 순서 보장)
     * @param partition 파티션 번호
     * @param offset 오프셋
     * @param acknowledgment 수동 ACK 객체
     */
    @KafkaListener(
        topics = ["outbox.event.ORDER"],
        groupId = "ecommerce-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun consumeOrderPaidEvent(
        @Payload payload: String,
        @Header(KafkaHeaders.RECEIVED_KEY) key: String?,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
        acknowledgment: Acknowledgment?
    ) {
        logger.info("[Kafka Consumer] ORDER 이벤트 수신: key=$key, partition=$partition, offset=$offset")

        try {
            // 1. JSON 파싱
            val event = parseEvent(payload)

            // 2. 이벤트 타입별 처리
            when (event["eventType"] ?: "ORDER_PAID") {
                "ORDER_PAID" -> handleOrderPaidEvent(event)
                else -> logger.warn("[Kafka Consumer] 알 수 없는 이벤트 타입: ${event["eventType"]}")
            }

            // 3. 테스트용 저장
            processedEvents.add(payload)

            // 4. 수동 ACK (at-least-once 보장)
            acknowledgment?.acknowledge()
            logger.info("[Kafka Consumer] ORDER 이벤트 처리 완료: orderId=${event["orderId"]}, offset=$offset")

        } catch (e: Exception) {
            logger.error("[Kafka Consumer] ORDER 이벤트 처리 실패: offset=$offset, payload=$payload", e)
            // 실패 시에도 ACK 처리 (DLQ로 전송하거나 재처리 로직 필요)
            acknowledgment?.acknowledge()
        }
    }

    /**
     * ORDER_PAID 이벤트 처리
     *
     * 실제 비즈니스 로직:
     * 1. 데이터 웨어하우스로 주문 데이터 전송
     * 2. 외부 시스템 (ERP, 배송 시스템)에 주문 정보 전달
     * 3. 분석 시스템으로 결제 데이터 전송
     */
    private fun handleOrderPaidEvent(event: Map<String, Any>) {
        val orderId = (event["orderId"] as? Number)?.toLong() ?: 0L
        val userId = (event["userId"] as? Number)?.toLong() ?: 0L
        val finalAmount = (event["finalAmount"] as? Number)?.toLong() ?: 0L
        val paidAt = event["paidAt"]?.toString() ?: ""

        logger.info(
            """
            [Kafka Consumer] ORDER_PAID 이벤트 처리:
              - orderId: $orderId
              - userId: $userId
              - finalAmount: $finalAmount
              - paidAt: $paidAt
            """.trimIndent()
        )

        try {
            // 1. 데이터 웨어하우스로 주문 데이터 전송
            dataPlatformService.sendOrderDataToWarehouse(orderId, userId, finalAmount, paidAt)

            // 2. ERP 시스템에 주문 완료 알림
            dataPlatformService.notifyErpSystem(orderId)

            // 3. 분석 시스템으로 결제 데이터 전송
            dataPlatformService.sendPaymentAnalytics(orderId, userId, finalAmount)

            logger.info("[Kafka Consumer] 데이터 플랫폼 전송 완료: orderId=$orderId")
        } catch (e: Exception) {
            // 데이터 플랫폼 전송 실패가 이벤트 처리 전체를 막지 않음
            logger.error("[Kafka Consumer] 데이터 플랫폼 전송 중 오류 발생: orderId=$orderId", e)
        }
    }

    /**
     * JSON 파싱
     */
    private fun parseEvent(payload: String): Map<String, Any> {
        return try {
            objectMapper.readValue(payload, Map::class.java) as Map<String, Any>
        } catch (e: Exception) {
            logger.error("[Kafka Consumer] JSON 파싱 실패: payload=$payload", e)
            throw e
        }
    }

    /**
     * 테스트용: 처리된 이벤트 목록 조회
     */
    fun getProcessedEvents(): List<String> = processedEvents.toList()

    /**
     * 테스트용: 처리된 이벤트 목록 초기화
     */
    fun clearProcessedEvents() {
        processedEvents.clear()
    }
}
