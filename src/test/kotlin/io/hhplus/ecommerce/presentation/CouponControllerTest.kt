package io.hhplus.ecommerce.presentation

import com.fasterxml.jackson.databind.ObjectMapper
import io.hhplus.ecommerce.application.usecases.CouponUseCase
import io.hhplus.ecommerce.domain.Coupon
import io.hhplus.ecommerce.domain.CouponType
import io.hhplus.ecommerce.domain.CouponValidationResult
import io.hhplus.ecommerce.dto.ValidateCouponRequest
import com.ninjasquad.springmockk.MockkBean
import io.hhplus.ecommerce.presentation.controllers.CouponController
import io.mockk.every
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.time.LocalDateTime

@WebMvcTest(CouponController::class)
@DisplayName("CouponController 통합 테스트")
class CouponControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockkBean
    private lateinit var couponUseCase: CouponUseCase

    @Test
    @DisplayName("유효한 쿠폰을 검증할 수 있다")
    fun testValidateCoupon() {
        // Given
        val couponCode = "SUMMER2024"
        val orderAmount = 100_000L
        val coupon = Coupon(
            id = "coupon1",
            code = couponCode,
            name = "여름 세일",
            type = CouponType.FIXED_AMOUNT,
            discount = 10_000L,
            discountRate = 0,
            minOrderAmount = 50_000L,
            totalQuantity = 100,
            issuedQuantity = 50,
            startDate = LocalDateTime.now().minusDays(1),
            endDate = LocalDateTime.now().plusDays(30),
            validFrom = "2024-06-01T00:00:00Z",
            validUntil = "2025-12-31T23:59:59Z"
        )

        val validationResult = CouponValidationResult(
            valid = true,
            coupon = coupon,
            discount = 10_000L,
            message = "쿠폰이 적용되었습니다"
        )

        val request = ValidateCouponRequest(
            couponCode = couponCode,
            orderAmount = orderAmount
        )

        every { couponUseCase.validateCoupon(couponCode, orderAmount) } returns validationResult

        // When & Then
        mockMvc.perform(
            post("/api/v1/coupons/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.valid").value(true))
            .andExpect(jsonPath("$.discount").value(10_000L))
            .andExpect(jsonPath("$.coupon.code").value(couponCode))
            .andExpect(jsonPath("$.coupon.name").value("여름 세일"))
    }

    @Test
    @DisplayName("유효하지 않은 쿠폰 검증 시 valid=false를 반환한다")
    fun testValidateInvalidCoupon() {
        // Given
        val couponCode = "INVALID"
        val orderAmount = 100_000L

        val validationResult = CouponValidationResult(
            valid = false,
            coupon = null,
            discount = 0L,
            message = "유효하지 않은 쿠폰입니다"
        )

        val request = ValidateCouponRequest(
            couponCode = couponCode,
            orderAmount = orderAmount
        )

        every { couponUseCase.validateCoupon(couponCode, orderAmount) } returns validationResult

        // When & Then
        mockMvc.perform(
            post("/api/v1/coupons/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.valid").value(false))
            .andExpect(jsonPath("$.discount").value(0))
            .andExpect(jsonPath("$.coupon").isEmpty)
    }

    @Test
    @DisplayName("최소 주문 금액 미만 시 쿠폰 검증 실패")
    fun testValidateCouponBelowMinAmount() {
        // Given
        val couponCode = "SUMMER2024"
        val orderAmount = 10_000L

        val validationResult = CouponValidationResult(
            valid = false,
            coupon = null,
            discount = 0L,
            message = "최소 주문 금액을 충족하지 못했습니다"
        )

        val request = ValidateCouponRequest(
            couponCode = couponCode,
            orderAmount = orderAmount
        )

        every { couponUseCase.validateCoupon(couponCode, orderAmount) } returns validationResult

        // When & Then
        mockMvc.perform(
            post("/api/v1/coupons/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.valid").value(false))
            .andExpect(jsonPath("$.message").value("최소 주문 금액을 충족하지 못했습니다"))
    }

    @Test
    @DisplayName("쿠폰 목록을 조회할 수 있다")
    fun testGetCoupons() {
        // When & Then
        mockMvc.perform(get("/api/v1/coupons"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data").isArray)
            .andExpect(jsonPath("$.data[0].code").value("SUMMER2024"))
            .andExpect(jsonPath("$.data[1].code").value("WELCOME20"))
            .andExpect(jsonPath("$.data[2].code").value("FREESHIP"))
            .andExpect(jsonPath("$.pagination.total").value(3))
    }

    @Test
    @DisplayName("페이지네이션 파라미터로 쿠폰 목록을 조회할 수 있다")
    fun testGetCouponsWithPagination() {
        // When & Then
        mockMvc.perform(
            get("/api/v1/coupons")
                .param("page", "2")
                .param("limit", "10")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.pagination.page").value(2))
            .andExpect(jsonPath("$.pagination.limit").value(10))
    }

    @Test
    @DisplayName("비율 할인 쿠폰을 검증할 수 있다")
    fun testValidatePercentageCoupon() {
        // Given
        val couponCode = "WELCOME20"
        val orderAmount = 100_000L
        val coupon = Coupon(
            id = "coupon2",
            code = couponCode,
            name = "신규 회원 20% 할인",
            type = CouponType.PERCENTAGE,
            discount = 0L,
            discountRate = 20,
            minOrderAmount = 0L,
            maxDiscountAmount = 50_000L,
            totalQuantity = 100,
            issuedQuantity = 30,
            startDate = LocalDateTime.now().minusDays(1),
            endDate = LocalDateTime.now().plusDays(30),
            validFrom = "2024-06-01T00:00:00Z",
            validUntil = "2025-12-31T23:59:59Z"
        )

        val validationResult = CouponValidationResult(
            valid = true,
            coupon = coupon,
            discount = 20_000L, // 100,000 * 20%
            message = "쿠폰이 적용되었습니다"
        )

        val request = ValidateCouponRequest(
            couponCode = couponCode,
            orderAmount = orderAmount
        )

        every { couponUseCase.validateCoupon(couponCode, orderAmount) } returns validationResult

        // When & Then
        mockMvc.perform(
            post("/api/v1/coupons/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.valid").value(true))
            .andExpect(jsonPath("$.discount").value(20_000L))
            .andExpect(jsonPath("$.coupon.type").value("PERCENTAGE"))
    }

    @Test
    @DisplayName("무료 배송 쿠폰을 검증할 수 있다")
    fun testValidateFreeShippingCoupon() {
        // Given
        val couponCode = "FREESHIP"
        val orderAmount = 50_000L
        val coupon = Coupon(
            id = "coupon3",
            code = couponCode,
            name = "배송비 무료 쿠폰",
            type = CouponType.FREE_SHIPPING,
            discount = 3_000L,
            discountRate = 0,
            minOrderAmount = 10_000L,
            totalQuantity = 1000,
            issuedQuantity = 200,
            startDate = LocalDateTime.now().minusDays(1),
            endDate = LocalDateTime.now().plusDays(30),
            validFrom = "2024-06-01T00:00:00Z",
            validUntil = "2025-12-31T23:59:59Z"
        )

        val validationResult = CouponValidationResult(
            valid = true,
            coupon = coupon,
            discount = 3_000L,
            message = "쿠폰이 적용되었습니다"
        )

        val request = ValidateCouponRequest(
            couponCode = couponCode,
            orderAmount = orderAmount
        )

        every { couponUseCase.validateCoupon(couponCode, orderAmount) } returns validationResult

        // When & Then
        mockMvc.perform(
            post("/api/v1/coupons/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.valid").value(true))
            .andExpect(jsonPath("$.discount").value(3_000L))
            .andExpect(jsonPath("$.coupon.type").value("FREE_SHIPPING"))
    }
}
