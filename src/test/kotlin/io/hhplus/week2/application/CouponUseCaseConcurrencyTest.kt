package io.hhplus.week2.application

import io.hhplus.week2.domain.Coupon
import io.hhplus.week2.repository.CouponRepository
import io.hhplus.week2.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 쿠폰 발급 동시성 테스트
 *
 * 선착순 쿠폰 발급 시 Race Condition이 발생하지 않는지 검증합니다.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class CouponUseCaseConcurrencyTest {

    @Autowired
    private lateinit var couponUseCase: CouponUseCase

    @Autowired
    private lateinit var couponRepository: CouponRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @BeforeEach
    fun setUp() {
        // 테스트를 위해 쿠폰 초기화
        val coupon1 = Coupon(
            id = "C001",
            name = "신규 회원 10% 할인",
            discountRate = 10,
            totalQuantity = 10,
            issuedQuantity = 0,
            startDate = LocalDateTime.now().minusDays(1),
            endDate = LocalDateTime.now().plusDays(30)
        )
        couponRepository.save(coupon1)

        val coupon2 = Coupon(
            id = "C002",
            name = "여름 세일 20% 할인",
            discountRate = 20,
            totalQuantity = 5,
            issuedQuantity = 0,
            startDate = LocalDateTime.now().minusDays(1),
            endDate = LocalDateTime.now().plusDays(7)
        )
        couponRepository.save(coupon2)
    }

    @Test
    @DisplayName("100명이 동시에 10개 한정 쿠폰을 발급받으면 정확히 10명만 성공한다")
    fun concurrentCouponIssuance_shouldIssueExactQuantity() {
        // Given
        val couponId = "C001" // 10개 한정 쿠폰
        val totalUsers = 100
        val expectedSuccessCount = 10

        val successCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)

        // 100명의 사용자가 동시에 요청
        val executor = Executors.newFixedThreadPool(50)
        val latch = CountDownLatch(totalUsers)

        // When
        repeat(totalUsers) { index ->
            executor.submit {
                try {
                    val userId = "user${index + 1}"
                    couponUseCase.issueCoupon(couponId, userId)
                    successCount.incrementAndGet()
                } catch (e: IllegalStateException) {
                    // 쿠폰 소진 또는 이미 발급받은 경우
                    failureCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        // Then
        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // 정확히 10명만 성공해야 함
        assertThat(successCount.get()).isEqualTo(expectedSuccessCount)
        assertThat(failureCount.get()).isEqualTo(totalUsers - expectedSuccessCount)

        // 쿠폰의 발급 수량이 정확히 10개여야 함
        val coupon = couponRepository.findById(couponId)
        assertThat(coupon).isNotNull
        assertThat(coupon!!.issuedQuantity).isEqualTo(expectedSuccessCount)
    }

    @Test
    @DisplayName("50명이 동시에 5개 한정 쿠폰을 발급받으면 정확히 5명만 성공한다")
    fun concurrentCouponIssuance_withSmallerQuantity_shouldIssueExactQuantity() {
        // Given
        val couponId = "C002" // 5개 한정 쿠폰
        val totalUsers = 50
        val expectedSuccessCount = 5

        val successCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)

        val executor = Executors.newFixedThreadPool(25)
        val latch = CountDownLatch(totalUsers)

        // When
        repeat(totalUsers) { index ->
            executor.submit {
                try {
                    val userId = "concurrent_user_${index + 1}"
                    couponUseCase.issueCoupon(couponId, userId)
                    successCount.incrementAndGet()
                } catch (e: IllegalStateException) {
                    failureCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        // Then
        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        assertThat(successCount.get()).isEqualTo(expectedSuccessCount)
        assertThat(failureCount.get()).isEqualTo(totalUsers - expectedSuccessCount)

        val coupon = couponRepository.findById(couponId)
        assertThat(coupon!!.issuedQuantity).isEqualTo(expectedSuccessCount)
    }

    @Test
    @DisplayName("동일한 사용자가 여러 번 요청해도 1번만 발급된다")
    fun concurrentCouponIssuance_sameUser_shouldIssueOnlyOnce() {
        // Given
        val couponId = "C001"
        val userId = "duplicate_user"
        val requestCount = 10

        val successCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)

        val executor = Executors.newFixedThreadPool(10)
        val latch = CountDownLatch(requestCount)

        // When: 동일한 사용자가 10번 동시 요청
        repeat(requestCount) {
            executor.submit {
                try {
                    couponUseCase.issueCoupon(couponId, userId)
                    successCount.incrementAndGet()
                } catch (e: IllegalStateException) {
                    failureCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        // Then
        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // 1번만 성공해야 함
        assertThat(successCount.get()).isEqualTo(1)
        assertThat(failureCount.get()).isEqualTo(requestCount - 1)

        // 사용자의 쿠폰이 1개만 있어야 함
        val userCoupon = couponRepository.findUserCouponByCouponId(userId, couponId)
        assertThat(userCoupon).isNotNull
    }

    @Test
    @DisplayName("서로 다른 쿠폰은 독립적으로 발급된다")
    fun concurrentCouponIssuance_differentCoupons_shouldBeIndependent() {
        // Given
        val couponId1 = "C001" // 10개 한정
        val couponId2 = "C002" // 5개 한정
        val usersPerCoupon = 20

        val success1 = AtomicInteger(0)
        val success2 = AtomicInteger(0)

        val executor = Executors.newFixedThreadPool(40)
        val latch = CountDownLatch(usersPerCoupon * 2)

        // When: 두 쿠폰을 동시에 발급
        repeat(usersPerCoupon) { index ->
            // C001 쿠폰 발급
            executor.submit {
                try {
                    couponUseCase.issueCoupon(couponId1, "c1_user_$index")
                    success1.incrementAndGet()
                } catch (e: Exception) {
                    // 실패 무시
                } finally {
                    latch.countDown()
                }
            }

            // C002 쿠폰 발급
            executor.submit {
                try {
                    couponUseCase.issueCoupon(couponId2, "c2_user_$index")
                    success2.incrementAndGet()
                } catch (e: Exception) {
                    // 실패 무시
                } finally {
                    latch.countDown()
                }
            }
        }

        // Then
        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // 각 쿠폰이 정확히 제한 수량만큼 발급되어야 함
        assertThat(success1.get()).isEqualTo(10)
        assertThat(success2.get()).isEqualTo(5)
    }
}
