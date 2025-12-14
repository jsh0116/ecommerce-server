package io.hhplus.ecommerce.application.services

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * 쿠폰 캐시 관리 컴포넌트
 *
 * Redis 기반 쿠폰 캐시 전략을 담당합니다.
 * Single Responsibility: Redis 키 전략과 캐시 관리만 집중
 *
 * Key 전략:
 * - coupon:issued:{couponId} → Set<userId>  (발급받은 사용자 목록)
 * - coupon:count:{couponId} → Long          (현재 발급 수량)
 * - coupon:quota:{couponId} → Long          (최대 발급 가능 수량)
 */
@Component
class CouponCacheManager(
    private val redisTemplate: RedisTemplate<String, String>
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val ISSUED_SET_PREFIX = "coupon:issued:"
        private const val COUNT_PREFIX = "coupon:count:"
        private const val QUOTA_PREFIX = "coupon:quota:"
        private const val TTL_DAYS = 30L
    }

    // === Key 생성 메서드 ===

    fun getIssuedSetKey(couponId: Long) = "$ISSUED_SET_PREFIX$couponId"
    fun getCountKey(couponId: Long) = "$COUNT_PREFIX$couponId"
    fun getQuotaKey(couponId: Long) = "$QUOTA_PREFIX$couponId"

    // === 조회 메서드 ===

    /**
     * 사용자가 쿠폰을 발급받았는지 확인
     */
    fun hasUserIssued(couponId: Long, userId: Long): Boolean {
        val issuedSetKey = getIssuedSetKey(couponId)
        return redisTemplate.opsForSet().isMember(issuedSetKey, userId.toString()) ?: false
    }

    /**
     * 쿠폰 할당량 조회
     */
    fun getQuota(couponId: Long): Long? {
        val quotaKey = getQuotaKey(couponId)
        return redisTemplate.opsForValue().get(quotaKey)?.toLongOrNull()
    }

    /**
     * 현재 발급 수량 조회
     */
    fun getCurrentCount(couponId: Long): Long {
        val countKey = getCountKey(couponId)
        return redisTemplate.opsForValue().get(countKey)?.toLongOrNull() ?: 0L
    }

    /**
     * 쿠폰 상태 조회 (할당량, 발급 수량, 남은 수량)
     */
    fun getCouponStatus(couponId: Long): CouponStatus {
        val quota = getQuota(couponId) ?: 0L
        val issuedCount = getCurrentCount(couponId)
        val remaining = quota - issuedCount

        return CouponStatus(
            couponId = couponId,
            totalQuantity = quota,
            issuedCount = issuedCount,
            remainingQuantity = remaining
        )
    }

    // === 수정 메서드 ===

    /**
     * 발급 수량 증가 (Atomic)
     *
     * @return 증가 후 수량
     */
    fun incrementCount(couponId: Long): Long {
        val countKey = getCountKey(couponId)
        val newCount = redisTemplate.opsForValue().increment(countKey) ?: 1L
        redisTemplate.expire(countKey, TTL_DAYS, TimeUnit.DAYS)
        return newCount
    }

    /**
     * 발급 수량 감소 (롤백용)
     *
     * @return 감소 후 수량
     */
    fun decrementCount(couponId: Long): Long {
        val countKey = getCountKey(couponId)
        return redisTemplate.opsForValue().decrement(countKey) ?: 0L
    }

    /**
     * 사용자를 발급 Set에 추가 (Atomic)
     *
     * @return 추가 성공 여부 (이미 존재하면 false)
     */
    fun addUserToIssuedSet(couponId: Long, userId: Long): Boolean {
        val issuedSetKey = getIssuedSetKey(couponId)
        val addedCount = redisTemplate.opsForSet().add(issuedSetKey, userId.toString()) ?: 0L
        redisTemplate.expire(issuedSetKey, TTL_DAYS, TimeUnit.DAYS)
        return addedCount > 0L
    }

    // === 초기화 메서드 ===

    /**
     * 쿠폰 캐시 초기화
     *
     * 새로운 쿠폰 생성 시 또는 수량 리셋 시 호출
     */
    fun initializeCoupon(couponId: Long, totalQuantity: Int) {
        val quotaKey = getQuotaKey(couponId)
        val countKey = getCountKey(couponId)
        val issuedSetKey = getIssuedSetKey(couponId)

        try {
            // 쿠폰 총 수량 설정
            redisTemplate.opsForValue().set(quotaKey, totalQuantity.toString())
            redisTemplate.expire(quotaKey, TTL_DAYS, TimeUnit.DAYS)

            // 발급 카운트 초기화
            redisTemplate.opsForValue().set(countKey, "0")
            redisTemplate.expire(countKey, TTL_DAYS, TimeUnit.DAYS)

            // 발급 사용자 Set 초기화 (기존 데이터 삭제)
            redisTemplate.delete(issuedSetKey)

            logger.info("쿠폰 Redis 초기화 완료 - couponId: {}, totalQuantity: {}", couponId, totalQuantity)
        } catch (e: Exception) {
            logger.error("쿠폰 Redis 초기화 실패: couponId={}, error={}", couponId, e.message)
            throw e
        }
    }

    /**
     * 쿠폰 캐시 전체 삭제
     */
    fun invalidateCoupon(couponId: Long) {
        redisTemplate.delete(getQuotaKey(couponId))
        redisTemplate.delete(getCountKey(couponId))
        redisTemplate.delete(getIssuedSetKey(couponId))
        logger.info("쿠폰 캐시 삭제 완료: couponId={}", couponId)
    }

    // === DTO ===

    data class CouponStatus(
        val couponId: Long,
        val totalQuantity: Long,
        val issuedCount: Long,
        val remainingQuantity: Long
    )
}
