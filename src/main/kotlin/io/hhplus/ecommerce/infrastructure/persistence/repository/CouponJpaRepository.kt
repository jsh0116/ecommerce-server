package io.hhplus.ecommerce.infrastructure.persistence.repository

import io.hhplus.ecommerce.infrastructure.persistence.entity.CouponJpaEntity
import io.hhplus.ecommerce.infrastructure.persistence.entity.UserCouponJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional

/**
 * 쿠폰 JPA Repository
 */
@Repository
interface CouponJpaRepository : JpaRepository<CouponJpaEntity, Long> {

    /**
     * 쿠폰 ID로 조회
     */
    override fun findById(id: Long): Optional<CouponJpaEntity>

    /**
     * 쿠폰 코드로 조회
     */
    fun findByCode(code: String): CouponJpaEntity?
}

/**
 * 사용자 쿠폰 JPA Repository
 */
@Repository
interface UserCouponJpaRepository : JpaRepository<UserCouponJpaEntity, Long> {

    /**
     * 사용자별, 쿠폰별 사용자 쿠폰 조회
     */
    fun findByUserIdAndCouponId(userId: Long, couponId: Long): UserCouponJpaEntity?

    /**
     * 사용자별 모든 쿠폰 조회
     */
    fun findByUserId(userId: Long): List<UserCouponJpaEntity>

    /**
     * 사용자별, 상태별 쿠폰 조회
     */
    fun findByUserIdAndStatus(userId: Long, status: String): List<UserCouponJpaEntity>

    /**
     * 만료된 쿠폰 조회
     */
    @Query("""
        SELECT u FROM UserCouponJpaEntity u
        WHERE u.status = 'AVAILABLE'
        AND u.validUntil <= CURRENT_TIMESTAMP
    """)
    fun findExpiredCoupons(): List<UserCouponJpaEntity>
}