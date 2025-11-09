package io.hhplus.week2.repository

/**
 * 사용자 도메인 모델
 */
data class User(
    val id: String,
    var balance: Long,
    val createdAt: String
)

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