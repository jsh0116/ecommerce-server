package io.hhplus.ecommerce.infrastructure.lock

import java.util.concurrent.TimeUnit

/**
 * 분산 환경에서 동시성 제어를 위한 분산 락 서비스
 *
 * 결제, 쿠폰, 예약 등 다양한 도메인에서 공통으로 사용할 수 있는
 * 추상화된 락 인터페이스입니다.
 *
 * 구현체:
 * - Redis 기반: RedissonDistributedLockService
 * - 테스트: Mock 구현 (TestRedissonConfig)
 *
 * 사용 예시:
 * ```kotlin
 * val lockAcquired = distributedLockService.tryLock(
 *     key = "coupon:${couponId}",
 *     waitTime = 3L,
 *     holdTime = 10L,
 *     unit = TimeUnit.SECONDS
 * )
 * if (!lockAcquired) {
 *     throw LockException("락 획득 실패")
 * }
 * try {
 *     // 비즈니스 로직
 * } finally {
 *     distributedLockService.unlock("coupon:${couponId}")
 * }
 * ```
 */
interface DistributedLockService {
    /**
     * 분산 락을 시도합니다.
     *
     * @param key 락의 고유 키 (예: "coupon:123", "payment:abc-def")
     * @param waitTime 락 획득 대기 시간
     * @param holdTime 락 보유 시간
     * @param unit 시간 단위
     * @return 락 획득 성공 여부
     */
    fun tryLock(key: String, waitTime: Long, holdTime: Long, unit: TimeUnit): Boolean

    /**
     * 락을 해제합니다.
     *
     * @param key 락의 고유 키
     */
    fun unlock(key: String)

    /**
     * 현재 스레드가 해당 락을 보유하고 있는지 확인합니다.
     *
     * @param key 락의 고유 키
     * @return 락 보유 여부
     */
    fun isHeldByCurrentThread(key: String): Boolean
}