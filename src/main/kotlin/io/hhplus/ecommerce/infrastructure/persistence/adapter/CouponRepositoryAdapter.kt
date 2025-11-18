package io.hhplus.ecommerce.infrastructure.persistence.adapter

import io.hhplus.ecommerce.domain.Coupon
import io.hhplus.ecommerce.domain.CouponType
import io.hhplus.ecommerce.domain.UserCoupon
import io.hhplus.ecommerce.infrastructure.repositories.CouponRepository
import io.hhplus.ecommerce.infrastructure.persistence.repository.CouponJpaRepository
import io.hhplus.ecommerce.infrastructure.persistence.repository.UserCouponJpaRepository
import io.hhplus.ecommerce.infrastructure.persistence.entity.CouponJpaEntity
import io.hhplus.ecommerce.infrastructure.persistence.entity.UserCouponJpaEntity
import org.springframework.stereotype.Repository

/**
 * CouponRepository JPA 어댑터
 *
 * Domain Coupon/UserCoupon과 JPA Entity 간의 변환을 담당합니다.
 */
@Repository
class CouponRepositoryAdapter(
    private val couponJpaRepository: CouponJpaRepository,
    private val userCouponJpaRepository: UserCouponJpaRepository
) : CouponRepository {

    override fun findById(id: Long): Coupon? {
        return couponJpaRepository.findById(id).map { it.toDomain() }.orElse(null)
    }

    override fun save(coupon: Coupon) {
        val entity = coupon.toEntity()
        couponJpaRepository.save(entity)
    }

    override fun findUserCoupon(userId: Long, couponId: Long): UserCoupon? {
        return userCouponJpaRepository.findByUserIdAndCouponId(userId, couponId)?.toDomain()
    }

    override fun saveUserCoupon(userCoupon: UserCoupon) {
        val entity = userCoupon.toEntity()
        userCouponJpaRepository.save(entity)
    }

    override fun findUserCouponByCouponId(userId: Long, couponId: Long): UserCoupon? {
        return userCouponJpaRepository.findByUserIdAndCouponId(userId, couponId)?.toDomain()
    }

    override fun findUserCoupons(userId: Long): List<UserCoupon> {
        return userCouponJpaRepository.findByUserId(userId).map { it.toDomain() }
    }

    /**
     * Domain Coupon를 JPA Entity로 변환
     */
    private fun Coupon.toEntity(): CouponJpaEntity {
        return CouponJpaEntity(
            id = this.id,
            code = this.code,
            name = this.name,
            type = this.type,
            discount = this.discount,
            discountRate = this.discountRate,
            minOrderAmount = this.minOrderAmount,
            maxDiscountAmount = this.maxDiscountAmount,
            totalQuantity = this.totalQuantity,
            issuedQuantity = this.issuedQuantity,
            startDate = this.startDate,
            endDate = this.endDate,
            isActive = this.isActive
        )
    }

    /**
     * JPA Entity를 Domain Coupon로 변환
     */
    private fun CouponJpaEntity.toDomain(): Coupon {
        return Coupon(
            id = this.id,
            code = this.code,
            name = this.name,
            type = this.type,
            discount = this.discount,
            discountRate = this.discountRate,
            minOrderAmount = this.minOrderAmount,
            maxDiscountAmount = this.maxDiscountAmount,
            totalQuantity = this.totalQuantity,
            issuedQuantity = this.issuedQuantity,
            startDate = this.startDate,
            endDate = this.endDate,
            isActive = this.isActive
        )
    }

    /**
     * Domain UserCoupon를 JPA Entity로 변환
     */
    private fun UserCoupon.toEntity(): UserCouponJpaEntity {
        return UserCouponJpaEntity(
            userId = this.userId,
            couponId = this.couponId,
            couponName = this.couponName,
            discountRate = this.discountRate,
            status = this.status,
            issuedAt = this.issuedAt,
            usedAt = this.usedAt,
            validUntil = this.expiresAt
        )
    }

    /**
     * JPA Entity를 Domain UserCoupon로 변환
     */
    private fun UserCouponJpaEntity.toDomain(): UserCoupon {
        return UserCoupon(
            userId = this.userId,
            couponId = this.couponId,
            couponName = this.couponName,
            discountRate = this.discountRate,
            status = this.status,
            issuedAt = this.issuedAt,
            usedAt = this.usedAt,
            expiresAt = this.validUntil
        )
    }
}
