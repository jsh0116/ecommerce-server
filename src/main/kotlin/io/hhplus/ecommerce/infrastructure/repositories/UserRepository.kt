package io.hhplus.ecommerce.infrastructure.repositories

import io.hhplus.ecommerce.domain.User

/**
 * 사용자 저장소 인터페이스
 */
interface UserRepository {
    /**
     * 사용자 단건 조회
     */
    fun findById(id: String): User?

    /**
     * 사용자 저장
     */
    fun save(user: User)
}