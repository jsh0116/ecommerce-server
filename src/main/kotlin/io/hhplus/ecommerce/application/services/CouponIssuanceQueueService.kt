package io.hhplus.ecommerce.application.services

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

/**
 * Redis List 기반 쿠폰 발급 대기열 서비스
 *
 * 비동기 쿠폰 발급을 위한 대기열 관리:
 * - 쿠폰 발급 요청을 Redis List에 추가 (LPUSH)
 * - 스케줄러가 주기적으로 대기열에서 꺼내서 처리 (RPOP)
 * - 처리 실패 시 재시도 큐로 이동
 *
 * Key 구조:
 * - coupon:queue:pending - 대기 중인 발급 요청 리스트
 * - coupon:queue:processing:{requestId} - 처리 중인 요청 (TTL 5분)
 * - coupon:queue:failed - 처리 실패한 요청 리스트
 */
@Service
class CouponIssuanceQueueService(
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val PENDING_QUEUE_KEY = "coupon:queue:pending"
        private const val PROCESSING_KEY_PREFIX = "coupon:queue:processing:"
        private const val FAILED_QUEUE_KEY = "coupon:queue:failed"
        private const val PROCESSING_TTL_MINUTES = 5L
    }

    /**
     * 쿠폰 발급 요청을 대기열에 추가
     *
     * @param request 쿠폰 발급 요청
     * @return 대기열에 추가 성공 여부
     */
    fun enqueue(request: CouponIssuanceRequest): Boolean {
        try {
            val requestJson = objectMapper.writeValueAsString(request)
            redisTemplate.opsForList().leftPush(PENDING_QUEUE_KEY, requestJson)

            logger.info(
                "쿠폰 발급 요청 대기열 추가: requestId={}, couponId={}, userId={}",
                request.requestId, request.couponId, request.userId
            )

            return true
        } catch (e: Exception) {
            logger.error("쿠폰 발급 요청 대기열 추가 실패: request={}, error={}", request, e.message, e)
            return false
        }
    }

    /**
     * 대기열에서 쿠폰 발급 요청을 꺼내서 반환
     *
     * @return 쿠폰 발급 요청 (없으면 null)
     */
    fun dequeue(): CouponIssuanceRequest? {
        try {
            // RPOP: 오른쪽에서 꺼내기 (FIFO)
            val requestJson = redisTemplate.opsForList().rightPop(PENDING_QUEUE_KEY)
                ?: return null

            val request = objectMapper.readValue(requestJson, CouponIssuanceRequest::class.java)

            // 처리 중 상태로 마킹 (TTL 설정)
            val processingKey = PROCESSING_KEY_PREFIX + request.requestId
            redisTemplate.opsForValue().set(processingKey, requestJson, PROCESSING_TTL_MINUTES, TimeUnit.MINUTES)

            logger.debug("쿠폰 발급 요청 대기열에서 꺼냄: requestId={}", request.requestId)

            return request
        } catch (e: Exception) {
            logger.error("쿠폰 발급 요청 dequeue 실패: error={}", e.message, e)
            return null
        }
    }

    /**
     * 배치로 여러 요청을 꺼내기
     *
     * @param batchSize 배치 크기
     * @return 쿠폰 발급 요청 리스트
     */
    fun dequeueBatch(batchSize: Int): List<CouponIssuanceRequest> {
        val requests = mutableListOf<CouponIssuanceRequest>()

        repeat(batchSize) {
            val request = dequeue() ?: return requests
            requests.add(request)
        }

        return requests
    }

    /**
     * 처리 완료 마킹 (처리 중 상태 제거)
     *
     * @param requestId 요청 ID
     */
    fun markAsCompleted(requestId: String) {
        try {
            val processingKey = PROCESSING_KEY_PREFIX + requestId
            redisTemplate.delete(processingKey)

            logger.debug("쿠폰 발급 요청 처리 완료: requestId={}", requestId)
        } catch (e: Exception) {
            logger.error("쿠폰 발급 요청 완료 마킹 실패: requestId={}, error={}", requestId, e.message)
        }
    }

    /**
     * 처리 실패한 요청을 실패 큐로 이동
     *
     * @param request 쿠폰 발급 요청
     * @param failReason 실패 사유
     */
    fun markAsFailed(request: CouponIssuanceRequest, failReason: String) {
        try {
            val processingKey = PROCESSING_KEY_PREFIX + request.requestId
            redisTemplate.delete(processingKey)

            val failedRequest = request.copy(
                failReason = failReason,
                failedAt = System.currentTimeMillis()
            )
            val failedJson = objectMapper.writeValueAsString(failedRequest)

            redisTemplate.opsForList().leftPush(FAILED_QUEUE_KEY, failedJson)

            logger.warn(
                "쿠폰 발급 요청 처리 실패: requestId={}, reason={}",
                request.requestId, failReason
            )
        } catch (e: Exception) {
            logger.error("쿠폰 발급 요청 실패 마킹 실패: requestId={}, error={}", request.requestId, e.message)
        }
    }

    /**
     * 대기 중인 요청 수 조회
     *
     * @return 대기 큐 크기
     */
    fun getPendingCount(): Long {
        return redisTemplate.opsForList().size(PENDING_QUEUE_KEY) ?: 0L
    }

    /**
     * 실패한 요청 수 조회
     *
     * @return 실패 큐 크기
     */
    fun getFailedCount(): Long {
        return redisTemplate.opsForList().size(FAILED_QUEUE_KEY) ?: 0L
    }

    /**
     * 쿠폰 발급 요청 DTO
     */
    data class CouponIssuanceRequest(
        val requestId: String,       // 요청 고유 ID (UUID)
        val couponId: Long,          // 쿠폰 ID
        val userId: Long,            // 사용자 ID
        val requestedAt: Long = System.currentTimeMillis(),  // 요청 시각
        val failReason: String? = null,     // 실패 사유
        val failedAt: Long? = null          // 실패 시각
    )
}
