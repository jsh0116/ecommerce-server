package io.hhplus.ecommerce.presentation

import com.fasterxml.jackson.databind.ObjectMapper
import io.hhplus.ecommerce.application.usecases.InventoryUseCase
import io.hhplus.ecommerce.application.usecases.ReservationInfo
import io.hhplus.ecommerce.domain.Inventory
import io.hhplus.ecommerce.dto.ReserveInventoryRequest
import io.hhplus.ecommerce.dto.DeductInventoryRequest
import com.ninjasquad.springmockk.MockkBean
import io.hhplus.ecommerce.presentation.controllers.InventoryController
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.time.LocalDateTime

@WebMvcTest(InventoryController::class)
@DisplayName("InventoryController 통합 테스트")
class InventoryControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockkBean
    private lateinit var inventoryUseCase: InventoryUseCase

    @Test
    @DisplayName("재고를 조회할 수 있다")
    fun testGetInventory() {
        // Given
        val sku = "SKU-001"
        val inventory = Inventory(
            sku = sku,
            physicalStock = 100,
            reservedStock = 20,
            safetyStock = 10
        )

        every { inventoryUseCase.getInventoryBySku(sku) } returns inventory

        // When & Then
        mockMvc.perform(get("/api/v1/inventory/skus/{sku}", sku))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.sku").value(sku))
            .andExpect(jsonPath("$.physical").value(100))
            .andExpect(jsonPath("$.reserved").value(20))
            .andExpect(jsonPath("$.available").value(70)) // 100 - 20 - 10
            .andExpect(jsonPath("$.safetyStock").value(10))
            .andExpect(jsonPath("$.status").value("IN_STOCK"))
    }

    @Test
    @DisplayName("존재하지 않는 SKU 조회 시 404를 반환한다")
    fun testGetInventoryNotFound() {
        // Given
        val sku = "nonexistent"
        every { inventoryUseCase.getInventoryBySku(sku) } returns null

        // When & Then
        mockMvc.perform(get("/api/v1/inventory/skus/{sku}", sku))
            .andExpect(status().isNotFound)
    }

    @Test
    @DisplayName("재고를 예약할 수 있다")
    fun testReserveInventory() {
        // Given
        val sku = "SKU-001"
        val quantity = 5
        val reservationInfo = ReservationInfo(
            id = "res_001",
            sku = sku,
            quantity = quantity,
            expiresAt = LocalDateTime.now().plusMinutes(15).toString(),
            reservedAt = LocalDateTime.now().toString()
        )

        val request = ReserveInventoryRequest(
            sku = sku,
            quantity = quantity
        )

        every { inventoryUseCase.reserveInventory(sku, quantity, 15) } returns reservationInfo

        // When & Then
        mockMvc.perform(
            post("/api/v1/inventory/reserve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.reservationId").value("res_001"))
            .andExpect(jsonPath("$.sku").value(sku))
            .andExpect(jsonPath("$.quantity").value(quantity))
            .andExpect(jsonPath("$.success").value(true))

        verify { inventoryUseCase.reserveInventory(sku, quantity, 15) }
    }

    @Test
    @DisplayName("재고 부족 시 예약 실패하고 409를 반환한다")
    fun testReserveInventoryInsufficientStock() {
        // Given
        val sku = "SKU-001"
        val quantity = 100
        val request = ReserveInventoryRequest(
            sku = sku,
            quantity = quantity
        )

        every { inventoryUseCase.reserveInventory(sku, quantity, 15) } returns null

        // When & Then
        mockMvc.perform(
            post("/api/v1/inventory/reserve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
            .andExpect(jsonPath("$.details.sku").value(sku))
    }

    @Test
    @DisplayName("재고를 차감할 수 있다")
    fun testDeductInventory() {
        // Given
        val sku = "SKU-001"
        val quantity = 5
        val inventory = Inventory(
            sku = sku,
            physicalStock = 95,
            reservedStock = 15,
            safetyStock = 10
        )

        val request = DeductInventoryRequest(
            sku = sku,
            quantity = quantity
        )

        every { inventoryUseCase.deductInventory(sku, quantity) } returns true
        every { inventoryUseCase.getInventoryBySku(sku) } returns inventory

        // When & Then
        mockMvc.perform(
            post("/api/v1/inventory/deduct")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.sku").value(sku))
            .andExpect(jsonPath("$.remainingStock").value(70)) // 95 - 15 - 10
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("재고 차감 완료"))

        verify { inventoryUseCase.deductInventory(sku, quantity) }
    }

    @Test
    @DisplayName("재고 부족 시 차감 실패하고 409를 반환한다")
    fun testDeductInventoryInsufficientStock() {
        // Given
        val sku = "SKU-001"
        val quantity = 100
        val request = DeductInventoryRequest(
            sku = sku,
            quantity = quantity
        )

        every { inventoryUseCase.deductInventory(sku, quantity) } returns false

        // When & Then
        mockMvc.perform(
            post("/api/v1/inventory/deduct")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
    }

    @Test
    @DisplayName("예약을 취소할 수 있다")
    fun testCancelReservation() {
        // Given
        val sku = "SKU-001"
        val quantity = 5
        val inventory = Inventory(
            sku = sku,
            physicalStock = 100,
            reservedStock = 15,
            safetyStock = 10
        )

        val request = ReserveInventoryRequest(
            sku = sku,
            quantity = quantity
        )

        every { inventoryUseCase.cancelReservation(sku, quantity) } returns true
        every { inventoryUseCase.getInventoryBySku(sku) } returns inventory

        // When & Then
        mockMvc.perform(
            post("/api/v1/inventory/cancel-reservation")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value("예약 취소 완료"))
            .andExpect(jsonPath("$.sku").value(sku))
            .andExpect(jsonPath("$.availableStock").value(75)) // 100 - 15 - 10

        verify { inventoryUseCase.cancelReservation(sku, quantity) }
    }

    @Test
    @DisplayName("예약 취소 실패 시 404를 반환한다")
    fun testCancelReservationNotFound() {
        // Given
        val sku = "SKU-001"
        val quantity = 5
        val request = ReserveInventoryRequest(
            sku = sku,
            quantity = quantity
        )

        every { inventoryUseCase.cancelReservation(sku, quantity) } returns false

        // When & Then
        mockMvc.perform(
            post("/api/v1/inventory/cancel-reservation")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isNotFound)
    }
}
