package io.hhplus.week2.service

import io.hhplus.week2.domain.Coupon
import io.hhplus.week2.domain.CouponType
import io.hhplus.week2.repository.mock.CouponRepositoryMock
import io.hhplus.week2.service.impl.CouponServiceImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@DisplayName("CouponServiceImpl 테스트")
class CouponServiceTest {

    private lateinit var couponRepository: CouponRepositoryMock
    private lateinit var couponService: CouponService

    private val dateFormatter = DateTimeFormatter.ISO_DATE_TIME

    @BeforeEach
    fun setUp() {
        couponRepository = CouponRepositoryMock()
        couponService = CouponServiceImpl(couponRepository)
    }

    @Test
    @DisplayName("쿠폰 코드로 쿠폰을 조회할 수 있다")
    fun testGetCouponByCode() {
        // when
        val result = couponService.getCouponByCode("SUMMER2024")

        // then
        assertThat(result).isNotNull
        assertThat(result?.code).isEqualTo("SUMMER2024")
        assertThat(result?.name).isEqualTo("여름 세일 10,000원 할인")
    }

    @Test
    @DisplayName("존재하지 않는 쿠폰 코드로 조회하면 null을 반환한다")
    fun testGetCouponByCodeNotFound() {
        // when
        val result = couponService.getCouponByCode("NONEXISTENT")

        // then
        assertThat(result).isNull()
    }

    @Test
    @DisplayName("정액 할인 쿠폰을 검증할 수 있다")
    fun testValidateCouponFixedAmount() {
        // when
        val result = couponService.validateCoupon("SUMMER2024", 60000L)

        // then
        assertThat(result.valid).isTrue
        assertThat(result.coupon?.code).isEqualTo("SUMMER2024")
        assertThat(result.discount).isEqualTo(10000L)
    }

    @Test
    @DisplayName("비율 할인 쿠폰을 검증할 수 있다")
    fun testValidateCouponPercentage() {
        // when
        val result = couponService.validateCoupon("WELCOME20", 100000L)

        // then
        assertThat(result.valid).isTrue
        assertThat(result.discount).isEqualTo(20000L) // 100,000 * 20%
    }

    @Test
    @DisplayName("비율 할인 쿠폰의 최대 할인액을 초과하지 않는다")
    fun testValidateCouponPercentageMaxDiscount() {
        // when
        val result = couponService.validateCoupon("WELCOME20", 300000L)

        // then
        assertThat(result.valid).isTrue
        assertThat(result.discount).isEqualTo(50000L) // 최대 할인액
    }

    @Test
    @DisplayName("배송비 무료 쿠폰을 검증할 수 있다")
    fun testValidateCouponFreeShipping() {
        // when
        val result = couponService.validateCoupon("FREESHIP", 20000L)

        // then
        assertThat(result.valid).isTrue
        assertThat(result.discount).isEqualTo(3000L)
    }

    @Test
    @DisplayName("유효하지 않은 쿠폰 코드는 검증 실패")
    fun testValidateCouponInvalidCode() {
        // when
        val result = couponService.validateCoupon("INVALID", 50000L)

        // then
        assertThat(result.valid).isFalse
        assertThat(result.message).contains("유효하지 않은")
    }

    @Test
    @DisplayName("비활성화된 쿠폰은 검증 실패")
    fun testValidateCouponInactive() {
        // given - 비활성화 쿠폰을 저장
        val inactiveCoupon = Coupon(
            id = "coupon_004",
            code = "INACTIVE",
            name = "비활성 쿠폰",
            type = CouponType.FIXED_AMOUNT,
            discount = 10000,
            minOrderAmount = 50000,
            maxDiscountAmount = null,
            validFrom = LocalDateTime.now().minusDays(1).format(dateFormatter),
            validUntil = LocalDateTime.now().plusDays(90).format(dateFormatter),
            maxPerUser = 1,
            isActive = false
        )
        couponRepository.save(inactiveCoupon)

        // when
        val result = couponService.validateCoupon("INACTIVE", 50000L)

        // then
        assertThat(result.valid).isFalse
        assertThat(result.message).contains("비활성화")
    }

    @Test
    @DisplayName("유효 기간이 지난 쿠폰은 검증 실패")
    fun testValidateCouponExpired() {
        // given - 만료된 쿠폰을 저장
        val expiredCoupon = Coupon(
            id = "coupon_005",
            code = "EXPIRED",
            name = "만료된 쿠폰",
            type = CouponType.FIXED_AMOUNT,
            discount = 10000,
            minOrderAmount = 50000,
            maxDiscountAmount = null,
            validFrom = LocalDateTime.now().minusDays(10).format(dateFormatter),
            validUntil = LocalDateTime.now().minusDays(1).format(dateFormatter),
            maxPerUser = 1,
            isActive = true
        )
        couponRepository.save(expiredCoupon)

        // when
        val result = couponService.validateCoupon("EXPIRED", 50000L)

        // then
        assertThat(result.valid).isFalse
        assertThat(result.message).contains("유효 기간")
    }

    @Test
    @DisplayName("최소 주문 금액 미충족 시 검증 실패")
    fun testValidateCouponInsufficientOrderAmount() {
        // when
        val result = couponService.validateCoupon("SUMMER2024", 40000L)

        // then
        assertThat(result.valid).isFalse
        assertThat(result.message).contains("최소 주문 금액")
    }

    @Test
    @DisplayName("아직 유효 기간이 시작되지 않은 쿠폰은 검증 실패")
    fun testValidateCouponNotStarted() {
        // given - 향후 쿠폰을 저장
        val futureCoupon = Coupon(
            id = "coupon_006",
            code = "FUTURE",
            name = "미래 쿠폰",
            type = CouponType.FIXED_AMOUNT,
            discount = 10000,
            minOrderAmount = 50000,
            maxDiscountAmount = null,
            validFrom = LocalDateTime.now().plusDays(1).format(dateFormatter),
            validUntil = LocalDateTime.now().plusDays(90).format(dateFormatter),
            maxPerUser = 1,
            isActive = true
        )
        couponRepository.save(futureCoupon)

        // when
        val result = couponService.validateCoupon("FUTURE", 50000L)

        // then
        assertThat(result.valid).isFalse
        assertThat(result.message).contains("유효 기간")
    }
}
