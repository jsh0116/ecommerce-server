package io.hhplus.ecommerce.infrastructure.persistence.adapter

import io.hhplus.ecommerce.domain.User
import io.hhplus.ecommerce.infrastructure.repositories.UserRepository
import io.hhplus.ecommerce.infrastructure.persistence.repository.UserJpaRepository
import io.hhplus.ecommerce.infrastructure.persistence.entity.UserJpaEntity
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * UserRepository JPA 어댑터
 *
 * Domain User와 JPA Entity UserJpaEntity 간의 변환을 담당합니다.
 */
@Repository
class UserRepositoryAdapter(
    private val jpaRepository: UserJpaRepository
) : UserRepository {

    override fun findById(id: Long): User? {
        return jpaRepository.findById(id).map { it.toDomain() }.orElse(null)
    }

    override fun save(user: User) {
        val entity = user.toEntity()
        jpaRepository.save(entity)
    }

    /**
     * Domain User를 JPA Entity로 변환
     */
    private fun User.toEntity(): UserJpaEntity {
        return UserJpaEntity(
            id = this.id,
            balance = this.balance,
            createdAt = LocalDateTime.parse(this.createdAt, DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            updatedAt = LocalDateTime.now()
        )
    }

    /**
     * JPA Entity를 Domain User로 변환
     */
    private fun UserJpaEntity.toDomain(): User {
        return User(
            id = this.id,
            balance = this.balance,
            createdAt = this.createdAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        )
    }
}
