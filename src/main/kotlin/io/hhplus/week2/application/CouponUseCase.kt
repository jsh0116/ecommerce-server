package io.hhplus.week2.application

import io.hhplus.week2.domain.Coupon
import io.hhplus.week2.domain.UserCoupon
import io.hhplus.week2.repository.CouponRepository
import io.hhplus.week2.repository.UserRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 쿠폰 유스케이스
 */
@Service
class CouponUseCase(
    private val couponRepository: CouponRepository,
    private val userRepository: UserRepository
) {
    /**
     * 선착순 쿠폰 발급
     */
    fun issueCoupon(couponId: String, userId: String): CouponIssueResult {
        // 사용자 확인
        val user = userRepository.findById(userId)
            ?: throw IllegalStateException("사용자를 찾을 수 없습니다")

        // 기존 발급 여부 확인
        val existing = couponRepository.findUserCouponByCouponId(userId, couponId)
        if (existing != null) throw IllegalStateException("이미 발급받은 쿠폰입니다")

        // 쿠폰 정보 조회
        val coupon = couponRepository.findById(couponId)
            ?: throw IllegalStateException("쿠폰을 찾을 수 없습니다")

        // 수량 체크
        if (!coupon.canIssue()) throw IllegalStateException("쿠폰이 모두 소진되었습니다")

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

    /**
     * 보유 쿠폰 조회
     */
    fun getUserCoupons(userId: String): List<UserCoupon> {
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
    fun validateCoupon(couponCode: String, orderAmount: Long): io.hhplus.week2.domain.CouponValidationResult {
        // TODO: Implement proper coupon validation logic
        return io.hhplus.week2.domain.CouponValidationResult(
            valid = false,
            coupon = null,
            discount = 0L,
            message = "쿠폰 검증 기능이 아직 구현되지 않았습니다"
        )
    }

    // 내부 응답 객체
    data class CouponIssueResult(
        val userCouponId: String,
        val couponName: String,
        val discountRate: Int,
        val expiresAt: LocalDateTime,
        val remainingQuantity: Int
    )
}
