package io.hhplus.ecommerce.application.services

import io.hhplus.ecommerce.domain.User
import io.hhplus.ecommerce.exception.UserException
import io.hhplus.ecommerce.infrastructure.repositories.UserRepository
import org.springframework.stereotype.Service

@Service
class UserService(
    private val userRepository: UserRepository
) {
    fun getById(userId: Long): User {
        return userRepository.findById(userId)
            ?: throw UserException.UserNotFound(userId.toString())
    }

    fun chargeBalance(userId: Long, amount: Long): User {
        val user = getById(userId)
        user.charge(amount)
        userRepository.save(user)
        return user
    }

    fun deductBalance(userId: Long, amount: Long): User {
        val user = getById(userId)
        user.pay(amount)
        userRepository.save(user)
        return user
    }
}
