package io.hhplus.ecommerce.presentation

import com.fasterxml.jackson.databind.ObjectMapper
import io.hhplus.ecommerce.application.usecases.OrderUseCase
import io.hhplus.ecommerce.application.usecases.ProductUseCase
import io.hhplus.ecommerce.application.usecases.InventoryUseCase
import io.hhplus.ecommerce.domain.*
import io.hhplus.ecommerce.dto.CreateOrderRequest
import io.hhplus.ecommerce.dto.OrderItemRequest
import io.hhplus.ecommerce.dto.ShippingAddressRequest
import io.hhplus.ecommerce.dto.CancelOrderRequest
import com.ninjasquad.springmockk.MockkBean
import io.hhplus.ecommerce.presentation.controllers.OrderController
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

@WebMvcTest(OrderController::class)
@DisplayName("OrderController 통합 테스트")
class OrderControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockkBean
    private lateinit var orderUseCase: OrderUseCase

    @MockkBean
    private lateinit var productUseCase: ProductUseCase

    @MockkBean
    private lateinit var inventoryUseCase: InventoryUseCase

    @Test
    @DisplayName("주문을 생성할 수 있다")
    fun testCreateOrder() {
        // Given
        val productId = "prod1"
        val product = Product(
            id = productId,
            name = "노트북",
            description = "고성능 노트북",
            price = 1_000_000L,
            category = "전자제품"
        )
        val inventory = Inventory(
            sku = productId,
            physicalStock = 100,
            reservedStock = 20,
            safetyStock = 10
        )
        val order = Order(
            id = "order1",
            userId = "user1",
            items = listOf(
                OrderItem(
                    productId = productId,
                    productName = "노트북",
                    quantity = 1,
                    unitPrice = 1_000_000L,
                    subtotal = 1_000_000L
                )
            ),
            totalAmount = 1_000_000L,
            discountAmount = 0L,
            finalAmount = 1_003_000L
        )

        val request = CreateOrderRequest(
            items = listOf(
                OrderItemRequest(variantId = productId, quantity = 1)
            ),
            shippingAddress = ShippingAddressRequest(
                name = "홍길동",
                phone = "010-1234-5678",
                address = "서울시 강남구",
                addressDetail = "테헤란로 123",
                zipCode = "06234"
            ),
            shippingMethod = "standard",
            couponCode = null,
            pointsToUse = 0L,
            agreeToTerms = true
        )

        every { productUseCase.getProductById(productId) } returns product
        every { inventoryUseCase.getInventoryBySku(productId) } returns inventory
        every { orderUseCase.createOrder(any(), any(), any()) } returns order

        // When & Then
        mockMvc.perform(
            post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value("order1"))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.items[0].productName").value("노트북"))

        verify { orderUseCase.createOrder(any(), any(), any()) }
    }

    @Test
    @DisplayName("빈 주문 아이템으로 주문 생성 시 400 에러를 반환한다")
    fun testCreateOrderWithEmptyItems() {
        // Given
        val request = CreateOrderRequest(
            items = emptyList(),
            shippingAddress = ShippingAddressRequest(
                name = "홍길동",
                phone = "010-1234-5678",
                address = "서울시 강남구",
                addressDetail = "테헤란로 123",
                zipCode = "06234"
            ),
            shippingMethod = "standard",
            couponCode = null,
            pointsToUse = 0L,
            agreeToTerms = true
        )

        // When & Then
        mockMvc.perform(
            post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_ITEMS"))
    }

    @Test
    @DisplayName("약관 미동의 시 400 에러를 반환한다")
    fun testCreateOrderWithoutAgreement() {
        // Given
        val request = CreateOrderRequest(
            items = listOf(
                OrderItemRequest(variantId = "prod1", quantity = 1)
            ),
            shippingAddress = ShippingAddressRequest(
                name = "홍길동",
                phone = "010-1234-5678",
                address = "서울시 강남구",
                addressDetail = "테헤란로 123",
                zipCode = "06234"
            ),
            shippingMethod = "standard",
            couponCode = null,
            pointsToUse = 0L,
            agreeToTerms = false
        )

        // When & Then
        mockMvc.perform(
            post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("MUST_AGREE_TERMS"))
    }

    @Test
    @DisplayName("재고 부족 시 409 에러를 반환한다")
    fun testCreateOrderWithInsufficientStock() {
        // Given
        val productId = "prod1"
        val product = Product(
            id = productId,
            name = "노트북",
            description = "고성능 노트북",
            price = 1_000_000L,
            category = "전자제품"
        )
        val inventory = Inventory(
            sku = productId,
            physicalStock = 100,
            reservedStock = 95,
            safetyStock = 10
        )

        val request = CreateOrderRequest(
            items = listOf(
                OrderItemRequest(variantId = productId, quantity = 10)
            ),
            shippingAddress = ShippingAddressRequest(
                name = "홍길동",
                phone = "010-1234-5678",
                address = "서울시 강남구",
                addressDetail = "테헤란로 123",
                zipCode = "06234"
            ),
            shippingMethod = "standard",
            couponCode = null,
            pointsToUse = 0L,
            agreeToTerms = true
        )

        every { productUseCase.getProductById(productId) } returns product
        every { inventoryUseCase.getInventoryBySku(productId) } returns inventory

        // When & Then
        mockMvc.perform(
            post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
    }

    @Test
    @DisplayName("주문을 조회할 수 있다")
    fun testGetOrder() {
        // Given
        val orderId = "order1"
        val order = Order(
            id = orderId,
            userId = "user1",
            items = listOf(
                OrderItem(
                    productId = "prod1",
                    productName = "노트북",
                    quantity = 1,
                    unitPrice = 1_000_000L,
                    subtotal = 1_000_000L
                )
            ),
            totalAmount = 1_000_000L,
            discountAmount = 0L,
            finalAmount = 1_000_000L
        )

        every { orderUseCase.getOrderById(orderId) } returns order

        // When & Then
        mockMvc.perform(get("/api/v1/orders/{orderId}", orderId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(orderId))
            .andExpect(jsonPath("$.items[0].productName").value("노트북"))
    }

    @Test
    @DisplayName("존재하지 않는 주문 조회 시 404를 반환한다")
    fun testGetOrderNotFound() {
        // Given
        val orderId = "nonexistent"
        every { orderUseCase.getOrderById(orderId) } returns null

        // When & Then
        mockMvc.perform(get("/api/v1/orders/{orderId}", orderId))
            .andExpect(status().isNotFound)
    }

    @Test
    @DisplayName("주문을 취소할 수 있다")
    fun testCancelOrder() {
        // Given
        val orderId = "order1"
        val cancelledOrder = Order(
            id = orderId,
            userId = "user1",
            items = listOf(),
            totalAmount = 1_000_000L,
            discountAmount = 0L,
            finalAmount = 1_000_000L,
            status = "CANCELLED"
        )

        val request = CancelOrderRequest(
            reason = "단순 변심",
            detailReason = null
        )

        every { orderUseCase.cancelOrder(orderId, any()) } returns cancelledOrder

        // When & Then
        mockMvc.perform(
            post("/api/v1/orders/{orderId}/cancel", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value("주문이 취소되었습니다."))
            .andExpect(jsonPath("$.refundAmount").value(1_000_000L))

        verify { orderUseCase.cancelOrder(orderId, any()) }
    }

    @Test
    @DisplayName("취소할 수 없는 주문 상태 시 400 에러를 반환한다")
    fun testCancelOrderInvalidState() {
        // Given
        val orderId = "order1"
        val request = CancelOrderRequest(
            reason = "단순 변심",
            detailReason = null
        )

        every { orderUseCase.cancelOrder(orderId, any()) } throws IllegalStateException("취소할 수 없는 주문입니다")

        // When & Then
        mockMvc.perform(
            post("/api/v1/orders/{orderId}/cancel", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("CANNOT_CANCEL"))
    }
}
