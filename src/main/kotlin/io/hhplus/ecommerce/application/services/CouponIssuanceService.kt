package io.hhplus.ecommerce.application.services

import io.hhplus.ecommerce.exception.BusinessRuleViolationException
import io.hhplus.ecommerce.exception.CouponException
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

/**
 * Redis 기반 쿠폰 발급 서비스 (STEP 14)
 *
 * 선착순 쿠폰 발급을 위한 Redis 자료구조 활용:
 * 1. Set: 중복 발급 방지 (userId 저장)
 * 2. String (INCR): 발급 수량 관리 (atomic operations)
 *
 * Key 전략:
 * - coupon:issued:{couponId} → Set<userId>  // 발급받은 사용자 목록
 * - coupon:count:{couponId} → Long          // 현재 발급 수량
 * - coupon:quota:{couponId} → Long          // 최대 발급 가능 수량
 */
@Service
class CouponIssuanceService(
    private val redisTemplate: RedisTemplate<String, String>
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val ISSUED_SET_PREFIX = "coupon:issued:"
        private const val COUNT_PREFIX = "coupon:count:"
        private const val QUOTA_PREFIX = "coupon:quota:"
        private const val TTL_DAYS = 30L
    }

    /**
     * 쿠폰 발급 가능 여부 체크 (Redis 기반)
     *
     * @param couponId 쿠폰 ID
     * @param userId 사용자 ID
     * @return 발급 가능 여부
     * @throws CouponException.AlreadyIssuedCoupon 이미 발급받은 경우
     * @throws CouponException.CouponExhausted 수량 소진된 경우
     */
    fun checkIssuanceEligibility(couponId: Long, userId: Long) {
        val issuedSetKey = getIssuedSetKey(couponId)

        // 1. 중복 발급 체크 (Redis Set)
        val alreadyIssued = redisTemplate.opsForSet().isMember(issuedSetKey, userId.toString())
        if (alreadyIssued == true) {
            throw CouponException.AlreadyIssuedCoupon()
        }

        // 2. 수량 체크 (Redis String)
        val quotaKey = getQuotaKey(couponId)
        val countKey = getCountKey(couponId)

        val quota = redisTemplate.opsForValue().get(quotaKey)?.toLongOrNull()
            ?: throw CouponException.CouponNotFound(couponId.toString())

        val currentCount = redisTemplate.opsForValue().get(countKey)?.toLongOrNull() ?: 0L

        if (currentCount >= quota) {
            throw CouponException.CouponExhausted()
        }
    }

    /**
     * Redis에 쿠폰 발급 기록 (atomic)
     *
     * INCR의 원자성을 활용하여 정확한 수량 제어를 보장합니다.
     * 1. INCR로 카운트 증가 (원자적)
     * 2. quota 초과 시 롤백 후 예외
     * 3. 성공 시 Set에 userId 추가
     *
     * @param couponId 쿠폰 ID
     * @param userId 사용자 ID
     * @return 발급 후 남은 수량
     */
    fun recordIssuance(couponId: Long, userId: Long): Long {
        val issuedSetKey = getIssuedSetKey(couponId)
        val countKey = getCountKey(couponId)
        val quotaKey = getQuotaKey(couponId)

        try {
            // 1. 발급 수량 증가 (atomic - 가장 먼저 실행하여 동시성 제어)
            val newCount = redisTemplate.opsForValue().increment(countKey) ?: 1L
            redisTemplate.expire(countKey, TTL_DAYS, TimeUnit.DAYS)

            // 2. quota 체크 - 초과 시 롤백
            val quota = redisTemplate.opsForValue().get(quotaKey)?.toLongOrNull() ?: 0L
            if (newCount > quota) {
                // 롤백: 카운트 감소
                redisTemplate.opsForValue().decrement(countKey)
                throw CouponException.CouponExhausted()
            }

            // 3. Set에 userId 추가 (발급 기록) - add는 원자적으로 중복 체크 및 추가
            // add 반환값: 추가된 개수 (이미 존재하면 0, 성공하면 1)
            val addedCount = redisTemplate.opsForSet().add(issuedSetKey, userId.toString()) ?: 0L

            if (addedCount == 0L) {
                // 이미 발급받은 사용자 (Race condition으로 인한 중복 발급 시도)
                // 카운트 롤백
                redisTemplate.opsForValue().decrement(countKey)
                throw CouponException.AlreadyIssuedCoupon()
            }

            redisTemplate.expire(issuedSetKey, TTL_DAYS, TimeUnit.DAYS)

            val remaining = quota - newCount

            logger.info(
                "쿠폰 발급 기록 완료 - couponId: {}, userId: {}, 발급수: {}, 남은수량: {}",
                couponId, userId, newCount, remaining
            )

            return remaining
        } catch (e: BusinessRuleViolationException) {
            logger.warn("쿠폰 발급 실패: couponId={}, userId={}, reason={}", couponId, userId, e.message)
            throw e
        } catch (e: Exception) {
            logger.error("쿠폰 발급 Redis 기록 실패: couponId={}, userId={}, error={}", couponId, userId, e.message)
            throw e
        }
    }

    /**
     * 쿠폰 발급 정보 초기화
     *
     * 새로운 쿠폰이 생성되거나 수량이 초기화될 때 호출합니다.
     *
     * @param couponId 쿠폰 ID
     * @param totalQuantity 총 발급 가능 수량
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
     * 쿠폰 현재 발급 상태 조회
     *
     * @param couponId 쿠폰 ID
     * @return 발급 상태 정보
     */
    fun getCouponStatus(couponId: Long): CouponStatus {
        val quotaKey = getQuotaKey(couponId)
        val countKey = getCountKey(couponId)

        val quota = redisTemplate.opsForValue().get(quotaKey)?.toLongOrNull() ?: 0L
        val issuedCount = redisTemplate.opsForValue().get(countKey)?.toLongOrNull() ?: 0L
        val remaining = quota - issuedCount

        return CouponStatus(
            couponId = couponId,
            totalQuantity = quota,
            issuedCount = issuedCount,
            remainingQuantity = remaining
        )
    }

    /**
     * 특정 사용자가 쿠폰을 발급받았는지 확인
     *
     * @param couponId 쿠폰 ID
     * @param userId 사용자 ID
     * @return 발급 여부
     */
    fun hasUserIssuedCoupon(couponId: Long, userId: Long): Boolean {
        val issuedSetKey = getIssuedSetKey(couponId)
        return redisTemplate.opsForSet().isMember(issuedSetKey, userId.toString()) ?: false
    }

    // === Private Helper Methods ===

    private fun getIssuedSetKey(couponId: Long) = "$ISSUED_SET_PREFIX$couponId"
    private fun getCountKey(couponId: Long) = "$COUNT_PREFIX$couponId"
    private fun getQuotaKey(couponId: Long) = "$QUOTA_PREFIX$couponId"

    data class CouponStatus(
        val couponId: Long,
        val totalQuantity: Long,
        val issuedCount: Long,
        val remainingQuantity: Long
    )
}
