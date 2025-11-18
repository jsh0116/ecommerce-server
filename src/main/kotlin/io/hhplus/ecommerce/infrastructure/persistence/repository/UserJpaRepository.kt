package io.hhplus.ecommerce.infrastructure.persistence.repository

import io.hhplus.ecommerce.infrastructure.persistence.entity.UserJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

/**
 * 사용자 JPA Repository
 */
@Repository
interface UserJpaRepository : JpaRepository<UserJpaEntity, Long> {

    /**
     * 사용자 ID로 조회
     */
    override fun findById(id: Long): Optional<UserJpaEntity>
}