package io.hhplus.ecommerce.application.services

import io.hhplus.ecommerce.domain.Payment
import io.hhplus.ecommerce.domain.PaymentMethod
import io.hhplus.ecommerce.domain.PaymentStatus
import io.hhplus.ecommerce.infrastructure.repositories.PaymentRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

@DisplayName("PaymentService 테스트")
class PaymentServiceTest {

    private val paymentRepository = mockk<PaymentRepository>()
    private val service = PaymentService(paymentRepository)

    @Nested
    @DisplayName("결제 처리 테스트")
    inner class ProcessPaymentTest {
        @Test
        fun `새로운 결제를 생성할 수 있다`() {
            // Given
            val idempotencyKey = "key-001"
            val createdPayment = Payment(
                id = 1L,
                orderId = 1L,
                idempotencyKey = idempotencyKey,
                method = PaymentMethod.CARD,
                amount = 50000L,
                status = PaymentStatus.PENDING,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )

            val blockSlot = slot<() -> Payment>()
            every { paymentRepository.withDistributedLock<Payment>(
                idempotencyKey = idempotencyKey,
                waitTime = 60L,
                holdTime = 30L,
                block = capture(blockSlot)
            ) } answers { blockSlot.captured.invoke() }

            every { paymentRepository.findByIdempotencyKeyInNewTransaction(idempotencyKey) } returns null
            every { paymentRepository.saveInNewTransaction(any()) } returns createdPayment

            // When
            val result = service.processPayment(1L, 50000L, PaymentMethod.CARD, idempotencyKey)

            // Then
            assertThat(result).isNotNull
            assertThat(result.orderId).isEqualTo(1L)
            assertThat(result.amount).isEqualTo(50000L)
            assertThat(result.status).isEqualTo(PaymentStatus.PENDING)
            verify { paymentRepository.findByIdempotencyKeyInNewTransaction(idempotencyKey) }
            verify { paymentRepository.saveInNewTransaction(any()) }
        }

        @Test
        fun `동일한 키로 재요청 시 기존 결과를 반환한다`() {
            // Given
            val idempotencyKey = "key-001"
            val existingPayment = Payment(
                id = 1L,
                orderId = 1L,
                idempotencyKey = idempotencyKey,
                method = PaymentMethod.CARD,
                amount = 50000L,
                status = PaymentStatus.PENDING
            )

            val blockSlot = slot<() -> Payment>()
            every { paymentRepository.withDistributedLock<Payment>(
                idempotencyKey = idempotencyKey,
                waitTime = 60L,
                holdTime = 30L,
                block = capture(blockSlot)
            ) } answers { blockSlot.captured.invoke() }

            every { paymentRepository.findByIdempotencyKeyInNewTransaction(idempotencyKey) } returns existingPayment

            // When
            val result = service.processPayment(1L, 50000L, PaymentMethod.CARD, idempotencyKey)

            // Then
            assertThat(result).isEqualTo(existingPayment)
            verify(exactly = 0) { paymentRepository.saveInNewTransaction(any()) }
        }

        @Test
        fun `다양한 결제 방법으로 결제할 수 있다`() {
            // Given
            val idempotencyKey = "key-bank"
            val createdPayment = Payment(
                id = 2L,
                orderId = 2L,
                idempotencyKey = idempotencyKey,
                method = PaymentMethod.BANK_TRANSFER,
                amount = 100000L,
                status = PaymentStatus.PENDING
            )

            val blockSlot = slot<() -> Payment>()
            every { paymentRepository.withDistributedLock<Payment>(
                idempotencyKey = idempotencyKey,
                waitTime = 60L,
                holdTime = 30L,
                block = capture(blockSlot)
            ) } answers { blockSlot.captured.invoke() }

            every { paymentRepository.findByIdempotencyKeyInNewTransaction(idempotencyKey) } returns null
            every { paymentRepository.saveInNewTransaction(any()) } returns createdPayment

            // When
            val result = service.processPayment(2L, 100000L, PaymentMethod.BANK_TRANSFER, idempotencyKey)

            // Then
            assertThat(result.method).isEqualTo(PaymentMethod.BANK_TRANSFER)
        }
    }

    @Nested
    @DisplayName("결제 승인 테스트")
    inner class ApprovePaymentTest {
        @Test
        fun `결제를 승인할 수 있다`() {
            // Given
            val payment = Payment(
                id = 1L,
                orderId = 1L,
                idempotencyKey = "key-001",
                method = PaymentMethod.CARD,
                amount = 50000L,
                status = PaymentStatus.PENDING
            )
            val approvedPayment = payment.approve("TXN-001")

            every { paymentRepository.findById(1L) } returns payment
            every { paymentRepository.save(any()) } returns approvedPayment

            // When
            val result = service.approvePayment(1L, "TXN-001")

            // Then
            assertThat(result).isNotNull
            assertThat(result.status).isEqualTo(PaymentStatus.APPROVED)
            assertThat(result.transactionId).isEqualTo("TXN-001")
            verify { paymentRepository.findById(1L) }
            verify { paymentRepository.save(any()) }
        }

        @Test
        fun `존재하지 않는 결제는 예외를 발생시킨다`() {
            // Given
            every { paymentRepository.findById(999L) } returns null

            // When/Then
            assertThatThrownBy {
                service.approvePayment(999L, "TXN-001")
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("결제를 찾을 수 없습니다")
        }
    }

    @Nested
    @DisplayName("결제 실패 처리 테스트")
    inner class FailPaymentTest {
        @Test
        fun `결제 실패 처리를 할 수 있다`() {
            // Given
            val payment = Payment(
                id = 1L,
                orderId = 1L,
                idempotencyKey = "key-001",
                method = PaymentMethod.CARD,
                amount = 50000L,
                status = PaymentStatus.PENDING
            )
            val failedPayment = payment.fail("카드 승인 거절", "CARD_DECLINED")

            every { paymentRepository.findById(1L) } returns payment
            every { paymentRepository.save(any()) } returns failedPayment

            // When
            val result = service.failPayment(1L, "카드 승인 거절", "CARD_DECLINED")

            // Then
            assertThat(result).isNotNull
            assertThat(result.status).isEqualTo(PaymentStatus.FAILED)
            assertThat(result.failReason).isEqualTo("카드 승인 거절")
            assertThat(result.pgCode).isEqualTo("CARD_DECLINED")
            verify { paymentRepository.findById(1L) }
            verify { paymentRepository.save(any()) }
        }

        @Test
        fun `PG 코드 없이 실패 처리할 수 있다`() {
            // Given
            val payment = Payment(
                id = 1L,
                orderId = 1L,
                idempotencyKey = "key-001",
                method = PaymentMethod.CARD,
                amount = 50000L,
                status = PaymentStatus.PENDING
            )
            val failedPayment = payment.fail("일반 오류", null)

            every { paymentRepository.findById(1L) } returns payment
            every { paymentRepository.save(any()) } returns failedPayment

            // When
            val result = service.failPayment(1L, "일반 오류")

            // Then
            assertThat(result).isNotNull
            assertThat(result.status).isEqualTo(PaymentStatus.FAILED)
            assertThat(result.pgCode).isNull()
        }
    }

    @Nested
    @DisplayName("환불 처리 테스트")
    inner class RefundPaymentTest {
        @Test
        fun `승인된 결제를 환불할 수 있다`() {
            // Given
            val payment = Payment(
                id = 1L,
                orderId = 1L,
                idempotencyKey = "key-001",
                method = PaymentMethod.CARD,
                amount = 50000L,
                status = PaymentStatus.APPROVED,
                transactionId = "TXN-001",
                approvedAt = LocalDateTime.now()
            )
            val refundedPayment = payment.refund()

            every { paymentRepository.findById(1L) } returns payment
            every { paymentRepository.save(any()) } returns refundedPayment

            // When
            val result = service.refundPayment(1L)

            // Then
            assertThat(result).isNotNull
            assertThat(result.status).isEqualTo(PaymentStatus.REFUNDED)
            verify { paymentRepository.findById(1L) }
            verify { paymentRepository.save(any()) }
        }

        @Test
        fun `미승인 결제는 환불할 수 없다`() {
            // Given
            val payment = Payment(
                id = 1L,
                orderId = 1L,
                idempotencyKey = "key-001",
                method = PaymentMethod.CARD,
                amount = 50000L,
                status = PaymentStatus.PENDING
            )

            every { paymentRepository.findById(1L) } returns payment

            // When/Then
            assertThatThrownBy {
                service.refundPayment(1L)
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("승인된 결제만 환불 가능")
        }
    }

    @Nested
    @DisplayName("결제 조회 테스트")
    inner class GetPaymentTest {
        @Test
        fun `멱등성 키로 결제를 조회할 수 있다`() {
            // Given
            val idempotencyKey = "key-001"
            val payment = Payment(
                id = 1L,
                orderId = 1L,
                idempotencyKey = idempotencyKey,
                method = PaymentMethod.CARD,
                amount = 50000L,
                status = PaymentStatus.APPROVED
            )

            every { paymentRepository.findByIdempotencyKey(idempotencyKey) } returns payment

            // When
            val result = service.getPaymentByIdempotencyKey(idempotencyKey)

            // Then
            assertThat(result).isNotNull
            assertThat(result?.id).isEqualTo(1L)
        }

        @Test
        fun `주문별 결제를 조회할 수 있다`() {
            // Given
            val payment = Payment(
                id = 1L,
                orderId = 1L,
                idempotencyKey = "key-001",
                method = PaymentMethod.CARD,
                amount = 50000L,
                status = PaymentStatus.APPROVED
            )

            every { paymentRepository.findByOrderId(1L) } returns payment

            // When
            val result = service.getPaymentByOrderId(1L)

            // Then
            assertThat(result).isNotNull
            assertThat(result?.orderId).isEqualTo(1L)
        }
    }

    @Nested
    @DisplayName("결제 상태 확인 테스트")
    inner class IsPaymentApprovedTest {
        @Test
        fun `결제가 승인되었는지 확인할 수 있다`() {
            // Given
            val payment = Payment(
                id = 1L,
                orderId = 1L,
                idempotencyKey = "key-001",
                method = PaymentMethod.CARD,
                amount = 50000L,
                status = PaymentStatus.APPROVED
            )

            every { paymentRepository.findByOrderId(1L) } returns payment

            // When
            val result = service.isPaymentApproved(1L)

            // Then
            assertThat(result).isTrue
        }

        @Test
        fun `미승인 결제는 false를 반환한다`() {
            // Given
            val payment = Payment(
                id = 1L,
                orderId = 1L,
                idempotencyKey = "key-001",
                method = PaymentMethod.CARD,
                amount = 50000L,
                status = PaymentStatus.PENDING
            )

            every { paymentRepository.findByOrderId(1L) } returns payment

            // When
            val result = service.isPaymentApproved(1L)

            // Then
            assertThat(result).isFalse
        }

        @Test
        fun `결제가 없으면 false를 반환한다`() {
            // Given
            every { paymentRepository.findByOrderId(999L) } returns null

            // When
            val result = service.isPaymentApproved(999L)

            // Then
            assertThat(result).isFalse
        }
    }
}
