package io.hhplus.ecommerce.application.services.impl

import io.hhplus.ecommerce.application.services.AsyncCouponIssuanceService
import io.hhplus.ecommerce.infrastructure.kafka.CouponIssuanceProducer
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service

/**
 * Kafka 기반 쿠폰 발급 요청 서비스
 *
 * Kafka Topic을 통해 쿠폰 발급 요청을 비동기적으로 처리합니다.
 *
 * 장점:
 * - 높은 처리량과 확장성
 * - 파티션 기반 순서 보장 (같은 userId는 같은 파티션)
 * - At-least-once 보장
 * - 영구 저장 (메시지 유실 방지)
 *
 * @param couponIssuanceProducer Kafka Producer (Infrastructure 계층)
 */
@Service
@Primary  // 기본 구현체로 지정 (Queue 방식보다 Kafka 우선)
class KafkaBasedCouponIssuanceService(
    private val couponIssuanceProducer: CouponIssuanceProducer
) : AsyncCouponIssuanceService {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun requestIssuance(couponId: Long, userId: Long): String {
        val requestId = generateRequestId(couponId, userId)

        logger.info(
            "[Kafka-Based Issuance] 쿠폰 발급 요청: requestId={}, couponId={}, userId={}",
            requestId, couponId, userId
        )

        // Kafka Producer에 이벤트 발행 (Infrastructure 계층 위임)
        couponIssuanceProducer.publishIssuanceRequest(userId, couponId, requestId)

        return requestId
    }

    override fun generateRequestId(couponId: Long, userId: Long): String {
        // Producer의 ID 생성 로직 재사용
        return couponIssuanceProducer.generateRequestId(userId, couponId)
    }
}
