package io.hhplus.week2.repository.impl

import io.hhplus.week2.domain.Coupon
import io.hhplus.week2.domain.CouponType
import io.hhplus.week2.repository.CouponRepository
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

/**
 * CouponRepository의 실제 구현체
 */
@Repository
@Primary
class CouponRepositoryImpl : CouponRepository {

    private val coupons = ConcurrentHashMap<String, Coupon>()

    override fun findByCode(code: String): Coupon? {
        TODO("Not yet implemented")
    }

    override fun save(coupon: Coupon) {
        TODO("Not yet implemented")
    }

    override fun update(coupon: Coupon) {
        TODO("Not yet implemented")
    }
}
