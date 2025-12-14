package io.hhplus.ecommerce.application.services

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Redis 기반 쿠폰 발급 서비스 (STEP 14)
 *
 * Validator, Executor, Publisher, CacheManager 패턴을 활용하여
 * Redis 기반 선착순 쿠폰 발급 로직의 책임을 명확히 분리합니다.
 *
 * 선착순 쿠폰 발급 프로세스:
 * 1. 중복 발급 및 수량 검증 → CouponValidator
 * 2. Redis INCR 기반 발급 실행 → CouponExecutor
 * 3. 이벤트 발행 (미래) → CouponEventPublisher
 * 4. Redis 키 전략 관리 → CouponCacheManager ⭐
 */
@Service
class CouponIssuanceService(
    private val couponValidator: CouponValidator,
    private val couponExecutor: CouponExecutor,
    private val couponEventPublisher: CouponEventPublisher,
    private val couponCacheManager: CouponCacheManager
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 쿠폰 발급 가능 여부 체크
     *
     * Validator를 활용한 깔끔한 검증
     *
     * @param couponId 쿠폰 ID
     * @param userId 사용자 ID
     * @throws CouponException.AlreadyIssuedCoupon 이미 발급받은 경우
     * @throws CouponException.CouponExhausted 수량 소진된 경우
     */
    fun checkIssuanceEligibility(couponId: Long, userId: Long) {
        couponValidator.validateIssuanceEligibility(couponId, userId)
    }

    /**
     * Redis에 쿠폰 발급 기록 (atomic)
     *
     * Executor를 활용한 원자적 발급 실행
     *
     * @param couponId 쿠폰 ID
     * @param userId 사용자 ID
     * @return 발급 후 남은 수량
     */
    fun recordIssuance(couponId: Long, userId: Long): Long {
        // 1. 발급 실행 (CouponExecutor)
        val remaining = couponExecutor.executeIssuance(couponId, userId)

        // 2. 이벤트 발행 (CouponEventPublisher - 미래 확장용)
        // couponEventPublisher.publishCouponIssuedEvent(couponId, userId, remaining)

        // 3. 수량 소진 시 이벤트 발행 (옵션)
        if (remaining == 0L) {
            // couponEventPublisher.publishCouponExhaustedEvent(couponId)
        }

        return remaining
    }

    /**
     * 쿠폰 발급 정보 초기화
     *
     * CacheManager에 위임
     *
     * @param couponId 쿠폰 ID
     * @param totalQuantity 총 발급 가능 수량
     */
    fun initializeCoupon(couponId: Long, totalQuantity: Int) {
        couponCacheManager.initializeCoupon(couponId, totalQuantity)
    }

    /**
     * 쿠폰 현재 발급 상태 조회
     *
     * CacheManager에 위임
     *
     * @param couponId 쿠폰 ID
     * @return 발급 상태 정보
     */
    fun getCouponStatus(couponId: Long): CouponCacheManager.CouponStatus {
        return couponCacheManager.getCouponStatus(couponId)
    }

    /**
     * 특정 사용자가 쿠폰을 발급받았는지 확인
     *
     * CacheManager에 위임
     *
     * @param couponId 쿠폰 ID
     * @param userId 사용자 ID
     * @return 발급 여부
     */
    fun hasUserIssuedCoupon(couponId: Long, userId: Long): Boolean {
        return couponCacheManager.hasUserIssued(couponId, userId)
    }
}
