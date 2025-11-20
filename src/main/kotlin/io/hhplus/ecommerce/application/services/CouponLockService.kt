package io.hhplus.ecommerce.application.services

import java.util.concurrent.TimeUnit

/**
 * 쿠폰 발급 시 동시성 제어를 위한 분산 락 인터페이스
 *
 * Redis 기반 분산 락을 추상화하여, 테스트에서는 Mock으로 처리하고
 * 프로덕션에서는 Redisson을 사용할 수 있도록 합니다.
 */
interface CouponLockService {
    /**
     * 쿠폰 발급 락을 시도합니다.
     *
     * @param couponId 쿠폰 ID
     * @param waitTime 락 획득 대기 시간
     * @param holdTime 락 보유 시간
     * @param unit 시간 단위
     * @return 락 획득 성공 여부
     */
    fun tryLock(couponId: Long, waitTime: Long, holdTime: Long, unit: TimeUnit): Boolean

    /**
     * 락을 해제합니다.
     *
     * @param couponId 쿠폰 ID
     */
    fun unlock(couponId: Long)

    /**
     * 현재 스레드가 해당 쿠폰의 락을 보유하고 있는지 확인합니다.
     *
     * @param couponId 쿠폰 ID
     * @return 락 보유 여부
     */
    fun isHeldByCurrentThread(couponId: Long): Boolean
}
