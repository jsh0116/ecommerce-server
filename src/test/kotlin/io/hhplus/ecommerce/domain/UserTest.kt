package io.hhplus.ecommerce.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("User 도메인 모델 테스트")
class UserTest {

    @Nested
    @DisplayName("사용자 생성 테스트")
    inner class UserCreationTest {
        @Test
        fun `사용자를 생성할 수 있다`() {
            // Given & When
            val user = User(id = 1L, balance = 100000L, createdAt = "2024-01-01")

            // Then
            assertThat(user.id).isEqualTo(1L)
            assertThat(user.balance).isEqualTo(100000L)
            assertThat(user.createdAt).isEqualTo("2024-01-01")
        }

        @Test
        fun `다양한 잔액의 사용자를 생성할 수 있다`() {
            // Given
            val balances = listOf(0L, 50000L, 100000L, 500000L, 1000000L)

            // When & Then
            for (balance in balances) {
                val user = User(id = 1L, balance = balance, createdAt = "2024-01-01")
                assertThat(user.balance).isEqualTo(balance)
            }
        }

        @Test
        fun `0원 잔액의 사용자를 생성할 수 있다`() {
            // Given & When
            val user = User(id = 1L, balance = 0L, createdAt = "2024-01-01")

            // Then
            assertThat(user.balance).isEqualTo(0L)
        }

        @Test
        fun `많은 금액의 잔액을 가진 사용자를 생성할 수 있다`() {
            // Given & When
            val user = User(id = 1L, balance = 1000000000L, createdAt = "2024-01-01")

            // Then
            assertThat(user.balance).isEqualTo(1000000000L)
        }
    }

    @Nested
    @DisplayName("사용자 잔액 관리 테스트")
    inner class UserBalanceTest {
        @Test
        fun `사용자의 잔액을 확인할 수 있다`() {
            // Given
            val user = User(id = 1L, balance = 50000L, createdAt = "2024-01-01")

            // Then
            assertThat(user.balance).isEqualTo(50000L)
        }

        @Test
        fun `사용자의 잔액을 변경할 수 있다`() {
            // Given
            val user = User(id = 1L, balance = 50000L, createdAt = "2024-01-01")

            // When
            user.balance = 75000L

            // Then
            assertThat(user.balance).isEqualTo(75000L)
        }

        @Test
        fun `여러 번 잔액 변경을 수행할 수 있다`() {
            // Given
            val user = User(id = 1L, balance = 100000L, createdAt = "2024-01-01")

            // When
            user.balance = 80000L  // 20000 사용
            user.balance = 130000L // 50000 충전
            user.balance = 110000L // 20000 사용

            // Then
            assertThat(user.balance).isEqualTo(110000L)
        }
    }

    @Nested
    @DisplayName("사용자 정보 테스트")
    inner class UserInformationTest {
        @Test
        fun `사용자의 ID를 확인할 수 있다`() {
            // Given & When
            val user = User(id = 12345L, balance = 50000L, createdAt = "2024-01-01")

            // Then
            assertThat(user.id).isEqualTo(12345L)
        }

        @Test
        fun `사용자의 생성 날짜를 확인할 수 있다`() {
            // Given & When
            val createdAt = "2024-11-14T10:30:00"
            val user = User(id = 1L, balance = 50000L, createdAt = createdAt)

            // Then
            assertThat(user.createdAt).isEqualTo(createdAt)
        }

        @Test
        fun `다양한 생성 날짜 형식을 저장할 수 있다`() {
            // Given
            val dates = listOf("2024-01-01", "2024-11-14", "2024-12-31")

            // When & Then
            for (date in dates) {
                val user = User(id = 1L, balance = 50000L, createdAt = date)
                assertThat(user.createdAt).isEqualTo(date)
            }
        }
    }
}
