package io.hhplus.ecommerce.infrastructure.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import io.hhplus.ecommerce.infrastructure.kafka.events.CouponIssuanceRequestedEvent
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 쿠폰 발급 요청 Kafka Producer
 *
 * 쿠폰 발급 요청을 Kafka Topic에 발행합니다.
 * userId를 Message Key로 사용하여 같은 유저의 요청은 같은 파티션으로 전송됩니다.
 */
@Component
class CouponIssuanceProducer(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        const val TOPIC = "coupon.issuance.requested"
    }

    /**
     * 쿠폰 발급 요청 이벤트 발행
     *
     * @param userId 사용자 ID (Message Key로 사용 - 파티션 결정)
     * @param couponId 쿠폰 ID
     * @param requestId 멱등성 키
     */
    fun publishIssuanceRequest(userId: Long, couponId: Long, requestId: String) {
        val event = CouponIssuanceRequestedEvent(
            requestId = requestId,
            userId = userId,
            couponId = couponId,
            requestedAt = LocalDateTime.now()
        )

        val payload = objectMapper.writeValueAsString(event)

        // userId를 Key로 사용 → 같은 유저의 요청은 같은 파티션으로
        kafkaTemplate.send(TOPIC, userId.toString(), payload)
            .whenComplete { result, ex ->
                if (ex == null) {
                    val meta = result?.recordMetadata
                    logger.info(
                        "[Kafka Producer] 쿠폰 발급 요청 발행 성공: " +
                                "requestId=$requestId, userId=$userId, couponId=$couponId, " +
                                "partition=${meta?.partition()}, offset=${meta?.offset()}"
                    )
                } else {
                    logger.error(
                        "[Kafka Producer] 쿠폰 발급 요청 발행 실패: " +
                                "requestId=$requestId, userId=$userId, couponId=$couponId",
                        ex
                    )
                }
            }
    }

    /**
     * requestId 생성
     */
    fun generateRequestId(userId: Long, couponId: Long): String {
        return "req-$userId-$couponId-${System.currentTimeMillis()}"
    }
}
