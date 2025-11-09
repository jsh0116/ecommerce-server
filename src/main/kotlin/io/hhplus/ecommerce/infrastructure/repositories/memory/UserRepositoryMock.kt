package io.hhplus.ecommerce.infrastructure.repositories.memory

import io.hhplus.ecommerce.domain.User
import io.hhplus.ecommerce.infrastructure.repositories.UserRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * UserRepository의 메모리 기반 구현체
 * 테스트용 Mock 데이터로 동작합니다.
 */
@Repository
class UserRepositoryMock : UserRepository {

    private val users = ConcurrentHashMap<String, User>()

    init {
        initializeMockData()
    }

    private fun initializeMockData() {
        val user1 = User(
            id = "user1",
            balance = 5000000,
            createdAt = LocalDateTime.now().toString()
        )
        users["user1"] = user1

        val user2 = User(
            id = "user2",
            balance = 100000,
            createdAt = LocalDateTime.now().toString()
        )
        users["user2"] = user2

        val user3 = User(
            id = "user3",
            balance = 1000000,
            createdAt = LocalDateTime.now().toString()
        )
        users["user3"] = user3
    }

    override fun findById(id: String): User? {
        // 동적 사용자 생성 (테스트 편의성을 위해)
        return users.computeIfAbsent(id) {
            User(
                id = it,
                balance = 5000000,
                createdAt = LocalDateTime.now().toString()
            )
        }
    }

    override fun save(user: User) {
        users[user.id] = user
    }
}