package io.hhplus.ecommerce.application.services.impl

import io.hhplus.ecommerce.application.services.DataPlatformService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 데이터 플랫폼 전송 서비스 구현체
 *
 * 실제 운영 환경에서는 외부 API 호출, 메시지 큐 발행 등의 로직이 구현됩니다.
 * 현재는 로깅으로 동작을 시뮬레이션합니다.
 *
 * 실제 구현 예시:
 * - REST API 호출: RestTemplate, WebClient
 * - 메시지 발행: Kafka, RabbitMQ
 * - 배치 처리: Spring Batch
 */
@Service
class DataPlatformServiceImpl : DataPlatformService {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun sendOrderDataToWarehouse(orderId: Long, userId: Long, finalAmount: Long, paidAt: String) {
        try {
            // 실제 구현: Data Warehouse API 호출
            // Example: dataWarehouseClient.post("/orders", OrderDataDto(...))

            logger.info(
                """
                [Data Platform] 주문 데이터 전송 (Data Warehouse):
                  - orderId: $orderId
                  - userId: $userId
                  - finalAmount: $finalAmount
                  - paidAt: $paidAt
                  - status: SUCCESS
                """.trimIndent()
            )
        } catch (e: Exception) {
            // 데이터 플랫폼 전송 실패가 핵심 비즈니스(결제)에 영향을 주지 않음
            logger.error("[Data Platform] 데이터 웨어하우스 전송 실패: orderId=$orderId", e)
        }
    }

    override fun sendPaymentAnalytics(orderId: Long, userId: Long, finalAmount: Long) {
        try {
            // 실제 구현: Analytics API 호출 (Google Analytics, Mixpanel, Amplitude 등)
            // Example: analyticsClient.track("payment_completed", mapOf(...))

            logger.info(
                """
                [Data Platform] 결제 분석 데이터 전송 (Analytics):
                  - orderId: $orderId
                  - userId: $userId
                  - finalAmount: $finalAmount
                  - event: payment_completed
                  - status: SUCCESS
                """.trimIndent()
            )
        } catch (e: Exception) {
            logger.error("[Data Platform] 분석 시스템 전송 실패: orderId=$orderId", e)
        }
    }

    override fun notifyErpSystem(orderId: Long) {
        try {
            // 실제 구현: ERP 시스템 API 호출
            // Example: erpClient.post("/orders/notify", mapOf("orderId" to orderId))

            logger.info(
                """
                [Data Platform] ERP 시스템 알림:
                  - orderId: $orderId
                  - action: order_paid_notification
                  - status: SUCCESS
                """.trimIndent()
            )
        } catch (e: Exception) {
            logger.error("[Data Platform] ERP 시스템 알림 실패: orderId=$orderId", e)
        }
    }
}
