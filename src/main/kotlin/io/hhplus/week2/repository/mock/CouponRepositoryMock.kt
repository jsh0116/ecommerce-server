package io.hhplus.week2.repository.mock

import io.hhplus.week2.domain.Coupon
import io.hhplus.week2.domain.CouponType
import io.hhplus.week2.repository.CouponRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

/**
 * CouponRepository의 메모리 기반 구현체
 * MockData로 동작합니다.
 */
@Repository
class CouponRepositoryMock : CouponRepository {

    private val coupons = ConcurrentHashMap<String, Coupon>()
    private val dateFormatter = DateTimeFormatter.ISO_DATE_TIME

    init {
        initializeMockData()
    }

    private fun initializeMockData() {
        // 쿠폰 1: 10,000원 정액 할인
        val coupon1 = Coupon(
            id = "coupon_001",
            code = "SUMMER2024",
            name = "여름 세일 10,000원 할인",
            type = CouponType.FIXED_AMOUNT,
            discount = 10000,
            minOrderAmount = 50000,
            maxDiscountAmount = null,
            validFrom = LocalDateTime.now().minusDays(1).format(dateFormatter),
            validUntil = "2025-12-31T23:59:59Z",
            maxPerUser = 1,
            isActive = true
        )
        coupons["SUMMER2024"] = coupon1

        // 쿠폰 2: 20% 할인
        val coupon2 = Coupon(
            id = "coupon_002",
            code = "WELCOME20",
            name = "신규 회원 20% 할인",
            type = CouponType.PERCENTAGE,
            discount = 20,
            minOrderAmount = 0,
            maxDiscountAmount = 50000,
            validFrom = LocalDateTime.now().minusDays(1).format(dateFormatter),
            validUntil = "2025-12-31T23:59:59Z",
            maxPerUser = 1,
            isActive = true
        )
        coupons["WELCOME20"] = coupon2

        // 쿠폰 3: 배송비 무료
        val coupon3 = Coupon(
            id = "coupon_003",
            code = "FREESHIP",
            name = "배송비 무료 쿠폰",
            type = CouponType.FREE_SHIPPING,
            discount = 3000,
            minOrderAmount = 10000,
            maxDiscountAmount = null,
            validFrom = LocalDateTime.now().minusDays(1).format(dateFormatter),
            validUntil = "2025-12-31T23:59:59Z",
            maxPerUser = 5,
            isActive = true
        )
        coupons["FREESHIP"] = coupon3
    }

    override fun findByCode(code: String): Coupon? {
        return coupons[code]
    }

    override fun save(coupon: Coupon) {
        coupons[coupon.code] = coupon
    }

    override fun update(coupon: Coupon) {
        coupons[coupon.code] = coupon
    }
}
