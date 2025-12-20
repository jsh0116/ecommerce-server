package io.hhplus.ecommerce.infrastructure.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import io.hhplus.ecommerce.config.TestKafkaConfig
import io.hhplus.ecommerce.infrastructure.kafka.events.CouponIssuanceRequestedEvent
import io.hhplus.ecommerce.test.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.TestPropertySource

@SpringBootTest
@EmbeddedKafka(
    topics = ["coupon.issuance.requested"],
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
@DisplayName("CouponIssuanceProducer 통합 테스트")
class CouponIssuanceProducerTest : IntegrationTestBase() {

    @Autowired
    private lateinit var producer: CouponIssuanceProducer

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    @DisplayName("쿠폰 발급 요청을 Kafka에 발행할 수 있다")
    fun publishIssuanceRequest() {
        // Given
        val userId = 123L
        val couponId = 456L
        val requestId = producer.generateRequestId(userId, couponId)

        // When
        producer.publishIssuanceRequest(userId, couponId, requestId)

        // Then
        Thread.sleep(1000) // Kafka 발행 대기
        // 실제로는 Consumer에서 검증하거나 Kafka Admin API로 확인
    }

    @Test
    @DisplayName("requestId를 정상적으로 생성한다")
    fun generateRequestId() {
        // Given
        val userId = 100L
        val couponId = 200L

        // When
        val requestId = producer.generateRequestId(userId, couponId)

        // Then
        assertThat(requestId).startsWith("req-$userId-$couponId-")
        assertThat(requestId).contains(userId.toString())
        assertThat(requestId).contains(couponId.toString())
    }

    @Test
    @DisplayName("동일한 userId의 요청은 같은 파티션으로 전송된다")
    fun sameUserIdSamePartition() {
        // Given
        val userId = 101L

        // When
        val requestId1 = producer.generateRequestId(userId, 1L)
        val requestId2 = producer.generateRequestId(userId, 2L)

        producer.publishIssuanceRequest(userId, 1L, requestId1)
        producer.publishIssuanceRequest(userId, 2L, requestId2)

        // Then
        Thread.sleep(1000)
        // 같은 userId → 같은 파티션 (검증은 Consumer 로그로 확인)
    }
}
