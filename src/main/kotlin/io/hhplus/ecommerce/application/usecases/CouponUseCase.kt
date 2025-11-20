package io.hhplus.ecommerce.application.usecases

import io.hhplus.ecommerce.domain.CouponValidationResult
import io.hhplus.ecommerce.domain.UserCoupon
import io.hhplus.ecommerce.exception.*
import io.hhplus.ecommerce.infrastructure.repositories.CouponRepository
import io.hhplus.ecommerce.infrastructure.repositories.UserRepository
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * 쿠폰 유스케이스
 *
 * Redisson 분산 락을 이용하여 멀티 서버 환경에서도 안전한 동시성 제어를 구현합니다.
 * - 쿠폰 ID별로 분산 락을 적용하여 race condition 방지
 * - 3초 대기 후 락 획득 실패 시 예외 발생
 * - 멀티 서버 환경에서도 정확한 선착순 처리 보장
 */
@Service
class CouponUseCase(
    private val couponRepository: CouponRepository,
    private val userRepository: UserRepository,
    private val redissonClient: RedissonClient
) {
    companion object {
        private const val LOCK_WAIT_TIME = 3L  // 락 획득 대기 시간 (초)
        private const val LOCK_HOLD_TIME = 10L // 락 보유 시간 (초)
    }

    /**
     * 선착순 쿠폰 발급 (분산 락 적용)
     *
     * Redisson 분산 락을 이용하여 멀티 서버 환경에서도 안전한 race condition 방지:
     * - check-then-act 패턴의 원자성을 보장
     * - 쿠폰 ID별로 락을 분리하여 다른 쿠폰 발급에는 영향 없음
     * - 3초 대기 후 락 획득 실패 시 CouponException 발생
     *
     * @param couponId 쿠폰 ID
     * @param userId 사용자 ID
     * @return 쿠폰 발급 결과
     * @throws UserException.UserNotFound 사용자를 찾을 수 없음
     * @throws CouponException.AlreadyIssuedCoupon 이미 발급받은 쿠폰
     * @throws CouponException.CouponNotFound 쿠폰을 찾을 수 없음
     * @throws CouponException.CouponExhausted 쿠폰 재고 부족
     * @throws InterruptedException 락 획득 실패
     */
    fun issueCoupon(couponId: Long, userId: Long): CouponIssueResult {
        val lockKey = "coupon:lock:$couponId"
        val lock = redissonClient.getLock(lockKey)

        return try {
            // 분산 락 획득 시도 (3초 대기, 10초 보유)
            val lockAcquired = lock.tryLock(LOCK_WAIT_TIME, LOCK_HOLD_TIME, TimeUnit.SECONDS)
            if (!lockAcquired) {
                throw CouponException.CouponExhausted()
            }

            // 사용자 확인
            val user = userRepository.findById(userId)
                ?: throw UserException.UserNotFound(userId.toString())

            // 기존 발급 여부 확인
            val existing = couponRepository.findUserCouponByCouponId(userId, couponId)
            if (existing != null) throw CouponException.AlreadyIssuedCoupon()

            // 쿠폰 정보 조회
            val coupon = couponRepository.findById(couponId)
                ?: throw CouponException.CouponNotFound(couponId.toString())

            // 수량 체크 및 발급을 원자적으로 처리 (Race Condition 방지)
            if (!coupon.canIssue()) throw CouponException.CouponExhausted()

            // 쿠폰 발급 (수량 차감)
            val remainingQuantity = coupon.issue()
            couponRepository.save(coupon)

            // 사용자 쿠폰 생성
            val userCoupon = UserCoupon(
                userId = userId,
                couponId = coupon.id,
                couponName = coupon.name,
                discountRate = coupon.discountRate,
                status = "AVAILABLE",
                issuedAt = LocalDateTime.now(),
                usedAt = null,
                expiresAt = LocalDateTime.now().plusDays(7)
            )

            // 저장
            couponRepository.saveUserCoupon(userCoupon)

            // 응답 반환
            CouponIssueResult(
                userCouponId = couponId,
                couponName = userCoupon.couponName,
                discountRate = userCoupon.discountRate,
                expiresAt = userCoupon.expiresAt,
                remainingQuantity = remainingQuantity
            )
        } finally {
            // 락 해제
            if (lock.isHeldByCurrentThread) {
                lock.unlock()
            }
        }
    }

    /**
     * 보유 쿠폰 조회
     */
    fun getUserCoupons(userId: Long): List<UserCoupon> {
        // 사용자 쿠폰 조회
        val coupons = couponRepository.findUserCoupons(userId)

        // 만료된 쿠폰 상태 업데이트
        val now = LocalDateTime.now()
        for (coupon in coupons) {
            if ("AVAILABLE" == coupon.status &&
                coupon.expiresAt != null &&
                now.isAfter(coupon.expiresAt)
            ) {
                // 만료 처리
                coupon.expire()
                couponRepository.saveUserCoupon(coupon)
            }
        }

        return coupons
    }

    /**
     * 쿠폰 검증
     */
    fun validateCoupon(couponCode: String, orderAmount: Long): CouponValidationResult {
        // TODO: Implement proper coupon validation logic
        return CouponValidationResult(
            valid = false,
            coupon = null,
            discount = 0L,
            message = "쿠폰 검증 기능이 아직 구현되지 않았습니다"
        )
    }

    // 내부 응답 객체
    data class CouponIssueResult(
        val userCouponId: Long,
        val couponName: String,
        val discountRate: Int,
        val expiresAt: LocalDateTime,
        val remainingQuantity: Int
    )
}
