package io.hhplus.ecommerce.presentation.controllers

import io.hhplus.ecommerce.infrastructure.persistence.entity.DataTransmissionLogJpaEntity
import io.hhplus.ecommerce.infrastructure.persistence.entity.TransmissionStatus
import io.hhplus.ecommerce.infrastructure.persistence.repository.DataTransmissionLogJpaRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("주문 외부 전송 상태 조회 API 테스트")
class OrderControllerTransmissionTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var transmissionLogRepository: DataTransmissionLogJpaRepository

    @BeforeEach
    fun setUp() {
        transmissionLogRepository.deleteAll()
    }

    @Test
    @DisplayName("GET /api/v1/orders/{orderId}/transmission-status - 전송 대기 중 상태 조회")
    fun getTransmissionStatus_pending() {
        // Given
        val orderId = 1L
        val log = DataTransmissionLogJpaEntity(
            orderId = orderId,
            userId = 100L,
            status = TransmissionStatus.PENDING
        )
        transmissionLogRepository.save(log)

        // When & Then
        mockMvc.perform(get("/api/v1/orders/$orderId/transmission-status"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.orderId").value(orderId))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.retryCount").value(0))
            .andExpect(jsonPath("$.completedAt").doesNotExist())
    }

    @Test
    @DisplayName("GET /api/v1/orders/{orderId}/transmission-status - 전송 성공 상태 조회")
    fun getTransmissionStatus_success() {
        // Given
        val orderId = 2L
        val log = DataTransmissionLogJpaEntity(
            orderId = orderId,
            userId = 100L,
            status = TransmissionStatus.SUCCESS,
            completedAt = LocalDateTime.now()
        )
        transmissionLogRepository.save(log)

        // When & Then
        mockMvc.perform(get("/api/v1/orders/$orderId/transmission-status"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.orderId").value(orderId))
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.completedAt").exists())
    }

    @Test
    @DisplayName("GET /api/v1/orders/{orderId}/transmission-status - 전송 실패 상태 조회")
    fun getTransmissionStatus_failed() {
        // Given
        val orderId = 3L
        val errorMessage = "외부 API 타임아웃"
        val log = DataTransmissionLogJpaEntity(
            orderId = orderId,
            userId = 100L,
            status = TransmissionStatus.FAILED,
            errorMessage = errorMessage,
            retryCount = 1
        )
        transmissionLogRepository.save(log)

        // When & Then
        mockMvc.perform(get("/api/v1/orders/$orderId/transmission-status"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.orderId").value(orderId))
            .andExpect(jsonPath("$.status").value("FAILED"))
            .andExpect(jsonPath("$.errorMessage").value(errorMessage))
            .andExpect(jsonPath("$.retryCount").value(1))
    }

    @Test
    @DisplayName("GET /api/v1/orders/{orderId}/transmission-status - 존재하지 않는 주문은 404")
    fun getTransmissionStatus_notFound() {
        // Given
        val orderId = 9999L

        // When & Then
        mockMvc.perform(get("/api/v1/orders/$orderId/transmission-status"))
            .andExpect(status().isNotFound)
    }
}
