package io.hhplus.ecommerce.infrastructure.persistence.entity

import io.hhplus.ecommerce.domain.CouponType
import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * 쿠폰 JPA Entity
 */
@Entity
@Table(
    name = "coupons",
    indexes = [
        Index(name = "idx_coupons_active_valid", columnList = "is_active,valid_until DESC")
    ]
)
class CouponJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Column(nullable = false, length = 100, unique = true)
    var code: String = "",

    @Column(nullable = false, length = 255)
    var name: String = "",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: CouponType = CouponType.FIXED_AMOUNT,

    @Column(nullable = false)
    var discount: Long = 0,

    @Column(nullable = false)
    var discountRate: Int = 0,

    @Column(nullable = false)
    var minOrderAmount: Long = 0,

    @Column
    var maxDiscountAmount: Long? = null,

    @Column(nullable = false)
    var totalQuantity: Int = 0,

    @Column(nullable = false)
    var issuedQuantity: Int = 0,

    @Column(nullable = false, updatable = false)
    val startDate: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var endDate: LocalDateTime = LocalDateTime.now().plusDays(1),

    @Column(nullable = false)
    var validFrom: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var validUntil: LocalDateTime = LocalDateTime.now().plusDays(7),

    @Column(nullable = false)
    var isActive: Boolean = true,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    /**
     * 발급 가능 여부
     */
    fun canIssue(): Boolean {
        val now = LocalDateTime.now()
        return issuedQuantity < totalQuantity &&
                now.isAfter(startDate) && now.isBefore(endDate)
    }

    /**
     * 쿠폰 발급
     */
    fun issue(): Int {
        require(canIssue()) { "쿠폰을 발급할 수 없습니다" }
        issuedQuantity++
        updatedAt = LocalDateTime.now()
        return totalQuantity - issuedQuantity
    }
}

/**
 * 사용자 쿠폰 JPA Entity
 */
@Entity
@Table(
    name = "user_coupons",
    indexes = [
        Index(name = "idx_user_coupons_user_status", columnList = "user_id,status"),
        Index(name = "idx_user_coupons_valid", columnList = "is_active,valid_from,valid_until")
    ]
)
class UserCouponJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Column(nullable = false)
    var userId: Long = 0,

    @Column(nullable = false)
    var couponId: Long = 0,

    @Column(nullable = false, length = 255)
    var couponName: String = "",

    @Column(nullable = false)
    var discountRate: Int = 0,

    @Column(nullable = false, length = 50)
    var status: String = "AVAILABLE",

    @Column(nullable = false, updatable = false)
    val issuedAt: LocalDateTime = LocalDateTime.now(),

    @Column
    var usedAt: LocalDateTime? = null,

    @Column(nullable = false)
    var validFrom: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var validUntil: LocalDateTime = LocalDateTime.now().plusDays(7),

    @Column(nullable = false)
    var isActive: Boolean = true,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    /**
     * 쿠폰 유효성 확인
     */
    fun isValid(): Boolean {
        return "AVAILABLE" == status &&
                LocalDateTime.now().isBefore(validUntil)
    }

    /**
     * 쿠폰 사용
     */
    fun use() {
        require(isValid()) { "사용할 수 없는 쿠폰입니다" }
        status = "USED"
        usedAt = LocalDateTime.now()
        updatedAt = LocalDateTime.now()
    }

    /**
     * 쿠폰 만료
     */
    fun expire() {
        status = "EXPIRED"
        updatedAt = LocalDateTime.now()
    }
}
