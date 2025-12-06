package io.hhplus.ecommerce.application.schedulers

import io.hhplus.ecommerce.application.services.CouponIssuanceProcessor
import io.hhplus.ecommerce.application.services.CouponIssuanceQueueService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 쿠폰 발급 대기열 처리 스케줄러
 *
 * 주기적으로 대기열에서 쿠폰 발급 요청을 꺼내서 처리합니다.
 * - 실행 주기: 1초마다 (fixedDelay = 1000ms)
 * - 배치 크기: 100개 (한 번에 처리할 요청 수)
 */
@Component
class CouponIssuanceScheduler(
    private val couponIssuanceProcessor: CouponIssuanceProcessor,
    private val couponIssuanceQueueService: CouponIssuanceQueueService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val BATCH_SIZE = 100
    }

    /**
     * 쿠폰 발급 대기열 처리 (1초마다 실행)
     *
     * fixedDelay: 이전 작업 완료 후 1초 뒤에 다시 실행
     */
    @Scheduled(fixedDelay = 1000)
    fun processIssuanceQueue() {
        try {
            val pendingCount = couponIssuanceQueueService.getPendingCount()

            if (pendingCount == 0L) {
                // 대기 중인 요청 없음 - 로그 생략
                return
            }

            logger.debug("쿠폰 발급 대기열 처리 시작: pendingCount={}", pendingCount)

            val processedCount = couponIssuanceProcessor.processBatch(BATCH_SIZE)

            if (processedCount > 0) {
                logger.info(
                    "쿠폰 발급 대기열 처리 완료: processed={}, remaining={}",
                    processedCount, couponIssuanceQueueService.getPendingCount()
                )
            }
        } catch (e: Exception) {
            logger.error("쿠폰 발급 스케줄러 오류: error={}", e.message, e)
        }
    }

    /**
     * 실패한 쿠폰 발급 요청 모니터링 (1분마다 실행)
     */
    @Scheduled(fixedDelay = 60000)
    fun monitorFailedRequests() {
        try {
            val failedCount = couponIssuanceQueueService.getFailedCount()

            if (failedCount > 0) {
                logger.warn(
                    "쿠폰 발급 실패 요청 존재: failedCount={} (수동 확인 필요)",
                    failedCount
                )
            }
        } catch (e: Exception) {
            logger.error("쿠폰 발급 실패 모니터링 오류: error={}", e.message)
        }
    }
}
