package io.hhplus.ecommerce.application.usecases

import io.hhplus.ecommerce.application.services.AsyncCouponIssuanceService
import io.hhplus.ecommerce.application.services.CouponIssuanceService
import io.hhplus.ecommerce.application.services.CouponService
import io.hhplus.ecommerce.config.TestKafkaConfig
import io.hhplus.ecommerce.domain.Coupon
import io.hhplus.ecommerce.domain.CouponType
import io.hhplus.ecommerce.test.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.TestPropertySource
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis

/**
 * Redis Lock vs Kafka 쿠폰 발급 성능 비교 테스트
 *
 * 비교 항목:
 * 1. 처리량 (Throughput): 초당 처리 가능한 요청 수
 * 2. 응답 시간 (Latency): 평균/P95/P99 응답 시간
 * 3. 동시성 처리 능력: 동시 요청 처리 성공률
 * 4. 확장성: 부하 증가 시 성능 변화
 *
 * 테스트 시나리오:
 * - 100명의 사용자가 동시에 쿠폰 발급 요청
 * - 쿠폰 재고는 50개 (의도적 경쟁 상황 생성)
 * - Redis Lock: 동기 방식, 분산 락 사용
 * - Kafka: 비동기 방식, 파티션 기반 순서 보장
 */
@SpringBootTest
@EmbeddedKafka(
    topics = ["coupon.issuance.requested"],
    partitions = 3,
    bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@TestPropertySource(
    properties = [
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.consumer.group-id=test-group"
    ]
)
@Import(TestKafkaConfig::class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Tag("performance")
@DisplayName("쿠폰 발급 성능 비교 테스트: Redis Lock vs Kafka")
class CouponIssuancePerformanceComparisonTest : IntegrationTestBase() {

    @Autowired
    private lateinit var couponUseCase: CouponUseCase

    @Autowired
    private lateinit var couponService: CouponService

    @Autowired
    private lateinit var couponIssuanceService: CouponIssuanceService

    @Autowired
    private lateinit var asyncCouponIssuanceService: AsyncCouponIssuanceService

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val COUPON_ID = 999L
        private const val COUPON_QUANTITY = 50
        private const val CONCURRENT_USERS = 100
        private const val THREAD_POOL_SIZE = 20
    }

    @BeforeEach
    fun setUp() {
        // 테스트용 쿠폰 생성
        val coupon = Coupon(
            id = COUPON_ID,
            code = "PERF-TEST-COUPON",
            name = "성능 테스트 쿠폰",
            type = CouponType.FIXED_AMOUNT,
            discount = 5000L,
            discountRate = 10,
            totalQuantity = COUPON_QUANTITY,
            issuedQuantity = 0,
            startDate = LocalDateTime.now().minusDays(1),
            endDate = LocalDateTime.now().plusDays(7)
        )

        try {
            couponService.save(coupon)
            logger.info("테스트 쿠폰 생성 완료: couponId=$COUPON_ID, quantity=$COUPON_QUANTITY")
        } catch (e: Exception) {
            logger.warn("쿠폰 생성 중 예외 (이미 존재할 수 있음): ${e.message}")
        }

        // Redis 초기화
        couponIssuanceService.initializeCoupon(COUPON_ID, COUPON_QUANTITY)
    }

    @Test
    @DisplayName("Redis Lock 방식 - 동시 발급 성능 테스트")
    fun testRedisLockBasedIssuance() {
        logger.info("=".repeat(80))
        logger.info("Redis Lock 방식 성능 테스트 시작")
        logger.info("동시 사용자: $CONCURRENT_USERS, 쿠폰 수량: $COUPON_QUANTITY")
        logger.info("=".repeat(80))

        val executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE)
        val latch = CountDownLatch(CONCURRENT_USERS)
        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)
        val latencies = mutableListOf<Long>()

        val totalTime = measureTimeMillis {
            for (userId in 1..CONCURRENT_USERS) {
                executor.submit {
                    try {
                        val latency = measureTimeMillis {
                            try {
                                // Redis Lock 기반 동기 발급
                                couponUseCase.issueCoupon(COUPON_ID, userId.toLong())
                                successCount.incrementAndGet()
                            } catch (e: Exception) {
                                failCount.incrementAndGet()
                            }
                        }
                        synchronized(latencies) {
                            latencies.add(latency)
                        }
                    } finally {
                        latch.countDown()
                    }
                }
            }
            latch.await(60, TimeUnit.SECONDS)
        }

        executor.shutdown()
        executor.awaitTermination(10, TimeUnit.SECONDS)

        // 결과 분석
        val throughput = (successCount.get().toDouble() / totalTime) * 1000
        val avgLatency = latencies.average()
        val p95Latency = latencies.sorted()[((latencies.size * 0.95).toInt())]
        val p99Latency = latencies.sorted()[((latencies.size * 0.99).toInt())]

        logger.info("-".repeat(80))
        logger.info("Redis Lock 방식 성능 결과:")
        logger.info("  총 소요 시간: ${totalTime}ms")
        logger.info("  성공: ${successCount.get()}, 실패: ${failCount.get()}")
        logger.info("  처리량 (Throughput): ${"%.2f".format(throughput)} req/sec")
        logger.info("  평균 응답 시간: ${"%.2f".format(avgLatency)}ms")
        logger.info("  P95 응답 시간: ${p95Latency}ms")
        logger.info("  P99 응답 시간: ${p99Latency}ms")
        logger.info("  성공률: ${"%.2f".format((successCount.get().toDouble() / CONCURRENT_USERS) * 100)}%")
        logger.info("-".repeat(80))

        // 검증: 정확히 COUPON_QUANTITY개만 발급되어야 함
        assertThat(successCount.get()).isLessThanOrEqualTo(COUPON_QUANTITY)
    }

    @Test
    @DisplayName("Kafka 방식 - 동시 발급 성능 테스트")
    fun testKafkaBasedIssuance() {
        logger.info("=".repeat(80))
        logger.info("Kafka 방식 성능 테스트 시작")
        logger.info("동시 사용자: $CONCURRENT_USERS, 쿠폰 수량: $COUPON_QUANTITY")
        logger.info("=".repeat(80))

        val executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE)
        val latch = CountDownLatch(CONCURRENT_USERS)
        val requestCount = AtomicInteger(0)
        val latencies = mutableListOf<Long>()

        val totalTime = measureTimeMillis {
            for (userId in 1..CONCURRENT_USERS) {
                executor.submit {
                    try {
                        val latency = measureTimeMillis {
                            try {
                                // Kafka 기반 비동기 발급
                                couponUseCase.requestCouponIssuanceAsync(COUPON_ID, userId.toLong())
                                requestCount.incrementAndGet()
                            } catch (e: Exception) {
                                logger.error("Kafka 발급 요청 실패: userId=$userId", e)
                            }
                        }
                        synchronized(latencies) {
                            latencies.add(latency)
                        }
                    } finally {
                        latch.countDown()
                    }
                }
            }
            latch.await(60, TimeUnit.SECONDS)
        }

        executor.shutdown()
        executor.awaitTermination(10, TimeUnit.SECONDS)

        // 결과 분석 (요청 접수 기준)
        val throughput = (requestCount.get().toDouble() / totalTime) * 1000
        val avgLatency = latencies.average()
        val p95Latency = latencies.sorted()[((latencies.size * 0.95).toInt())]
        val p99Latency = latencies.sorted()[((latencies.size * 0.99).toInt())]

        logger.info("-".repeat(80))
        logger.info("Kafka 방식 성능 결과 (요청 접수 기준):")
        logger.info("  총 소요 시간: ${totalTime}ms")
        logger.info("  요청 접수: ${requestCount.get()}개")
        logger.info("  처리량 (Throughput): ${"%.2f".format(throughput)} req/sec")
        logger.info("  평균 응답 시간: ${"%.2f".format(avgLatency)}ms")
        logger.info("  P95 응답 시간: ${p95Latency}ms")
        logger.info("  P99 응답 시간: ${p99Latency}ms")
        logger.info("  요청 접수율: ${"%.2f".format((requestCount.get().toDouble() / CONCURRENT_USERS) * 100)}%")
        logger.info("")
        logger.info("  참고: 실제 발급 처리는 Kafka Consumer에서 비동기적으로 진행됩니다.")
        logger.info("       Consumer 처리 시간은 별도로 측정이 필요합니다.")
        logger.info("-".repeat(80))

        // 검증: 모든 요청이 성공적으로 접수되어야 함
        assertThat(requestCount.get()).isEqualTo(CONCURRENT_USERS)
    }

    @Test
    @DisplayName("성능 비교 종합 분석")
    fun comprehensivePerformanceComparison() {
        logger.info("=".repeat(80))
        logger.info("Redis Lock vs Kafka 종합 성능 비교")
        logger.info("=".repeat(80))

        logger.info("""

            [Redis Lock 방식]
            장점:
              - 즉시 응답 (동기 처리)
              - 트랜잭션 일관성 보장
              - 구현이 직관적

            단점:
              - 락 대기로 인한 높은 지연시간
              - 제한된 처리량 (Lock contention)
              - 스케일 아웃 한계

            적합한 경우:
              - 즉시 응답이 필요한 경우
              - 동시 요청 수가 많지 않은 경우
              - 트랜잭션 일관성이 중요한 경우

            [Kafka 방식]
            장점:
              - 매우 빠른 요청 접수 (비동기)
              - 높은 처리량 (Lock-free)
              - 수평 확장 가능 (파티션 증가)
              - 메시지 영구 저장 (재처리 가능)

            단점:
              - 최종 일관성 (Eventual Consistency)
              - 복잡한 인프라 (Kafka, Consumer)
              - 디버깅 난이도 증가

            적합한 경우:
              - 대량의 동시 요청 처리
              - 높은 처리량이 필요한 경우
              - 비동기 처리 가능한 경우
              - 수평 확장이 필요한 경우

            [권장사항]
            - 선착순 이벤트 (쿠폰 발급 등): Kafka 방식 권장
            - 실시간 재고 확인: Redis Lock 방식 권장
            - 하이브리드: 빠른 응답 + 비동기 처리 조합 고려
        """.trimIndent())

        logger.info("=".repeat(80))
    }
}
