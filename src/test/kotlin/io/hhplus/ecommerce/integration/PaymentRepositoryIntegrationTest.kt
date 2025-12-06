package io.hhplus.ecommerce.integration

import io.hhplus.ecommerce.config.TestContainersConfig
import io.hhplus.ecommerce.config.TestRedissonConfig
import io.hhplus.ecommerce.domain.Payment
import io.hhplus.ecommerce.domain.PaymentMethod
import io.hhplus.ecommerce.domain.PaymentStatus
import io.hhplus.ecommerce.infrastructure.repositories.PaymentRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Tag
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

/**
 * PaymentRepository 통합 테스트
 *
 * PaymentRepositoryImpl이 실제 데이터베이스와 정상적으로 작동하는지 검증
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Tag("integration")
@DisplayName("PaymentRepository 통합 테스트")
@ActiveProfiles("test")
@Import(TestContainersConfig::class, TestRedissonConfig::class)
class PaymentRepositoryIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var paymentRepository: PaymentRepository

    @Test
    @DisplayName("결제를 저장하고 조회할 수 있다")
    fun testSaveAndFindPayment() {
        // Given
        val payment = Payment.create(
            orderId = 1L,
            idempotencyKey = "test-key-001",
            method = PaymentMethod.CARD,
            amount = 50000L
        )

        // When
        val savedPayment = paymentRepository.save(payment)

        // Then
        assertThat(savedPayment.id).isGreaterThan(0)
        assertThat(savedPayment.orderId).isEqualTo(1L)
        assertThat(savedPayment.idempotencyKey).isEqualTo("test-key-001")
        assertThat(savedPayment.amount).isEqualTo(50000L)
        assertThat(savedPayment.status).isEqualTo(PaymentStatus.PENDING)

        // 조회 확인
        val foundPayment = paymentRepository.findById(savedPayment.id)
        assertThat(foundPayment).isNotNull
        assertThat(foundPayment!!.id).isEqualTo(savedPayment.id)
    }

    @Test
    @DisplayName("멱등성 키로 결제를 조회할 수 있다")
    fun testFindByIdempotencyKey() {
        // Given
        val idempotencyKey = "unique-key-123"
        val payment = Payment.create(
            orderId = 2L,
            idempotencyKey = idempotencyKey,
            method = PaymentMethod.BANK_TRANSFER,
            amount = 100000L
        )
        paymentRepository.save(payment)

        // When
        val foundPayment = paymentRepository.findByIdempotencyKey(idempotencyKey)

        // Then
        assertThat(foundPayment).isNotNull
        assertThat(foundPayment!!.idempotencyKey).isEqualTo(idempotencyKey)
        assertThat(foundPayment.orderId).isEqualTo(2L)
        assertThat(foundPayment.method).isEqualTo(PaymentMethod.BANK_TRANSFER)
    }

    @Test
    @DisplayName("주문 ID로 결제를 조회할 수 있다")
    fun testFindByOrderId() {
        // Given
        val orderId = 3L
        val payment = Payment.create(
            orderId = orderId,
            idempotencyKey = "order-3-payment",
            method = PaymentMethod.VIRTUAL_ACCOUNT,
            amount = 75000L
        )
        paymentRepository.save(payment)

        // When
        val foundPayment = paymentRepository.findByOrderId(orderId)

        // Then
        assertThat(foundPayment).isNotNull
        assertThat(foundPayment!!.orderId).isEqualTo(orderId)
        assertThat(foundPayment.method).isEqualTo(PaymentMethod.VIRTUAL_ACCOUNT)
    }

    @Test
    @DisplayName("결제 상태를 변경하고 저장할 수 있다")
    fun testUpdatePaymentStatus() {
        // Given: 결제 생성
        val payment = Payment.create(
            orderId = 4L,
            idempotencyKey = "payment-status-test",
            method = PaymentMethod.CARD,
            amount = 60000L
        )
        val savedPayment = paymentRepository.save(payment)

        // When: 승인 처리
        val approvedPayment = savedPayment.approve("TXN-12345")
        val updatedPayment = paymentRepository.save(approvedPayment)

        // Then: 상태 변경 확인
        assertThat(updatedPayment.status).isEqualTo(PaymentStatus.APPROVED)
        assertThat(updatedPayment.transactionId).isEqualTo("TXN-12345")
        assertThat(updatedPayment.approvedAt).isNotNull

        // DB에서 다시 조회하여 확인
        val reloadedPayment = paymentRepository.findById(updatedPayment.id)
        assertThat(reloadedPayment!!.status).isEqualTo(PaymentStatus.APPROVED)
        assertThat(reloadedPayment.transactionId).isEqualTo("TXN-12345")
    }

    @Test
    @DisplayName("새 트랜잭션에서 결제를 저장할 수 있다")
    fun testSaveInNewTransaction() {
        // Given
        val payment = Payment.create(
            orderId = 5L,
            idempotencyKey = "new-transaction-test",
            method = PaymentMethod.CARD,
            amount = 40000L
        )

        // When
        val savedPayment = paymentRepository.saveInNewTransaction(payment)

        // Then
        assertThat(savedPayment.id).isGreaterThan(0)
        assertThat(savedPayment.idempotencyKey).isEqualTo("new-transaction-test")

        // 트랜잭션 외부에서도 조회 가능
        val foundPayment = paymentRepository.findByIdempotencyKey("new-transaction-test")
        assertThat(foundPayment).isNotNull
        assertThat(foundPayment!!.id).isEqualTo(savedPayment.id)
    }

    @Test
    @DisplayName("동일한 멱등성 키로 중복 저장 시 기존 결제를 반환한다")
    fun testIdempotencyOnDuplicateSave() {
        // Given: 첫 번째 결제 저장
        val idempotencyKey = "duplicate-key-test"
        val payment1 = Payment.create(
            orderId = 6L,
            idempotencyKey = idempotencyKey,
            method = PaymentMethod.CARD,
            amount = 30000L
        )
        val savedPayment1 = paymentRepository.saveInNewTransaction(payment1)

        // When: 동일한 멱등성 키로 다시 저장 시도
        val payment2 = Payment.create(
            orderId = 7L,  // 다른 주문 ID
            idempotencyKey = idempotencyKey,  // 동일한 멱등성 키
            method = PaymentMethod.BANK_TRANSFER,
            amount = 50000L
        )
        val savedPayment2 = paymentRepository.saveInNewTransaction(payment2)

        // Then: 동일한 결제가 반환됨
        assertThat(savedPayment2.id).isEqualTo(savedPayment1.id)
        assertThat(savedPayment2.orderId).isEqualTo(savedPayment1.orderId)  // 첫 번째 결제의 orderId
        assertThat(savedPayment2.idempotencyKey).isEqualTo(idempotencyKey)
    }

    @Test
    @DisplayName("분산 락을 사용하여 동시성 제어를 할 수 있다")
    fun testWithDistributedLock() {
        // Given
        val idempotencyKey = "concurrent-lock-test"
        val threadCount = 5
        val results = mutableListOf<Long>()
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)

        // When: 여러 스레드가 동시에 분산 락 획득 시도
        repeat(threadCount) { index ->
            executor.submit {
                try {
                    paymentRepository.withDistributedLock(
                        idempotencyKey = idempotencyKey,
                        waitTime = 10L,
                        holdTime = 2L
                    ) {
                        // 락 내부에서 결제 생성
                        val payment = Payment.create(
                            orderId = 100L + index,
                            idempotencyKey = "$idempotencyKey-$index",
                            method = PaymentMethod.CARD,
                            amount = 10000L
                        )
                        val saved = paymentRepository.saveInNewTransaction(payment)
                        synchronized(results) {
                            results.add(saved.id)
                        }
                        Thread.sleep(100)  // 락 보유 시간 시뮬레이션
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        // Then: 모든 스레드가 성공적으로 결제 생성
        assertThat(results).hasSize(threadCount)
        assertThat(results.distinct()).hasSize(threadCount)  // 모두 다른 ID
    }

    @Test
    @DisplayName("Entity와 Domain 모델 간 변환이 정확하다")
    fun testEntityDomainConversion() {
        // Given: 복잡한 결제 객체
        val originalPayment = Payment(
            id = 0L,
            orderId = 999L,
            idempotencyKey = "conversion-test",
            method = PaymentMethod.VIRTUAL_ACCOUNT,
            status = PaymentStatus.PENDING,
            amount = 123456L,
            transactionId = null,
            pgCode = null,
            failReason = null
        )

        // When: 저장 (Entity 변환 → 저장 → Domain 변환)
        val savedPayment = paymentRepository.save(originalPayment)

        // Then: 모든 필드가 정확히 보존됨
        assertThat(savedPayment.orderId).isEqualTo(originalPayment.orderId)
        assertThat(savedPayment.idempotencyKey).isEqualTo(originalPayment.idempotencyKey)
        assertThat(savedPayment.method).isEqualTo(originalPayment.method)
        assertThat(savedPayment.status).isEqualTo(originalPayment.status)
        assertThat(savedPayment.amount).isEqualTo(originalPayment.amount)

        // When: 상태 변경 후 저장
        val failedPayment = savedPayment.fail("Test failure", "PG_ERROR")
        val updatedPayment = paymentRepository.save(failedPayment)

        // Then: 변경된 필드가 정확히 저장됨
        assertThat(updatedPayment.status).isEqualTo(PaymentStatus.FAILED)
        assertThat(updatedPayment.failReason).isEqualTo("Test failure")
        assertThat(updatedPayment.pgCode).isEqualTo("PG_ERROR")
    }

    @Test
    @DisplayName("다양한 결제 수단을 저장하고 조회할 수 있다")
    fun testDifferentPaymentMethods() {
        // Given & When: 각 결제 수단으로 결제 생성
        val cardPayment = paymentRepository.save(
            Payment.create(1L, "card-payment", PaymentMethod.CARD, 10000L)
        )
        val bankPayment = paymentRepository.save(
            Payment.create(2L, "bank-payment", PaymentMethod.BANK_TRANSFER, 20000L)
        )
        val virtualAccountPayment = paymentRepository.save(
            Payment.create(3L, "virtual-payment", PaymentMethod.VIRTUAL_ACCOUNT, 30000L)
        )

        // Then: 각 결제 수단이 정확히 저장됨
        assertThat(paymentRepository.findById(cardPayment.id)!!.method).isEqualTo(PaymentMethod.CARD)
        assertThat(paymentRepository.findById(bankPayment.id)!!.method).isEqualTo(PaymentMethod.BANK_TRANSFER)
        assertThat(paymentRepository.findById(virtualAccountPayment.id)!!.method).isEqualTo(PaymentMethod.VIRTUAL_ACCOUNT)
    }

    @Test
    @DisplayName("결제 환불 처리를 할 수 있다")
    fun testPaymentRefund() {
        // Given: 승인된 결제
        val payment = Payment.create(10L, "refund-test", PaymentMethod.CARD, 50000L)
        val savedPayment = paymentRepository.save(payment)
        val approvedPayment = savedPayment.approve("TXN-REFUND")
        val approved = paymentRepository.save(approvedPayment)

        // When: 환불 처리
        val refundedPayment = approved.refund()
        val refunded = paymentRepository.save(refundedPayment)

        // Then: 환불 상태로 변경됨
        assertThat(refunded.status).isEqualTo(PaymentStatus.REFUNDED)

        // DB에서 다시 조회하여 확인
        val reloaded = paymentRepository.findById(refunded.id)
        assertThat(reloaded!!.status).isEqualTo(PaymentStatus.REFUNDED)
    }
}
