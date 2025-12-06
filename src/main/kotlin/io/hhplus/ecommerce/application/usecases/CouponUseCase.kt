package io.hhplus.ecommerce.application.usecases

import io.hhplus.ecommerce.application.services.CouponIssuanceService
import io.hhplus.ecommerce.application.services.CouponIssuanceQueueService
import io.hhplus.ecommerce.application.services.CouponService
import io.hhplus.ecommerce.application.services.UserService
import io.hhplus.ecommerce.domain.CouponValidationResult
import io.hhplus.ecommerce.domain.UserCoupon
import io.hhplus.ecommerce.exception.*
import org.springframework.stereotype.Service
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.UUID

@Service
class CouponUseCase(
    private val couponService: CouponService,
    private val userService: UserService,
    private val couponIssuanceService: CouponIssuanceService,
    private val couponIssuanceQueueService: CouponIssuanceQueueService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 쿠폰 발급 요청 (비동기 방식)
     *
     * Redis에서 발급 가능 여부만 빠르게 체크하고,
     * 대기열에 추가한 후 즉시 응답을 반환합니다.
     * 실제 DB 저장은 백그라운드 스케줄러가 처리합니다.
     *
     * @param couponId 쿠폰 ID
     * @param userId 사용자 ID
     * @return 쿠폰 발급 요청 결과
     */
    fun issueCoupon(couponId: Long, userId: Long): CouponIssueResult {
        // 1. Redis 사전 체크 (빠른 실패)
        try {
            couponIssuanceService.checkIssuanceEligibility(couponId, userId)
        } catch (e: BusinessRuleViolationException) {
            logger.info("쿠폰 발급 사전 체크 실패: couponId={}, userId={}, reason={}", couponId, userId, e.message)
            throw e
        } catch (e: ResourceNotFoundException) {
            logger.info("쿠폰 발급 사전 체크 실패: couponId={}, userId={}, reason={}", couponId, userId, e.message)
            throw e
        }

        // 2. 쿠폰 정보 조회 (응답에 필요한 정보)
        val coupon = couponService.getById(couponId)
        val status = couponIssuanceService.getCouponStatus(couponId)

        // 3. 발급 요청을 대기열에 추가
        val requestId = UUID.randomUUID().toString()
        val request = CouponIssuanceQueueService.CouponIssuanceRequest(
            requestId = requestId,
            couponId = couponId,
            userId = userId,
            requestedAt = System.currentTimeMillis()
        )

        val enqueued = couponIssuanceQueueService.enqueue(request)

        if (!enqueued) {
            throw CouponException.CouponIssuanceFailed("쿠폰 발급 요청 실패: 대기열 추가 오류")
        }

        logger.info(
            "쿠폰 발급 요청 대기열 추가 완료: requestId={}, couponId={}, userId={}, remaining={}",
            requestId, couponId, userId, status.remainingQuantity
        )

        // 4. 빠른 응답 반환 (실제 발급은 백그라운드에서 처리)
        return CouponIssueResult(
            userCouponId = couponId,  // 아직 실제 발급 전이므로 임시값
            couponName = coupon.name,
            discountRate = coupon.discountRate,
            expiresAt = LocalDateTime.now().plusDays(7),  // 예상 만료일
            remainingQuantity = status.remainingQuantity.toInt(),
            requestId = requestId,  // 발급 요청 ID (상태 조회용)
            status = "PENDING"  // 발급 대기 중
        )
    }

    fun getUserCoupons(userId: Long): List<UserCoupon> {
        val coupons = couponService.findUserCoupons(userId)
        val now = LocalDateTime.now()

        for (coupon in coupons) {
            if ("AVAILABLE" == coupon.status &&
                coupon.expiresAt != null &&
                now.isAfter(coupon.expiresAt)
            ) {
                coupon.expire()
                couponService.saveUserCoupon(coupon)
            }
        }

        return coupons
    }

    fun validateCoupon(couponCode: String, orderAmount: Long): CouponValidationResult {
        return CouponValidationResult(
            valid = false,
            coupon = null,
            discount = 0L,
            message = "쿠폰 검증 기능이 아직 구현되지 않았습니다"
        )
    }

    data class CouponIssueResult(
        val userCouponId: Long,
        val couponName: String,
        val discountRate: Int,
        val expiresAt: LocalDateTime,
        val remainingQuantity: Int,
        val requestId: String? = null,      // 발급 요청 ID (비동기 처리 상태 조회용)
        val status: String = "COMPLETED"    // 발급 상태 (PENDING, COMPLETED, FAILED)
    )
}
