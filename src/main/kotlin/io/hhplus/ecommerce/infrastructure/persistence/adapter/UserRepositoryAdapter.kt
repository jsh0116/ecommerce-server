package io.hhplus.ecommerce.infrastructure.persistence.adapter

import io.hhplus.ecommerce.domain.User
import io.hhplus.ecommerce.infrastructure.repositories.UserRepository
import org.springframework.stereotype.Repository

/**
 * UserRepository JPA 어댑터
 */
@Repository
class UserRepositoryAdapter : UserRepository {

    override fun findById(id: Long): User? {
        // TODO: 구현 필요
        return null
    }

    override fun save(user: User) {
        // TODO: 구현 필요
    }
}
