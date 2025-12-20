package io.hhplus.ecommerce.infrastructure.kafka

import io.hhplus.ecommerce.config.TestKafkaConfig
import io.hhplus.ecommerce.test.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.TestPropertySource
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * OrderPaidEventConsumer 통합 테스트
 *
 * EmbeddedKafka를 사용하여 실제 Kafka 환경을 시뮬레이션합니다.
 */
@SpringBootTest
@EmbeddedKafka(
    topics = ["outbox.event.ORDER"],
    partitions = 1,
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
@DisplayName("OrderPaidEventConsumer 통합 테스트")
class OrderPaidEventConsumerTest : IntegrationTestBase() {

    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @Autowired
    private lateinit var orderPaidEventConsumer: OrderPaidEventConsumer

    @BeforeEach
    fun setUp() {
        orderPaidEventConsumer.clearProcessedEvents()
    }

    @Test
    @DisplayName("ORDER_PAID 이벤트를 수신하고 처리할 수 있다")
    fun consumeOrderPaidEvent() {
        // Given
        val userId = "user-123"
        val payload = """
            {
                "orderId": 456,
                "userId": 123,
                "totalAmount": 100000,
                "discountAmount": 10000,
                "finalAmount": 90000,
                "paidAt": "2024-01-01T10:00:00"
            }
        """.trimIndent()

        // When
        kafkaTemplate.send("outbox.event.ORDER", userId, payload).get()

        // Then - Awaitility로 비동기 처리 대기
        await()
            .atMost(10, TimeUnit.SECONDS)
            .pollInterval(Duration.ofMillis(500))
            .untilAsserted {
                val processedEvents = orderPaidEventConsumer.getProcessedEvents()
                assertThat(processedEvents).isNotEmpty
                assertThat(processedEvents.last()).contains("orderId")
                assertThat(processedEvents.last()).contains("456")
            }
    }

    @Test
    @DisplayName("여러 ORDER_PAID 이벤트를 순차적으로 처리할 수 있다")
    fun consumeMultipleOrderPaidEvents() {
        // Given
        val userId = "user-456"
        val events = listOf(
            """{"orderId": 1, "userId": 456, "finalAmount": 10000}""",
            """{"orderId": 2, "userId": 456, "finalAmount": 20000}""",
            """{"orderId": 3, "userId": 456, "finalAmount": 30000}"""
        )

        // When
        events.forEach { payload ->
            kafkaTemplate.send("outbox.event.ORDER", userId, payload).get()
        }

        // Then
        await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted {
                val processedEvents = orderPaidEventConsumer.getProcessedEvents()
                assertThat(processedEvents.size).isGreaterThanOrEqualTo(3)
            }
    }

    @Test
    @DisplayName("동일한 userId의 이벤트는 순서가 보장된다")
    fun eventOrderingForSameUserId() {
        // Given
        val userId = "user-789"
        val event1 = """{"orderId": 100, "userId": 789, "finalAmount": 10000, "sequence": 1}"""
        val event2 = """{"orderId": 101, "userId": 789, "finalAmount": 20000, "sequence": 2}"""
        val event3 = """{"orderId": 102, "userId": 789, "finalAmount": 30000, "sequence": 3}"""

        // When - 동일한 userId (Message Key)로 전송
        kafkaTemplate.send("outbox.event.ORDER", userId, event1).get()
        kafkaTemplate.send("outbox.event.ORDER", userId, event2).get()
        kafkaTemplate.send("outbox.event.ORDER", userId, event3).get()

        // Then - 같은 파티션에 순서대로 저장되므로 순차 처리됨
        await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted {
                val processedEvents = orderPaidEventConsumer.getProcessedEvents()
                assertThat(processedEvents.size).isGreaterThanOrEqualTo(3)

                // 이벤트가 순서대로 처리되었는지 확인
                val lastThreeEvents = processedEvents.takeLast(3)
                assertThat(lastThreeEvents[0]).contains("\"sequence\": 1")
                assertThat(lastThreeEvents[1]).contains("\"sequence\": 2")
                assertThat(lastThreeEvents[2]).contains("\"sequence\": 3")
            }
    }

    @Test
    @DisplayName("잘못된 형식의 이벤트를 수신하면 로그를 남기고 계속 진행한다")
    fun handleInvalidEventFormat() {
        // Given
        val invalidPayload = """{"invalid": "data"""  // 잘못된 JSON

        // When
        kafkaTemplate.send("outbox.event.ORDER", "user-999", invalidPayload).get()

        // Then - Consumer가 멈추지 않고 계속 실행됨
        await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted {
                // Consumer가 여전히 실행 중이며 다음 이벤트를 처리할 수 있음
                val validPayload = """{"orderId": 999, "userId": 999, "finalAmount": 50000}"""
                kafkaTemplate.send("outbox.event.ORDER", "user-999", validPayload).get()

                Thread.sleep(2000)

                val processedEvents = orderPaidEventConsumer.getProcessedEvents()
                val validEventProcessed = processedEvents.any { it.contains("999") }
                assertThat(validEventProcessed).isTrue
            }
    }
}
