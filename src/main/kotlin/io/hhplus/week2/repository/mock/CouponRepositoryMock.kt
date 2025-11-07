package io.hhplus.week2.repository.mock

import io.hhplus.week2.domain.Coupon
import io.hhplus.week2.domain.UserCoupon
import io.hhplus.week2.repository.CouponRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * CouponRepository의 메모리 기반 구현체
 * 테스트용 Mock 데이터로 동작합니다.
 */
@Repository
class CouponRepositoryMock : CouponRepository {

    private val coupons = ConcurrentHashMap<String, Coupon>()
    private val userCoupons = ConcurrentHashMap<String, UserCoupon>()

    init {
        initializeMockData()
    }

    private fun initializeMockData() {
        val coupon1 = Coupon(
            id = "C001",
            name = "신규 회원 10% 할인",
            discountRate = 10,
            totalQuantity = 10,
            issuedQuantity = 0,
            startDate = LocalDateTime.now().minusDays(1),
            endDate = LocalDateTime.now().plusDays(30)
        )
        coupons["C001"] = coupon1

        val coupon2 = Coupon(
            id = "C002",
            name = "여름 세일 20% 할인",
            discountRate = 20,
            totalQuantity = 5,
            issuedQuantity = 0,
            startDate = LocalDateTime.now().minusDays(1),
            endDate = LocalDateTime.now().plusDays(7)
        )
        coupons["C002"] = coupon2
    }

    override fun findById(id: String): Coupon? {
        return coupons[id]
    }

    override fun findUserCoupon(userId: String, couponId: String): UserCoupon? {
        return userCoupons.values.find { it.userId == userId && it.couponId == couponId }
    }

    override fun findUserCouponByCouponId(userId: String, couponId: String): UserCoupon? {
        return userCoupons.values.find { it.userId == userId && it.couponId == couponId }
    }

    override fun findUserCoupons(userId: String): List<UserCoupon> {
        return userCoupons.values.filter { it.userId == userId }
    }

    override fun save(coupon: Coupon) {
        coupons[coupon.id] = coupon
    }

    override fun saveUserCoupon(userCoupon: UserCoupon) {
        userCoupons["${userCoupon.userId}:${userCoupon.couponId}"] = userCoupon
    }
}
