package io.hhplus.ecommerce.application.services

import io.hhplus.ecommerce.domain.UserCoupon
import io.hhplus.ecommerce.exception.BusinessRuleViolationException
import io.hhplus.ecommerce.exception.CouponException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 쿠폰 발급 대기열 처리 서비스
 *
 * 대기열에서 쿠폰 발급 요청을 꺼내서 실제 DB에 저장하는 역할을 합니다.
 * 스케줄러가 주기적으로 이 서비스를 호출하여 배치 처리합니다.
 */
@Service
class CouponIssuanceProcessor(
    private val couponIssuanceQueueService: CouponIssuanceQueueService,
    private val couponIssuanceService: CouponIssuanceService,
    private val couponService: CouponService,
    private val userService: UserService,
    private val couponEventPublisher: CouponEventPublisher
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 대기열에서 배치로 쿠폰 발급 요청을 처리
     *
     * @param batchSize 배치 크기 (한 번에 처리할 요청 수)
     * @return 처리된 요청 수
     */
    fun processBatch(batchSize: Int = 100): Int {
        val requests = couponIssuanceQueueService.dequeueBatch(batchSize)

        if (requests.isEmpty()) {
            return 0
        }

        logger.info("쿠폰 발급 배치 처리 시작: batchSize={}, actualSize={}", batchSize, requests.size)

        var successCount = 0
        var failCount = 0

        for (request in requests) {
            try {
                processSingleRequest(request)
                couponIssuanceQueueService.markAsCompleted(request.requestId)
                successCount++
            } catch (e: BusinessRuleViolationException) {
                // 비즈니스 룰 위반 (이미 발급, 수량 소진 등) - 재시도 불필요
                couponIssuanceQueueService.markAsFailed(request, e.message ?: "비즈니스 룰 위반")
                failCount++
                logger.warn(
                    "쿠폰 발급 실패 (비즈니스 룰 위반): requestId={}, reason={}",
                    request.requestId, e.message
                )
            } catch (e: Exception) {
                // 시스템 오류 - 실패 큐로 이동 (향후 재시도 가능)
                couponIssuanceQueueService.markAsFailed(request, e.message ?: "시스템 오류")
                failCount++
                logger.error(
                    "쿠폰 발급 실패 (시스템 오류): requestId={}, error={}",
                    request.requestId, e.message, e
                )
            }
        }

        logger.info(
            "쿠폰 발급 배치 처리 완료: total={}, success={}, fail={}",
            requests.size, successCount, failCount
        )

        return successCount
    }

    /**
     * 단일 쿠폰 발급 요청 처리
     *
     * @param request 쿠폰 발급 요청
     */
    @Transactional
    private fun processSingleRequest(request: CouponIssuanceQueueService.CouponIssuanceRequest) {
        val couponId = request.couponId
        val userId = request.userId

        // 1. Redis에서 발급 가능 여부 재확인
        couponIssuanceService.checkIssuanceEligibility(couponId, userId)

        // 2. DB에서 쿠폰 및 사용자 조회
        val user = userService.getById(userId)
        val coupon = couponService.getById(couponId)

        // 3. 쿠폰 발급 가능 여부 확인
        if (!coupon.canIssue()) {
            throw CouponException.CouponExhausted()
        }

        // 4. 쿠폰 수량 감소
        coupon.issue()
        couponService.save(coupon)

        // 5. 사용자 쿠폰 생성
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

        couponService.saveUserCoupon(userCoupon)

        // 6. Redis에 발급 기록 (원자적 중복 체크)
        val remainingQuantity = couponIssuanceService.recordIssuance(couponId, userId)

        // 7. 쿠폰 발급 이벤트 발행
        couponEventPublisher.publishCouponIssuedEvent(userCoupon, remainingQuantity)

        // 8. 쿠폰 소진 확인 및 이벤트 발행
        if (coupon.issuedQuantity >= coupon.totalQuantity) {
            couponEventPublisher.publishCouponExhaustedEvent(coupon)
        }

        logger.info(
            "쿠폰 발급 처리 완료: requestId={}, couponId={}, userId={}, remaining={}",
            request.requestId, couponId, userId, remainingQuantity
        )
    }
}
