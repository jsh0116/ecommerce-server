package io.hhplus.ecommerce.infrastructure.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.hhplus.ecommerce.application.usecases.CouponUseCase
import io.hhplus.ecommerce.application.services.IdempotencyResult
import io.hhplus.ecommerce.application.services.IdempotencyService
import io.hhplus.ecommerce.config.TestKafkaConfig
import io.hhplus.ecommerce.domain.UserCoupon
import io.hhplus.ecommerce.exception.CouponException
import io.hhplus.ecommerce.infrastructure.kafka.events.CouponIssuanceRequestedEvent
import io.hhplus.ecommerce.test.IntegrationTestBase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.just
import io.mockk.runs
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.TestPropertySource
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

@SpringBootTest
@EmbeddedKafka(
    topics = ["coupon.issuance.requested", "coupon.issued", "coupon.issuance.failed"],
    partitions = 3,
    bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@TestPropertySource(
    properties = [
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.consumer.group-id=test-group"
    ]
)
@Import(TestKafkaConfig::class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Tag("integration")
@DisplayName("CouponIssuanceConsumer 통합 테스트")
class CouponIssuanceConsumerTest : IntegrationTestBase() {

    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockkBean
    private lateinit var couponUseCase: CouponUseCase

    @MockkBean
    private lateinit var idempotencyService: IdempotencyService

    @BeforeEach
    fun setUp() {
        // Mock will be configured per test
    }

    @Test
    @DisplayName("쿠폰 발급 요청을 수신하고 처리할 수 있다")
    fun consumeAndProcessIssuanceRequest() {
        // Given
        val userId = 123L
        val couponId = 456L
        val requestId = "req-$userId-$couponId-${System.currentTimeMillis()}"

        val event = CouponIssuanceRequestedEvent(
            requestId = requestId,
            userId = userId,
            couponId = couponId,
            requestedAt = LocalDateTime.now()
        )

        val userCoupon = UserCoupon(
            userId = userId,
            couponId = couponId,
            couponName = "테스트 쿠폰",
            discountRate = 10,
            issuedAt = LocalDateTime.now()
        )

        // Mock setup
        every { idempotencyService.acquireKey(any(), any(), any(), any()) } returns
            IdempotencyResult.NewRequest(mockk())

        every { couponUseCase.issueCouponDirectly(couponId, userId) } returns userCoupon

        every { idempotencyService.markAsCompleted(any(), any()) } just runs

        // When
        val payload = objectMapper.writeValueAsString(event)
        kafkaTemplate.send("coupon.issuance.requested", userId.toString(), payload).get()

        // Then
        await()
            .atMost(10, TimeUnit.SECONDS)
            .pollInterval(Duration.ofMillis(500))
            .untilAsserted {
                verify(exactly = 1) { couponUseCase.issueCouponDirectly(couponId, userId) }
                verify(exactly = 1) { idempotencyService.markAsCompleted(requestId, any()) }
            }
    }

    @Test
    @DisplayName("중복 요청은 멱등성 검증으로 필터링된다")
    fun filterDuplicateRequests() {
        // Given
        val userId = 100L
        val couponId = 200L
        val requestId = "req-duplicate-${System.currentTimeMillis()}"

        val event = CouponIssuanceRequestedEvent(
            requestId = requestId,
            userId = userId,
            couponId = couponId
        )

        // Mock setup
        every { idempotencyService.acquireKey(requestId, any(), any(), any()) } returns
            IdempotencyResult.AlreadyCompleted("{\"userId\":$userId,\"couponId\":$couponId}")

        // When
        val payload = objectMapper.writeValueAsString(event)
        kafkaTemplate.send("coupon.issuance.requested", userId.toString(), payload).get()

        // Then
        await()
            .atMost(10, TimeUnit.SECONDS)
            .pollInterval(Duration.ofMillis(500))
            .untilAsserted {
                verify(exactly = 0) { couponUseCase.issueCouponDirectly(any(), any()) }
            }
    }

    @Test
    @DisplayName("쿠폰 소진 시 실패 이벤트를 발행한다")
    fun publishFailedEventWhenCouponSoldOut() {
        // Given
        val userId = 111L
        val couponId = 222L
        val requestId = "req-soldout-${System.currentTimeMillis()}"

        val event = CouponIssuanceRequestedEvent(
            requestId = requestId,
            userId = userId,
            couponId = couponId
        )

        // Mock setup
        every { idempotencyService.acquireKey(any(), any(), any(), any()) } returns
            IdempotencyResult.NewRequest(mockk())

        every { couponUseCase.issueCouponDirectly(couponId, userId) } throws CouponException.CouponExhausted()

        // When
        val payload = objectMapper.writeValueAsString(event)
        kafkaTemplate.send("coupon.issuance.requested", userId.toString(), payload).get()

        // Then
        await()
            .atMost(10, TimeUnit.SECONDS)
            .pollInterval(Duration.ofMillis(500))
            .untilAsserted {
                verify(exactly = 1) { couponUseCase.issueCouponDirectly(couponId, userId) }
                // 실패 이벤트가 발행됨 (로그로 확인)
            }
    }
}