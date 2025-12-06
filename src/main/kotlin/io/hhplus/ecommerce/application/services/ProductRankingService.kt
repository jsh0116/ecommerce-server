package io.hhplus.ecommerce.application.services

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Redis Sorted Set 기반 실시간 상품 랭킹 서비스
 *
 * 설계 원칙:
 * - Redis Sorted Set을 사용하여 O(log N) 성능으로 실시간 랭킹 집계
 * - 일간/주간 랭킹을 Key 분리로 관리
 * - TTL을 통한 자동 만료로 메모리 관리
 * - DB 부하 없이 실시간 랭킹 조회 가능
 * - 랭킹 조회 결과는 Product ID 리스트만 반환 (Product 조회는 UseCase에서 담당)
 *
 * Key 구조:
 * - ranking:products:daily:{YYYYMMDD} - 일간 상품 판매 랭킹
 * - ranking:products:weekly:{YYYY-Www} - 주간 상품 판매 랭킹
 *
 * Redis 명령어:
 * - ZINCRBY: 판매량 증가 (O(log N))
 * - ZREVRANGE: TOP N 조회 (O(log(N) + M))
 * - ZREVRANK: 특정 상품 순위 조회 (O(log N))
 * - ZSCORE: 특정 상품 점수(판매량) 조회 (O(1))
 */
@Service
class ProductRankingService(
    private val redisTemplate: RedisTemplate<String, String>
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val DAILY_RANKING_PREFIX = "ranking:products:daily:"
        private const val WEEKLY_RANKING_PREFIX = "ranking:products:weekly:"
        private const val DAILY_TTL_DAYS = 7L
        private const val WEEKLY_TTL_DAYS = 30L
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd")
        private val WEEK_FORMATTER = DateTimeFormatter.ofPattern("YYYY-'W'ww")
    }

    /**
     * 상품 판매 시 랭킹 증가
     *
     * @param productId 상품 ID
     * @param quantity 판매 수량
     * @param date 판매 날짜 (기본값: 오늘)
     */
    fun incrementSales(productId: Long, quantity: Int, date: LocalDate = LocalDate.now()) {
        require(quantity > 0) { "판매 수량은 1 이상이어야 합니다" }

        try {
            val dailyKey = getDailyRankingKey(date)
            val weeklyKey = getWeeklyRankingKey(date)

            // Redis Sorted Set에 판매량 증가 (ZINCRBY)
            // - productId를 member로, 판매량을 score로 저장
            // - score가 높을수록 순위가 높음
            redisTemplate.opsForZSet().incrementScore(dailyKey, productId.toString(), quantity.toDouble())
            redisTemplate.opsForZSet().incrementScore(weeklyKey, productId.toString(), quantity.toDouble())

            // TTL 설정 (키가 처음 생성될 때만 설정)
            setTTLIfNotExists(dailyKey, DAILY_TTL_DAYS, TimeUnit.DAYS)
            setTTLIfNotExists(weeklyKey, WEEKLY_TTL_DAYS, TimeUnit.DAYS)

            logger.debug("상품 랭킹 증가: productId={}, quantity={}, date={}", productId, quantity, date)
        } catch (e: Exception) {
            logger.error("상품 랭킹 증가 실패: productId={}, error={}", productId, e.message, e)
            // Redis 장애 시에도 서비스는 계속 동작해야 하므로 예외를 전파하지 않음
        }
    }

    /**
     * 일간 TOP N 상품 ID 조회
     *
     * @param limit 조회할 상품 수 (기본값: 10)
     * @param date 조회 날짜 (기본값: 오늘)
     * @return 랭킹 순으로 정렬된 상품 ID 목록
     */
    fun getTopProductIdsDaily(limit: Int = 10, date: LocalDate = LocalDate.now()): List<RankingProductId> {
        return getTopProductIds(getDailyRankingKey(date), limit)
    }

    /**
     * 주간 TOP N 상품 ID 조회
     *
     * @param limit 조회할 상품 수 (기본값: 10)
     * @param date 조회 날짜 (기본값: 오늘)
     * @return 랭킹 순으로 정렬된 상품 ID 목록
     */
    fun getTopProductIdsWeekly(limit: Int = 10, date: LocalDate = LocalDate.now()): List<RankingProductId> {
        return getTopProductIds(getWeeklyRankingKey(date), limit)
    }

    /**
     * 특정 상품의 일간 순위 조회
     *
     * @param productId 상품 ID
     * @param date 조회 날짜 (기본값: 오늘)
     * @return 순위 (1부터 시작, 랭킹 없으면 null)
     */
    fun getProductRankDaily(productId: Long, date: LocalDate = LocalDate.now()): Int? {
        return getProductRank(getDailyRankingKey(date), productId)
    }

    /**
     * 특정 상품의 주간 순위 조회
     *
     * @param productId 상품 ID
     * @param date 조회 날짜 (기본값: 오늘)
     * @return 순위 (1부터 시작, 랭킹 없으면 null)
     */
    fun getProductRankWeekly(productId: Long, date: LocalDate = LocalDate.now()): Int? {
        return getProductRank(getWeeklyRankingKey(date), productId)
    }

    /**
     * 특정 상품의 판매량 조회
     *
     * @param productId 상품 ID
     * @param date 조회 날짜 (기본값: 오늘)
     * @return 판매량 (랭킹 없으면 0)
     */
    fun getProductSalesCount(productId: Long, date: LocalDate = LocalDate.now()): Long {
        try {
            val dailyKey = getDailyRankingKey(date)
            val score = redisTemplate.opsForZSet().score(dailyKey, productId.toString())
            return score?.toLong() ?: 0L
        } catch (e: Exception) {
            logger.error("상품 판매량 조회 실패: productId={}, error={}", productId, e.message)
            return 0L
        }
    }

    // ===== Private Helper Methods =====

    /**
     * TOP N 상품 ID 조회 (공통 로직)
     *
     * Redis에서 상위 N개의 상품 ID와 판매량만 조회합니다.
     * Product 상세 정보는 UseCase 레이어에서 조회합니다.
     */
    private fun getTopProductIds(key: String, limit: Int): List<RankingProductId> {
        try {
            // ZREVRANGE: score 높은 순으로 조회 (내림차순)
            // 0부터 limit-1까지 조회
            val productIds = redisTemplate.opsForZSet()
                .reverseRange(key, 0, (limit - 1).toLong())
                ?: emptySet()

            if (productIds.isEmpty()) {
                return emptyList()
            }

            // 랭킹 순서대로 Product ID와 판매량만 반환
            return productIds.mapIndexedNotNull { index, productIdStr ->
                val productId = productIdStr.toLongOrNull() ?: return@mapIndexedNotNull null
                val salesCount = redisTemplate.opsForZSet().score(key, productIdStr)?.toLong() ?: 0L

                RankingProductId(
                    rank = index + 1,
                    productId = productId,
                    salesCount = salesCount
                )
            }
        } catch (e: Exception) {
            logger.error("TOP 상품 ID 조회 실패: key={}, error={}", key, e.message)
            return emptyList()
        }
    }

    /**
     * 특정 상품 순위 조회 (공통 로직)
     */
    private fun getProductRank(key: String, productId: Long): Int? {
        try {
            // ZREVRANK: score 높은 순으로 순위 조회 (0부터 시작)
            val rank = redisTemplate.opsForZSet()
                .reverseRank(key, productId.toString())

            // Redis는 0부터 시작하므로 +1 (사용자에게는 1부터 표시)
            return rank?.let { (it + 1).toInt() }
        } catch (e: Exception) {
            logger.error("상품 순위 조회 실패: productId={}, error={}", productId, e.message)
            return null
        }
    }

    /**
     * TTL 설정 (키가 TTL이 없을 때만 설정)
     */
    private fun setTTLIfNotExists(key: String, timeout: Long, unit: TimeUnit) {
        try {
            val ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS)
            // TTL이 -1이면 영구 키이므로 TTL 설정 필요
            if (ttl == -1L) {
                redisTemplate.expire(key, timeout, unit)
                logger.debug("TTL 설정: key={}, timeout={} {}", key, timeout, unit)
            }
        } catch (e: Exception) {
            logger.warn("TTL 설정 실패: key={}, error={}", key, e.message)
        }
    }

    /**
     * 일간 랭킹 키 생성
     */
    private fun getDailyRankingKey(date: LocalDate): String {
        return DAILY_RANKING_PREFIX + date.format(DATE_FORMATTER)
    }

    /**
     * 주간 랭킹 키 생성
     */
    private fun getWeeklyRankingKey(date: LocalDate): String {
        return WEEKLY_RANKING_PREFIX + date.format(WEEK_FORMATTER)
    }

    /**
     * 랭킹 상품 ID DTO
     *
     * Redis 랭킹에서 조회한 상품 ID와 판매량 정보만 포함합니다.
     * Product 상세 정보는 UseCase에서 별도로 조회합니다.
     */
    data class RankingProductId(
        val rank: Int,
        val productId: Long,
        val salesCount: Long
    )
}
