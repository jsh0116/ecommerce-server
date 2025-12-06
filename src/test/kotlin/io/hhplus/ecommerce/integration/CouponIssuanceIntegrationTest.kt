package io.hhplus.ecommerce.integration

import io.hhplus.ecommerce.application.services.CouponIssuanceService
import io.hhplus.ecommerce.config.TestContainersConfig
import io.hhplus.ecommerce.config.TestRedisConfig
import io.hhplus.ecommerce.domain.Coupon
import io.hhplus.ecommerce.domain.CouponType
import io.hhplus.ecommerce.exception.CouponException
import io.hhplus.ecommerce.infrastructure.persistence.repository.CouponJpaRepository
import io.hhplus.ecommerce.infrastructure.persistence.entity.CouponJpaEntity
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Tag("integration")
@DisplayName("[STEP 14] CouponIssuanceService 통합 테스트")
@ActiveProfiles("test")
@Import(TestContainersConfig::class, TestRedisConfig::class)
class CouponIssuanceIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var couponIssuanceService: CouponIssuanceService

    @Autowired
    private lateinit var couponRepository: CouponJpaRepository

    @Autowired
    private lateinit var redisTemplate: RedisTemplate<String, String>

    @BeforeEach
    fun setUp() {
        val keys = redisTemplate.keys("coupon:*")
        if (keys.isNotEmpty()) {
            redisTemplate.delete(keys)
        }
        couponRepository.deleteAll()
    }

    @Nested
    @DisplayName("쿠폰 초기화 테스트")
    inner class InitializeCouponTest {

        @Test
        fun `쿠폰 Redis 데이터를 초기화할 수 있다`() {
            // Given
            val couponId = 1L
            val totalQuantity = 100

            // When
            couponIssuanceService.initializeCoupon(couponId, totalQuantity)

            // Then
            val quotaKey = "coupon:quota:$couponId"
            val countKey = "coupon:count:$couponId"

            assertThat(redisTemplate.opsForValue().get(quotaKey)).isEqualTo("100")
            assertThat(redisTemplate.opsForValue().get(countKey)).isEqualTo("0")
        }
    }

    @Nested
    @DisplayName("중복 발급 체크 테스트")
    inner class DuplicateCheckTest {

        @Test
        fun `Redis Set으로 중복 발급을 빠르게 체크한다`() {
            // Given
            val couponId = 1L
            val userId = 1L
            couponIssuanceService.initializeCoupon(couponId, 100)

            // When - 첫 발급 체크는 통과
            couponIssuanceService.checkIssuanceEligibility(couponId, userId)

            // 발급 기록
            couponIssuanceService.recordIssuance(couponId, userId)

            // Then - 두 번째 발급 체크는 실패
            assertThatThrownBy {
                couponIssuanceService.checkIssuanceEligibility(couponId, userId)
            }.isInstanceOf(CouponException.AlreadyIssuedCoupon::class.java)
        }

        @Test
        fun `여러 사용자가 동시에 발급해도 중복이 없다`() {
            // Given
            val couponId = 1L
            val userCount = 50
            couponIssuanceService.initializeCoupon(couponId, userCount)

            // When - 50명의 사용자가 동시에 발급
            val threads = (1L..userCount.toLong()).map { userId ->
                Thread {
                    try {
                        couponIssuanceService.checkIssuanceEligibility(couponId, userId)
                        couponIssuanceService.recordIssuance(couponId, userId)
                    } catch (e: Exception) {
                        // 정상적인 예외는 무시
                    }
                }
            }

            threads.forEach { it.start() }
            threads.forEach { it.join() }

            // Then
            val issuedSetKey = "coupon:issued:$couponId"
            val issuedCount = redisTemplate.opsForSet().size(issuedSetKey)
            assertThat(issuedCount).isEqualTo(userCount.toLong())
        }
    }

    @Nested
    @DisplayName("선착순 수량 관리 테스트")
    inner class QuotaManagementTest {

        @Test
        fun `Redis INCR로 발급 수량을 atomic하게 증가시킨다`() {
            // Given
            val couponId = 1L
            couponIssuanceService.initializeCoupon(couponId, 100)

            // When
            couponIssuanceService.recordIssuance(couponId, 1L)
            couponIssuanceService.recordIssuance(couponId, 2L)
            couponIssuanceService.recordIssuance(couponId, 3L)

            // Then
            val status = couponIssuanceService.getCouponStatus(couponId)
            assertThat(status.issuedCount).isEqualTo(3L)
            assertThat(status.remainingQuantity).isEqualTo(97L)
        }

        @Test
        fun `수량이 소진되면 발급을 막는다`() {
            // Given
            val couponId = 1L
            val totalQuantity = 5
            couponIssuanceService.initializeCoupon(couponId, totalQuantity)

            // When - 5장 모두 발급
            for (userId in 1L..5L) {
                couponIssuanceService.recordIssuance(couponId, userId)
            }

            // Then - 6번째 발급은 실패
            assertThatThrownBy {
                couponIssuanceService.checkIssuanceEligibility(couponId, 6L)
            }.isInstanceOf(CouponException.CouponExhausted::class.java)
        }
    }

    @Nested
    @DisplayName("동시성 테스트")
    inner class ConcurrencyTest {

        @Test
        fun `100명이 동시에 50장 쿠폰에 요청해도 정확히 50장만 발급된다`() {
            // Given
            val couponId = 1L
            val totalQuantity = 50
            val totalUsers = 100
            couponIssuanceService.initializeCoupon(couponId, totalQuantity)

            // When - 100명이 동시에 요청
            val successCount = java.util.concurrent.atomic.AtomicInteger(0)
            val failCount = java.util.concurrent.atomic.AtomicInteger(0)

            val threads = (1L..totalUsers.toLong()).map { userId ->
                Thread {
                    try {
                        couponIssuanceService.checkIssuanceEligibility(couponId, userId)
                        couponIssuanceService.recordIssuance(couponId, userId)
                        successCount.incrementAndGet()
                    } catch (e: CouponException.CouponExhausted) {
                        failCount.incrementAndGet()
                    } catch (e: Exception) {
                        failCount.incrementAndGet()
                    }
                }
            }

            threads.forEach { it.start() }
            threads.forEach { it.join() }

            // Then
            assertThat(successCount.get()).isEqualTo(totalQuantity)
            assertThat(failCount.get()).isEqualTo(totalUsers - totalQuantity)

            val status = couponIssuanceService.getCouponStatus(couponId)
            assertThat(status.issuedCount).isEqualTo(totalQuantity.toLong())
            assertThat(status.remainingQuantity).isEqualTo(0L)
        }
    }

    @Nested
    @DisplayName("쿠폰 상태 조회 테스트")
    inner class StatusQueryTest {

        @Test
        fun `쿠폰 발급 상태를 조회할 수 있다`() {
            // Given
            val couponId = 1L
            couponIssuanceService.initializeCoupon(couponId, 100)
            couponIssuanceService.recordIssuance(couponId, 1L)
            couponIssuanceService.recordIssuance(couponId, 2L)

            // When
            val status = couponIssuanceService.getCouponStatus(couponId)

            // Then
            assertThat(status.couponId).isEqualTo(1L)
            assertThat(status.totalQuantity).isEqualTo(100L)
            assertThat(status.issuedCount).isEqualTo(2L)
            assertThat(status.remainingQuantity).isEqualTo(98L)
        }

        @Test
        fun `특정 사용자의 쿠폰 발급 여부를 확인할 수 있다`() {
            // Given
            val couponId = 1L
            val userId = 1L
            couponIssuanceService.initializeCoupon(couponId, 100)

            // When - Before
            val beforeIssued = couponIssuanceService.hasUserIssuedCoupon(couponId, userId)

            // 발급 후
            couponIssuanceService.recordIssuance(couponId, userId)
            val afterIssued = couponIssuanceService.hasUserIssuedCoupon(couponId, userId)

            // Then
            assertThat(beforeIssued).isFalse
            assertThat(afterIssued).isTrue
        }
    }

    @Nested
    @DisplayName("Redis Key 전략 테스트")
    inner class KeyStrategyTest {

        @Test
        fun `쿠폰 발급 시 올바른 Redis 키가 생성된다`() {
            // Given
            val couponId = 1L
            val userId = 1L
            couponIssuanceService.initializeCoupon(couponId, 100)

            // When
            couponIssuanceService.recordIssuance(couponId, userId)

            // Then - Set, Count, Quota 키 모두 존재
            assertThat(redisTemplate.hasKey("coupon:issued:$couponId")).isTrue
            assertThat(redisTemplate.hasKey("coupon:count:$couponId")).isTrue
            assertThat(redisTemplate.hasKey("coupon:quota:$couponId")).isTrue
        }

        @Test
        fun `Redis 키에 TTL이 설정된다`() {
            // Given
            val couponId = 1L
            couponIssuanceService.initializeCoupon(couponId, 100)

            // When
            couponIssuanceService.recordIssuance(couponId, 1L)

            // Then - TTL이 설정되어 있음 (seconds > 0)
            val issuedSetTtl = redisTemplate.getExpire("coupon:issued:$couponId")
            val countTtl = redisTemplate.getExpire("coupon:count:$couponId")
            val quotaTtl = redisTemplate.getExpire("coupon:quota:$couponId")

            assertThat(issuedSetTtl).isGreaterThan(0)
            assertThat(countTtl).isGreaterThan(0)
            assertThat(quotaTtl).isGreaterThan(0)
        }
    }
}
