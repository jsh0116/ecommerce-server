package io.hhplus.ecommerce.infrastructure.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import io.hhplus.ecommerce.application.usecases.CouponUseCase
import io.hhplus.ecommerce.application.services.IdempotencyResult
import io.hhplus.ecommerce.application.services.IdempotencyService
import io.hhplus.ecommerce.exception.CouponException
import io.hhplus.ecommerce.infrastructure.kafka.events.CouponIssuanceFailedEvent
import io.hhplus.ecommerce.infrastructure.kafka.events.CouponIssuanceRequestedEvent
import io.hhplus.ecommerce.infrastructure.kafka.events.CouponIssuedEvent
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.Acknowledgment
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 쿠폰 발급 요청 Kafka Consumer
 *
 * coupon.issuance.requested 토픽을 구독하여 쿠폰 발급을 처리합니다.
 *
 * 중요:
 * - Kafka 파티션 내에서 순차 처리되므로 락이 필요 없음
 * - 같은 userId의 요청은 같은 파티션 → 순서 보장
 * - 멱등성 검증으로 중복 발급 방지
 */
@Component
class CouponIssuanceConsumer(
    private val couponUseCase: CouponUseCase,
    private val idempotencyService: IdempotencyService,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        const val TOPIC_REQUESTED = "coupon.issuance.requested"
        const val TOPIC_ISSUED = "coupon.issued"
        const val TOPIC_FAILED = "coupon.issuance.failed"
    }

    @KafkaListener(
        topics = [TOPIC_REQUESTED],
        groupId = "coupon-issuance-service",
        concurrency = "3" // 파티션 3개 → Consumer instance 3개
    )
    @Transactional
    fun consumeIssuanceRequest(
        @Payload payload: String,
        @Header(KafkaHeaders.RECEIVED_KEY) key: String,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
        acknowledgment: Acknowledgment?
    ) {
        logger.info("[Kafka Consumer] 쿠폰 발급 요청 수신: key=$key, partition=$partition, offset=$offset")

        try {
            val event = objectMapper.readValue(payload, CouponIssuanceRequestedEvent::class.java)

            // 1. 멱등성 검증 (중복 요청 방지)
            when (val result = idempotencyService.acquireKey(
                idempotencyKey = event.requestId,
                requestType = "coupon-issuance",
                userId = event.userId,
                entityId = event.couponId
            )) {
                is IdempotencyResult.AlreadyCompleted -> {
                    logger.info("[Idempotency] 이미 처리된 요청: requestId=${event.requestId}")
                    acknowledgment?.acknowledge()
                    return
                }
                is IdempotencyResult.Processing -> {
                    logger.warn("[Idempotency] 처리 중인 요청: requestId=${event.requestId}")
                    return // NACK - 재처리
                }
                is IdempotencyResult.Failed -> {
                    logger.error("[Idempotency] 실패한 요청: requestId=${event.requestId}, message=${result.message}")
                    acknowledgment?.acknowledge()
                    return
                }
                is IdempotencyResult.NewRequest -> {
                    logger.info("[Idempotency] 새로운 요청 처리: requestId=${event.requestId}")
                }
            }

            // 2. 쿠폰 발급 (트랜잭션 내 - UseCase 위임)
            couponUseCase.issueCouponDirectly(event.couponId, event.userId)

            // 3. 성공 이벤트 발행
            publishIssuedEvent(event)

            // 4. 멱등성 키 완료 처리
            idempotencyService.markAsCompleted(
                idempotencyKey = event.requestId,
                responseData = objectMapper.writeValueAsString(mapOf(
                    "userId" to event.userId,
                    "couponId" to event.couponId
                ))
            )

            // 5. ACK
            acknowledgment?.acknowledge()
            logger.info("[Kafka Consumer] 쿠폰 발급 완료: requestId=${event.requestId}, userId=${event.userId}, couponId=${event.couponId}")

        } catch (e: CouponException.CouponExhausted) {
            logger.warn("[Kafka Consumer] 쿠폰 소진: $payload", e)
            publishFailedEvent(payload, "COUPON_EXHAUSTED", e.message ?: "쿠폰이 모두 소진되었습니다")
            acknowledgment?.acknowledge()

        } catch (e: Exception) {
            logger.error("[Kafka Consumer] 쿠폰 발급 실패: $payload", e)
            publishFailedEvent(payload, "INTERNAL_ERROR", e.message ?: "알 수 없는 오류")
            acknowledgment?.acknowledge()
        }
    }

    /**
     * 발급 성공 이벤트 발행
     */
    private fun publishIssuedEvent(request: CouponIssuanceRequestedEvent) {
        val event = CouponIssuedEvent(
            requestId = request.requestId,
            userId = request.userId,
            couponId = request.couponId,
            userCouponId = null,  // Domain model doesn't expose ID
            issuedAt = LocalDateTime.now()
        )

        val payload = objectMapper.writeValueAsString(event)
        kafkaTemplate.send(TOPIC_ISSUED, request.userId.toString(), payload)
        logger.info("[Kafka Producer] 쿠폰 발급 완료 이벤트 발행: requestId=${request.requestId}")
    }

    /**
     * 발급 실패 이벤트 발행
     */
    private fun publishFailedEvent(originalPayload: String, errorCode: String, errorMessage: String) {
        try {
            val originalEvent = objectMapper.readValue(originalPayload, CouponIssuanceRequestedEvent::class.java)
            val event = CouponIssuanceFailedEvent(
                requestId = originalEvent.requestId,
                userId = originalEvent.userId,
                couponId = originalEvent.couponId,
                errorCode = errorCode,
                errorMessage = errorMessage,
                failedAt = LocalDateTime.now()
            )

            val payload = objectMapper.writeValueAsString(event)
            kafkaTemplate.send(TOPIC_FAILED, "error", payload)
            logger.info("[Kafka Producer] 쿠폰 발급 실패 이벤트 발행: requestId=${originalEvent.requestId}, errorCode=$errorCode")
        } catch (e: Exception) {
            logger.error("[Kafka Producer] 실패 이벤트 발행 실패: $originalPayload", e)
        }
    }
}