package io.hhplus.ecommerce.domain

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("Order 도메인 테스트")
class OrderTest {

    @Test
    @DisplayName("주문을 생성할 수 있다")
    fun testCreateOrder() {
        // Given
        val product = Product(
            id = "P001",
            name = "노트북",
            description = "고급 노트북",
            price = 1_000_000L,
            category = "전자제품"
        )
        val item = OrderItem.create(product, 2)
        val items = listOf(item)

        // When
        val order = Order(
            id = "ORD001",
            userId = "USER1",
            items = items,
            totalAmount = 2_000_000L,
            discountAmount = 0L,
            finalAmount = 2_000_000L
        )

        // Then
        assert(order.id == "ORD001")
        assert(order.userId == "USER1")
        assert(order.items.size == 1)
        assert(order.totalAmount == 2_000_000L)
        assert(order.status == "PENDING")
    }

    @Test
    @DisplayName("주문 항목을 생성할 수 있다")
    fun testCreateOrderItem() {
        // Given
        val product = Product(
            id = "P001",
            name = "노트북",
            description = "고급 노트북",
            price = 1_000_000L,
            category = "전자제품"
        )

        // When
        val item = OrderItem.create(product, 2)

        // Then
        assert(item.productId == "P001")
        assert(item.productName == "노트북")
        assert(item.quantity == 2)
        assert(item.unitPrice == 1_000_000L)
        assert(item.subtotal == 2_000_000L)
    }

    @Test
    @DisplayName("주문이 결제 가능한 상태를 확인할 수 있다")
    fun testCanPay() {
        // Given
        val product = Product(
            id = "P001",
            name = "노트북",
            description = "고급 노트북",
            price = 1_000_000L,
            category = "전자제품"
        )
        val item = OrderItem.create(product, 2)
        val order = Order(
            id = "ORD001",
            userId = "USER1",
            items = listOf(item),
            totalAmount = 2_000_000L,
            discountAmount = 0L,
            finalAmount = 2_000_000L
        )

        // When & Then
        assert(order.canPay())
    }

    @Test
    @DisplayName("주문 완료 처리를 할 수 있다")
    fun testCompleteOrder() {
        // Given
        val product = Product(
            id = "P001",
            name = "노트북",
            description = "고급 노트북",
            price = 1_000_000L,
            category = "전자제품"
        )
        val item = OrderItem.create(product, 2)
        val order = Order(
            id = "ORD001",
            userId = "USER1",
            items = listOf(item),
            totalAmount = 2_000_000L,
            discountAmount = 0L,
            finalAmount = 2_000_000L
        )

        // When
        order.complete()

        // Then
        assert(order.status == "PAID")
        assert(order.paidAt != null)
    }

    @Test
    @DisplayName("결제할 수 없는 상태에서는 완료 처리가 실패한다")
    fun testCompleteOrderWhenCannotPay() {
        // Given
        val product = Product(
            id = "P001",
            name = "노트북",
            description = "고급 노트북",
            price = 1_000_000L,
            category = "전자제품"
        )
        val item = OrderItem.create(product, 2)
        val order = Order(
            id = "ORD001",
            userId = "USER1",
            items = listOf(item),
            totalAmount = 2_000_000L,
            discountAmount = 0L,
            finalAmount = 2_000_000L,
            status = "PAID"
        )

        // When & Then
        val exception = assertThrows<IllegalStateException> {
            order.complete()
        }
        assert(exception.message?.contains("결제할 수 없는 주문") ?: false)
    }

    @Test
    @DisplayName("주문을 취소할 수 있다")
    fun testCancelOrder() {
        // Given
        val product = Product(
            id = "P001",
            name = "노트북",
            description = "고급 노트북",
            price = 1_000_000L,
            category = "전자제품"
        )
        val item = OrderItem.create(product, 2)
        val order = Order(
            id = "ORD001",
            userId = "USER1",
            items = listOf(item),
            totalAmount = 2_000_000L,
            discountAmount = 0L,
            finalAmount = 2_000_000L
        )

        // When
        order.cancel()

        // Then
        assert(order.status == "CANCELLED")
    }

    @Test
    @DisplayName("결제 완료된 주문은 취소할 수 없다")
    fun testCancelPaidOrder() {
        // Given
        val product = Product(
            id = "P001",
            name = "노트북",
            description = "고급 노트북",
            price = 1_000_000L,
            category = "전자제품"
        )
        val item = OrderItem.create(product, 2)
        val order = Order(
            id = "ORD001",
            userId = "USER1",
            items = listOf(item),
            totalAmount = 2_000_000L,
            discountAmount = 0L,
            finalAmount = 2_000_000L,
            status = "PAID"
        )

        // When & Then
        val exception = assertThrows<IllegalStateException> {
            order.cancel()
        }
        assert(exception.message?.contains("결제 완료된 주문은 취소할 수 없습니다") ?: false)
    }

    @Test
    @DisplayName("할인을 포함한 주문을 생성할 수 있다")
    fun testCreateOrderWithDiscount() {
        // Given
        val product = Product(
            id = "P001",
            name = "노트북",
            description = "고급 노트북",
            price = 1_000_000L,
            category = "전자제품"
        )
        val item = OrderItem.create(product, 2)

        // When
        val order = Order(
            id = "ORD001",
            userId = "USER1",
            items = listOf(item),
            totalAmount = 2_000_000L,
            discountAmount = 200_000L,
            finalAmount = 1_800_000L
        )

        // Then
        assert(order.totalAmount == 2_000_000L)
        assert(order.discountAmount == 200_000L)
        assert(order.finalAmount == 1_800_000L)
    }
}
