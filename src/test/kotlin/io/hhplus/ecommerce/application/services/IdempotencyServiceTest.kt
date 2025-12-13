package io.hhplus.ecommerce.application.services

import io.hhplus.ecommerce.infrastructure.persistence.entity.IdempotencyKeyJpaEntity
import io.hhplus.ecommerce.infrastructure.persistence.entity.IdempotencyStatus
import io.hhplus.ecommerce.infrastructure.persistence.repository.IdempotencyKeyJpaRepository
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

/**
 * 멱등성 서비스 테스트
 *
 * Phase 2: 멱등성 보장 개선
 *
 * 검증 사항:
 * 1. 새로운 요청은 PROCESSING 상태로 생성된다
 * 2. 동일한 요청 재시도 시 기존 응답을 반환한다
 * 3. 좀비 요청(1시간 초과)은 자동으로 FAILED 처리된다
 * 4. TTL 초과(24시간) 키는 자동 삭제된다
 */
@DisplayName("멱등성 서비스 테스트")
class IdempotencyServiceTest {

    private lateinit var idempotencyKeyRepository: IdempotencyKeyJpaRepository
    private lateinit var idempotencyService: IdempotencyService

    @BeforeEach
    fun setUp() {
        idempotencyKeyRepository = mockk(relaxed = true)
        idempotencyService = IdempotencyService(idempotencyKeyRepository)
    }

    @Test
    @DisplayName("새로운 요청은 PROCESSING 상태로 생성된다")
    fun shouldCreateNewKeyForNewRequest() {
        // Given: 기존 키가 없음
        every { idempotencyKeyRepository.findByIdempotencyKey(any()) } returns Optional.empty()
        every { idempotencyKeyRepository.save(any()) } returnsArgument 0

        // When: 새로운 요청
        val result = idempotencyService.acquireKey(
            idempotencyKey = "test-key-1",
            requestType = "order-payment",
            userId = 100L,
            entityId = 1L
        )

        // Then: 새 키 생성
        assertThat(result).isInstanceOf(IdempotencyResult.NewRequest::class.java)

        verify(exactly = 1) {
            idempotencyKeyRepository.save(
                match {
                    it.idempotencyKey == "test-key-1" &&
                    it.status == IdempotencyStatus.PROCESSING
                }
            )
        }
    }

    @Test
    @DisplayName("PROCESSING 상태의 요청은 동시 요청으로 감지된다")
    fun shouldDetectConcurrentRequest() {
        // Given: 처리 중인 요청
        val processingKey = IdempotencyKeyJpaEntity(
            idempotencyKey = "test-key-2",
            requestType = "order-payment",
            userId = 100L,
            status = IdempotencyStatus.PROCESSING,
            updatedAt = LocalDateTime.now().minusMinutes(10) // 10분 전
        )
        every { idempotencyKeyRepository.findByIdempotencyKey("test-key-2") } returns Optional.of(processingKey)

        // When: 동일한 요청 재시도
        val result = idempotencyService.acquireKey(
            idempotencyKey = "test-key-2",
            requestType = "order-payment",
            userId = 100L
        )

        // Then: PROCESSING 반환
        assertThat(result).isInstanceOf(IdempotencyResult.Processing::class.java)
        assertThat((result as IdempotencyResult.Processing).message).contains("처리 중")
    }

    @Test
    @DisplayName("COMPLETED 상태의 요청은 이전 응답을 반환한다")
    fun shouldReturnCachedResponseForCompletedRequest() {
        // Given: 완료된 요청
        val completedKey = IdempotencyKeyJpaEntity(
            idempotencyKey = "test-key-3",
            requestType = "order-payment",
            userId = 100L,
            status = IdempotencyStatus.COMPLETED,
            responseData = """{"sagaId":"saga-1","status":"SUCCESS"}"""
        )
        every { idempotencyKeyRepository.findByIdempotencyKey("test-key-3") } returns Optional.of(completedKey)

        // When: 동일한 요청 재시도
        val result = idempotencyService.acquireKey(
            idempotencyKey = "test-key-3",
            requestType = "order-payment",
            userId = 100L
        )

        // Then: 이전 응답 반환
        assertThat(result).isInstanceOf(IdempotencyResult.AlreadyCompleted::class.java)
        val response = (result as IdempotencyResult.AlreadyCompleted).responseData
        assertThat(response).contains("saga-1")
        assertThat(response).contains("SUCCESS")
    }

    @Test
    @DisplayName("좀비 요청(1시간 초과)은 자동으로 FAILED 처리된다")
    fun shouldMarkZombieRequestAsFailed() {
        // Given: 1시간 이상 PROCESSING 상태
        val zombieKey = IdempotencyKeyJpaEntity(
            idempotencyKey = "test-key-4",
            requestType = "order-payment",
            userId = 100L,
            status = IdempotencyStatus.PROCESSING,
            updatedAt = LocalDateTime.now().minusHours(2) // 2시간 전
        )
        every { idempotencyKeyRepository.findByIdempotencyKey("test-key-4") } returns Optional.of(zombieKey)
        every { idempotencyKeyRepository.save(any()) } returnsArgument 0

        // When: 좀비 요청 조회
        val result = idempotencyService.acquireKey(
            idempotencyKey = "test-key-4",
            requestType = "order-payment",
            userId = 100L
        )

        // Then: FAILED로 전환
        assertThat(result).isInstanceOf(IdempotencyResult.Failed::class.java)
        assertThat((result as IdempotencyResult.Failed).message).contains("타임아웃")

        verify {
            idempotencyKeyRepository.save(
                match {
                    it.status == IdempotencyStatus.FAILED &&
                    it.errorMessage?.contains("좀비") == true
                }
            )
        }
    }

    @Test
    @DisplayName("좀비 요청 자동 정리 스케줄러가 동작한다")
    fun shouldCleanupZombieRequests() {
        // Given: 좀비 요청 목록
        val zombie1 = IdempotencyKeyJpaEntity(
            idempotencyKey = "zombie-1",
            requestType = "order-payment",
            userId = 100L,
            status = IdempotencyStatus.PROCESSING,
            updatedAt = LocalDateTime.now().minusHours(2)
        )
        val zombie2 = IdempotencyKeyJpaEntity(
            idempotencyKey = "zombie-2",
            requestType = "balance-deduct",
            userId = 200L,
            status = IdempotencyStatus.PROCESSING,
            updatedAt = LocalDateTime.now().minusHours(3)
        )

        every { idempotencyKeyRepository.findZombieRequests(any()) } returns listOf(zombie1, zombie2)
        every { idempotencyKeyRepository.save(any()) } returnsArgument 0

        // When: 좀비 정리 실행
        idempotencyService.cleanupZombieRequests()

        // Then: 2개 모두 FAILED 처리
        verify(exactly = 2) {
            idempotencyKeyRepository.save(
                match { it.status == IdempotencyStatus.FAILED }
            )
        }
    }

    @Test
    @DisplayName("TTL 초과(24시간) 키는 자동 삭제된다")
    fun shouldDeleteExpiredKeys() {
        // Given: 24시간 초과 키들
        val expiredKey1 = IdempotencyKeyJpaEntity(
            idempotencyKey = "expired-1",
            requestType = "order-payment",
            userId = 100L,
            status = IdempotencyStatus.COMPLETED,
            createdAt = LocalDateTime.now().minusHours(25)
        )
        val expiredKey2 = IdempotencyKeyJpaEntity(
            idempotencyKey = "expired-2",
            requestType = "order-payment",
            userId = 200L,
            status = IdempotencyStatus.FAILED,
            createdAt = LocalDateTime.now().minusHours(30)
        )

        every { idempotencyKeyRepository.findExpiredKeys(any()) } returns listOf(expiredKey1, expiredKey2)
        every { idempotencyKeyRepository.deleteAll(any<List<IdempotencyKeyJpaEntity>>()) } just Runs

        // When: TTL 정리 실행
        idempotencyService.cleanupExpiredKeys()

        // Then: 삭제됨
        verify(exactly = 1) {
            idempotencyKeyRepository.deleteAll(
                match<List<IdempotencyKeyJpaEntity>> { it.size == 2 }
            )
        }
    }

    @Test
    @DisplayName("성공 완료 처리 시 응답 데이터가 저장된다")
    fun shouldSaveResponseDataOnCompletion() {
        // Given: PROCESSING 상태 키
        val key = IdempotencyKeyJpaEntity(
            idempotencyKey = "test-key-5",
            requestType = "order-payment",
            userId = 100L,
            status = IdempotencyStatus.PROCESSING
        )
        every { idempotencyKeyRepository.findByIdempotencyKey("test-key-5") } returns Optional.of(key)
        every { idempotencyKeyRepository.save(any()) } returnsArgument 0

        // When: 성공 완료
        idempotencyService.markAsCompleted(
            idempotencyKey = "test-key-5",
            responseData = """{"orderId":123,"status":"PAID"}"""
        )

        // Then: 응답 데이터 저장
        verify {
            idempotencyKeyRepository.save(
                match {
                    it.status == IdempotencyStatus.COMPLETED &&
                    it.responseData?.contains("orderId") == true
                }
            )
        }
    }

    @Test
    @DisplayName("실패 처리 시 에러 메시지가 저장된다")
    fun shouldSaveErrorMessageOnFailure() {
        // Given: PROCESSING 상태 키
        val key = IdempotencyKeyJpaEntity(
            idempotencyKey = "test-key-6",
            requestType = "order-payment",
            userId = 100L,
            status = IdempotencyStatus.PROCESSING
        )
        every { idempotencyKeyRepository.findByIdempotencyKey("test-key-6") } returns Optional.of(key)
        every { idempotencyKeyRepository.save(any()) } returnsArgument 0

        // When: 실패 처리
        idempotencyService.markAsFailed(
            idempotencyKey = "test-key-6",
            errorMessage = "잔액 부족"
        )

        // Then: 에러 메시지 저장
        verify {
            idempotencyKeyRepository.save(
                match {
                    it.status == IdempotencyStatus.FAILED &&
                    it.errorMessage == "잔액 부족"
                }
            )
        }
    }
}
