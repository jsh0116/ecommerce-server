package io.hhplus.ecommerce.application.usecases

import io.hhplus.ecommerce.domain.CouponValidationResult
import io.hhplus.ecommerce.domain.UserCoupon
import io.hhplus.ecommerce.exception.*
import io.hhplus.ecommerce.infrastructure.repositories.CouponRepository
import io.hhplus.ecommerce.infrastructure.repositories.UserRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * 쿠폰 유스케이스
 */
@Service
class CouponUseCase(
    private val couponRepository: CouponRepository,
    private val userRepository: UserRepository
) {
    // 쿠폰 ID별 락 관리를 위한 맵
    private val couponLocks = ConcurrentHashMap<Long, Any>()

    /**
     * 선착순 쿠폰 발급 (동시성 제어 적용)
     *
     * Race Condition 방지를 위해 쿠폰 ID별로 synchronized 블록을 사용합니다.
     * - check-then-act 패턴의 원자성을 보장
     * - 쿠폰 ID별로 락을 분리하여 다른 쿠폰 발급에는 영향 없음
     */
    fun issueCoupon(couponId: Long, userId: Long): CouponIssueResult {
        // 쿠폰 ID별 락 객체 획득 (동일한 쿠폰에 대해서만 동기화)
        val lockObject = couponLocks.computeIfAbsent(couponId) { Any() }

        // 동시성 제어: 쿠폰 발급 로직을 원자적으로 처리
        synchronized(lockObject) {
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
            return CouponIssueResult(
                userCouponId = couponId,
                couponName = userCoupon.couponName,
                discountRate = userCoupon.discountRate,
                expiresAt = userCoupon.expiresAt,
                remainingQuantity = remainingQuantity
            )
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
