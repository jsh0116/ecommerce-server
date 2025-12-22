package io.hhplus.ecommerce.application.services

/**
 * 비동기 쿠폰 발급 요청 서비스 인터페이스
 *
 * 쿠폰 발급 요청을 비동기적으로 처리하기 위한 추상화 계층입니다.
 * 구현체를 교체하여 다양한 메시징 시스템(Kafka, RabbitMQ, SQS 등)을 사용할 수 있습니다.
 *
 * 구현체:
 * - KafkaBasedCouponIssuanceService: Kafka Topic을 사용한 비동기 처리
 * - QueueBasedCouponIssuanceService: 인메모리 Queue를 사용한 비동기 처리
 */
interface AsyncCouponIssuanceService {
    /**
     * 쿠폰 발급 요청을 비동기적으로 처리합니다.
     *
     * @param couponId 쿠폰 ID
     * @param userId 사용자 ID
     * @return 발급 요청 ID (멱등성 키)
     */
    fun requestIssuance(couponId: Long, userId: Long): String

    /**
     * 요청 ID를 생성합니다.
     *
     * @param couponId 쿠폰 ID
     * @param userId 사용자 ID
     * @return 생성된 요청 ID
     */
    fun generateRequestId(couponId: Long, userId: Long): String
}
