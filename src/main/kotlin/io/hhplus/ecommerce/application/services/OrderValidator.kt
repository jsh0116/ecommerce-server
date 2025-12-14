package io.hhplus.ecommerce.application.services

import io.hhplus.ecommerce.domain.Product
import io.hhplus.ecommerce.domain.User
import io.hhplus.ecommerce.domain.UserCoupon
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 주문 검증 컴포넌트
 *
 * 주문 생성 및 취소 프로세스의 모든 검증 로직을 담당합니다.
 * Single Responsibility: 검증 로직만 집중
 */
@Component
class OrderValidator(
    private val userService: UserService,
    private val productService: ProductService,
    private val couponService: CouponService
) {
    companion object {
        private val logger = LoggerFactory.getLogger(OrderValidator::class.java)
    }

    /**
     * 사용자 존재 검증
     *
     * @param userId 사용자 ID
     * @return 검증된 사용자
     * @throws UserException.UserNotFound 사용자가 없는 경우
     */
    fun validateUser(userId: Long): User {
        return userService.getById(userId)
    }

    /**
     * 상품 존재 검증
     *
     * @param productId 상품 ID
     * @return 검증된 상품
     * @throws ProductException.ProductNotFound 상품이 없는 경우
     */
    fun validateProduct(productId: Long): Product {
        return productService.getById(productId)
    }

    /**
     * 쿠폰 유효성 검증
     *
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID (nullable)
     * @return 검증된 사용자 쿠폰 (없으면 null)
     * @throws CouponException 쿠폰이 유효하지 않은 경우
     */
    fun validateCoupon(userId: Long, couponId: Long?): UserCoupon? {
        return couponId?.let {
            couponService.validateUserCoupon(userId, it)
        }
    }
}
