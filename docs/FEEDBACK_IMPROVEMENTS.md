# 피드백 개선 사항

## 📌 피드백 요약

1. **단점에 대한 보완점 부족**
   - 문제: Orchestrator SPOF에 대한 해결책 미제시
   - 문제: 이벤트 실행 추적 및 자동 재조정 메커니즘 부재

2. **멱등성 키 관리 미흡**
   - 문제: 타임아웃 + 오류 동시 발생 시 처리 방안 부족
   - 문제: Idempotency Key 정리 시점 미정의

3. **비동기 처리 개선 필요**
   - 현재: HTTP 동기 방식 중심
   - 개선: Kafka 비동기 메시징 활용 방안 검토

---

## 1. Orchestrator SPOF 해결 방안

### 🎯 목표
Orchestrator 장애 시에도 SAGA 복구 가능하도록 상태 영속화

### 📊 현재 문제점
```kotlin
// 현재: 메모리에만 저장
private val sagaInstances = mutableMapOf<String, SagaInstance>()
```

**문제:**
- Orchestrator 재시작 시 진행 중인 SAGA 손실
- 장애 발생 시 복구 불가능
- 어떤 단계까지 실행되었는지 추적 불가

### ✅ 개선 방안

#### 1.1. SAGA 상태 DB 영속화

**Entity 설계:**
```kotlin
@Entity
@Table(name = "saga_instances")
class SagaInstanceJpaEntity(
    @Id val sagaId: String,
    val orderId: Long,
    val userId: Long,

    @Enumerated(EnumType.STRING)
    var status: SagaStatus,  // PENDING, RUNNING, COMPENSATING, COMPLETED, FAILED, STUCK

    var currentStep: String?,  // 현재 실행 중인 단계
    var completedStepsJson: String,  // 완료된 단계 목록 ["STEP1", "STEP2"]
    var errorMessage: String?,

    var retryCount: Int = 0,
    val maxRetryCount: Int = 3,

    val createdAt: LocalDateTime,
    var updatedAt: LocalDateTime,
    var completedAt: LocalDateTime?
)
```

**장점:**
- ✅ Orchestrator 재시작 시 미완료 SAGA 자동 복구
- ✅ 실행 이력 추적 가능 (어디까지 실행되었는지)
- ✅ 장애 시 수동 복구 가능 (운영 도구)

#### 1.2. 자동 복구 메커니즘

**SagaRecoveryService 구현:**
```kotlin
@Service
class SagaRecoveryService {

    /**
     * 실패한 SAGA 자동 재시도 (5분마다)
     *
     * 피드백: "자동으로 재조정하는 부분까지 고민"
     */
    @Scheduled(fixedDelay = 300000)
    @Transactional
    fun recoverFailedSagas() {
        // 1. 5분 이상 FAILED 상태인 SAGA 조회
        val failedSagas = sagaRepository.findRetryableSagas(
            before = LocalDateTime.now().minusMinutes(5)
        )

        failedSagas.forEach { saga ->
            if (saga.canRetry()) {  // retryCount < maxRetryCount
                try {
                    // 2. 재시도 횟수 증가
                    saga.incrementRetry()

                    // 3. SAGA 재실행
                    val response = orchestrator.execute(
                        PaymentSagaRequest(saga.orderId, saga.userId)
                    )

                    // 4. 성공 시 COMPLETED로 변경
                    if (response.status == "SUCCESS") {
                        saga.markAsCompleted()
                    }
                } catch (e: Exception) {
                    // 5. 최대 재시도 초과 시 STUCK 처리 (수동 개입 필요)
                    if (!saga.canRetry()) {
                        saga.markAsStuck(e.message)
                        alertOps(saga)  // 운영팀 알림
                    }
                }
            }
        }
    }

    /**
     * SAGA 진행 상황 조회
     *
     * 피드백: "어디까지 실행되었고 어떤 부분에서 오류 발생했는지"
     */
    fun getSagaProgress(orderId: Long): SagaProgress {
        val saga = sagaRepository.findByOrderId(orderId)

        return SagaProgress(
            status = saga.status,
            currentStep = saga.currentStep,  // 실패 지점
            completedSteps = parseJson(saga.completedStepsJson),  // 완료된 단계
            errorMessage = saga.errorMessage,  // 오류 내용
            retryCount = saga.retryCount,
            maxRetryCount = saga.maxRetryCount
        )
    }
}
```

**Recovery Query:**
```sql
-- 재시도 가능한 SAGA 조회
SELECT * FROM saga_instances
WHERE status = 'FAILED'
  AND retry_count < max_retry_count
  AND updated_at < NOW() - INTERVAL 5 MINUTE
ORDER BY created_at ASC;

-- 중단된 SAGA 조회 (운영팀 알림 필요)
SELECT * FROM saga_instances
WHERE status = 'STUCK'
   OR (status = 'COMPENSATING' AND updated_at < NOW() - INTERVAL 1 HOUR)
ORDER BY created_at ASC;
```

#### 1.3. Orchestrator 고가용성 아키텍처

**현업 적용 사례:**

```
┌─────────────────────────────────────────┐
│         Load Balancer (HA Proxy)         │
└─────────┬───────────────────┬───────────┘
          │                   │
    ┌─────▼──────┐      ┌────▼────────┐
    │ Orchestrator│      │ Orchestrator│
    │   Node 1    │      │   Node 2    │
    │  (Active)   │      │  (Standby)  │
    └─────┬───────┘      └─────┬───────┘
          │                    │
          └──────────┬─────────┘
                     │
            ┌────────▼─────────┐
            │   SAGA State DB   │
            │   (PostgreSQL)    │
            └───────────────────┘
```

**고가용성 보장:**
1. **Active-Standby 구성**
   - Node 1 장애 시 Node 2가 즉시 인계
   - DB에 영속화된 SAGA 상태로 복구

2. **리더 선출 (Leader Election)**
   - Redis/Zookeeper로 리더 선출
   - 리더만 SAGA Recovery 작업 수행 (중복 방지)

3. **Health Check**
   - `/actuator/health`로 Orchestrator 상태 모니터링
   - 장애 감지 시 자동 Failover

---

## 2. 멱등성 키 자동 정리 메커니즘

### 🎯 목표
타임아웃 + 오류 동시 발생 시에도 안전한 멱등성 보장

### 📊 현재 문제점

**시나리오: 타임아웃 + 오류 동시 발생**
```
Client → [Request 1: key="order-123-deduct"] → Service (처리 중...)
Client → [Request 2: key="order-123-deduct"] → Service (중복 방지)

문제: key="order-123-deduct"가 언제 삭제되는가?
- 성공 시: 즉시 삭제?
- 실패 시: 언제 삭제?
- 타임아웃 시: 클라이언트는 실패로 보지만 서버는 성공할 수 있음
```

### ✅ 개선 방안

#### 2.1. Idempotency Key Entity 설계

```kotlin
@Entity
@Table(name = "idempotency_keys")
class IdempotencyKeyEntity(
    @Id
    val key: String,  // "order-123-deduct-balance"

    val requestHash: String,  // Request Body SHA-256
    val userId: Long,
    val operation: String,  // "DEDUCT_BALANCE", "CONFIRM_INVENTORY"

    @Enumerated(EnumType.STRING)
    var status: IdempotencyStatus,  // PROCESSING, COMPLETED, FAILED

    @Column(columnDefinition = "TEXT")
    var responseBody: String?,  // 성공 시 응답 저장 (재요청 시 반환)

    @Column(columnDefinition = "TEXT")
    var errorMessage: String?,  // 실패 시 오류 저장

    val createdAt: LocalDateTime,
    var updatedAt: LocalDateTime,

    // TTL: 기본 24시간 후 자동 삭제
    val expiresAt: LocalDateTime = createdAt.plusHours(24)
)
```

#### 2.2. 멱등성 처리 로직

```kotlin
@Service
class IdempotencyService {

    @Transactional
    fun executeIdempotent(
        key: String,
        requestHash: String,
        operation: () -> Any
    ): IdempotentResult {

        // 1. 기존 키 조회
        val existing = repository.findById(key)

        when {
            // 2-1. 키가 없음 → 첫 요청
            existing == null -> {
                val entity = IdempotencyKeyEntity(
                    key = key,
                    requestHash = requestHash,
                    status = PROCESSING
                )
                repository.save(entity)

                try {
                    val result = operation()  // 실제 작업 수행

                    entity.status = COMPLETED
                    entity.responseBody = serialize(result)
                    repository.save(entity)

                    return IdempotentResult.success(result)
                } catch (e: Exception) {
                    entity.status = FAILED
                    entity.errorMessage = e.message
                    repository.save(entity)

                    throw e
                }
            }

            // 2-2. 처리 중 → 중복 요청 (503 Retry-After)
            existing.status == PROCESSING -> {
                throw IdempotencyConflictException(
                    "요청이 처리 중입니다. 잠시 후 다시 시도하세요.",
                    retryAfter = 5  // 5초 후 재시도
                )
            }

            // 2-3. 완료됨 → 저장된 응답 반환 (멱등성 보장)
            existing.status == COMPLETED -> {
                if (existing.requestHash == requestHash) {
                    return IdempotentResult.cached(
                        deserialize(existing.responseBody)
                    )
                } else {
                    throw IdempotencyMismatchException(
                        "같은 키로 다른 요청이 이미 처리되었습니다."
                    )
                }
            }

            // 2-4. 실패함 → 재시도 허용 (같은 요청이면)
            existing.status == FAILED -> {
                if (existing.requestHash == requestHash &&
                    existing.createdAt.isAfter(LocalDateTime.now().minusMinutes(5))
                ) {
                    // 5분 이내 같은 요청 → 재시도
                    existing.status = PROCESSING
                    repository.save(existing)
                    // ... 재실행
                } else {
                    throw IdempotencyExpiredException(
                        "만료된 멱등성 키입니다."
                    )
                }
            }
        }
    }
}
```

#### 2.3. 자동 정리 스케줄러

```kotlin
@Service
class IdempotencyCleanupService {

    /**
     * 만료된 멱등성 키 자동 정리 (1시간마다)
     */
    @Scheduled(fixedDelay = 3600000)
    @Transactional
    fun cleanupExpiredKeys() {
        val now = LocalDateTime.now()

        // 1. TTL 만료된 키 삭제
        val expiredKeys = repository.findByExpiresAtBefore(now)
        repository.deleteAll(expiredKeys)

        logger.info("만료된 멱등성 키 ${expiredKeys.size}개 삭제")

        // 2. 오래된 PROCESSING 상태 정리 (좀비 요청)
        // 1시간 이상 PROCESSING 상태 → FAILED로 변경
        val zombieKeys = repository.findByStatusAndCreatedAtBefore(
            status = PROCESSING,
            before = now.minusHours(1)
        )

        zombieKeys.forEach { key ->
            key.status = FAILED
            key.errorMessage = "타임아웃: 1시간 이상 처리되지 않음"
            repository.save(key)
        }

        logger.warn("좀비 멱등성 키 ${zombieKeys.size}개 FAILED 처리")
    }
}
```

#### 2.4. 타임아웃 + 오류 동시 처리

**시나리오 분석:**

```
Time: 0s    - Client → Server: Request (key="order-123", timeout=30s)
Time: 10s   - Server: 잔액 차감 시작 (DB 락 대기 중...)
Time: 30s   - Client: Timeout! 재시도 결정
Time: 31s   - Client → Server: Request (key="order-123") 재시도
              Server: 기존 요청이 PROCESSING → 503 Retry-After: 5s 반환
Time: 35s   - Server: 잔액 차감 완료 → key 상태 COMPLETED로 변경
Time: 36s   - Client: 5초 후 재시도 → Server: COMPLETED 응답 반환 (멱등성!)
```

**보장 사항:**
- ✅ 중복 처리 방지 (PROCESSING 체크)
- ✅ 타임아웃 후 재시도 안전 (COMPLETED 응답 재사용)
- ✅ 좀비 요청 자동 정리 (1시간 후 FAILED 처리)
- ✅ TTL로 자동 삭제 (24시간 후)

---

## 3. Kafka 비동기 메시징 아키텍처

### 🎯 목표
HTTP 동기 방식에서 Kafka 비동기 방식으로 전환하여 성능 및 확장성 개선

### 📊 현재 (HTTP 동기 방식)

```
Client → PaymentController → PaymentSagaOrchestrator
                                      ↓
                            [HTTP] UserService.deductBalance()
                                      ↓
                            [HTTP] InventoryService.confirmReservation()
                                      ↓
                            [HTTP] CouponService.useCoupon()

문제:
- 각 서비스 호출이 순차적 → 전체 응답 시간 = 합계
- 하나의 서비스 장애 시 전체 실패
- 트래픽 급증 시 병목 발생
```

### ✅ 개선: Kafka 이벤트 기반 아키텍처

#### 3.1. 아키텍처 설계

```
┌─────────────┐        Kafka         ┌──────────────┐
│   Client    │                       │ OrderService │
└──────┬──────┘                       └──────┬───────┘
       │                                     │
       │ POST /orders                        │
       ├─────────────────────────────────────▶│
       │                                     │
       │ 202 Accepted (orderId: 123)        │
       │◀────────────────────────────────────┤
       │                                     │
       │                        Topic: payment.requested
       │                                     │
       │                                     ├──────────▶ [Kafka]
       │
       │
Topic: payment.requested                Topic: payment.completed
       │                                     │
       ▼                                     ▼
┌──────────────┐                    ┌───────────────┐
│UserService   │                    │OrderService   │
│(Consumer)    │                    │(Consumer)     │
└──────┬───────┘                    └───────┬───────┘
       │                                    │
       │ 잔액 차감                          │ 주문 완료
       │                                    │
       ├──▶ Topic: balance.deducted        │
                                            │
                                            ▼
                                    Topic: order.completed
                                            │
                                            ▼
                                    [Client Webhook/SSE]
```

#### 3.2. Kafka Topic 설계

```yaml
Topics:
  # 1. 주문 생성 요청
  - payment.requested:
      partitions: 3
      replication: 3
      key: orderId
      value:
        orderId: 123
        userId: 456
        totalAmount: 100000
        items: [...]

  # 2. 잔액 차감 완료
  - balance.deducted:
      partitions: 3
      key: orderId
      value:
        orderId: 123
        userId: 456
        deductedAmount: 100000
        remainingBalance: 50000

  # 3. 재고 확정 완료
  - inventory.confirmed:
      partitions: 3
      key: orderId
      value:
        orderId: 123
        items: [...]

  # 4. 쿠폰 사용 완료
  - coupon.used:
      partitions: 3
      key: orderId
      value:
        orderId: 123
        couponId: 789
        discountAmount: 10000

  # 5. 주문 완료 (SAGA Success)
  - order.completed:
      partitions: 3
      key: orderId
      value:
        orderId: 123
        status: COMPLETED

  # 6. 주문 실패 (SAGA Failure)
  - order.failed:
      partitions: 3
      key: orderId
      value:
        orderId: 123
        failedStep: INVENTORY_CONFIRM
        errorMessage: "재고 부족"
```

#### 3.3. SAGA with Kafka (Choreography 패턴)

**현재 (Orchestration):**
```kotlin
// Orchestrator가 모든 단계를 직접 제어
orchestrator.execute() {
    userService.deductBalance()  // HTTP 동기 호출
    inventoryService.confirm()   // HTTP 동기 호출
    couponService.use()          // HTTP 동기 호출
}
```

**개선 (Choreography):**
```kotlin
// 각 서비스가 이벤트를 발행하고 구독하여 협업

// 1. OrderService
@KafkaListener(topics = ["payment.requested"])
fun handlePaymentRequest(event: PaymentRequestedEvent) {
    // 주문 생성
    val order = createOrder(event)

    // 다음 단계 이벤트 발행
    kafkaTemplate.send("balance.deduction.requested", event)
}

// 2. UserService
@KafkaListener(topics = ["balance.deduction.requested"])
fun handleBalanceDeduction(event: BalanceDeductionRequestedEvent) {
    try {
        deductBalance(event.userId, event.amount)
        kafkaTemplate.send("balance.deducted", BalanceDeductedEvent(event.orderId))
    } catch (e: InsufficientBalanceException) {
        kafkaTemplate.send("order.failed", OrderFailedEvent(
            orderId = event.orderId,
            failedStep = "BALANCE_DEDUCTION",
            errorMessage = e.message
        ))
    }
}

// 3. InventoryService
@KafkaListener(topics = ["balance.deducted"])
fun handleBalanceDeducted(event: BalanceDeductedEvent) {
    confirmInventory(event.orderId)
    kafkaTemplate.send("inventory.confirmed", event)
}

// 4. OrderService (최종 완료)
@KafkaListener(topics = ["inventory.confirmed"])
fun handleInventoryConfirmed(event: InventoryConfirmedEvent) {
    completeOrder(event.orderId)
    kafkaTemplate.send("order.completed", OrderCompletedEvent(event.orderId))
}

// 5. 보상 트랜잭션 (실패 시)
@KafkaListener(topics = ["order.failed"])
fun handleOrderFailed(event: OrderFailedEvent) {
    when (event.failedStep) {
        "INVENTORY_CONFIRM" -> {
            kafkaTemplate.send("balance.refund.requested", event)
        }
        "COUPON_USE" -> {
            kafkaTemplate.send("inventory.release.requested", event)
            kafkaTemplate.send("balance.refund.requested", event)
        }
    }
}
```

#### 3.4. 장점 비교

| 항목 | HTTP 동기 | Kafka 비동기 |
|------|-----------|--------------|
| **응답 시간** | ~2000ms (합계) | ~100ms (202 Accepted) |
| **확장성** | 수직 (Scale Up) | 수평 (Scale Out) |
| **장애 격리** | 하나 실패 → 전체 실패 | 재시도 큐로 격리 |
| **처리량** | 동기 대기로 제한 | 파티션 병렬 처리 |
| **복잡도** | 낮음 | 높음 (이벤트 추적 필요) |

#### 3.5. 이벤트 추적 (Distributed Tracing)

**문제: Kafka 비동기 방식에서 어디까지 실행되었는지?**

**해결: Event Sourcing + Saga Log**

```kotlin
@Entity
@Table(name = "saga_event_log")
class SagaEventLogEntity(
    @Id @GeneratedValue
    val id: Long = 0,

    val sagaId: String,  // "payment-saga-123"
    val orderId: Long,

    val eventType: String,  // "BALANCE_DEDUCTED", "INVENTORY_CONFIRMED"
    val topic: String,
    val partition: Int,
    val offset: Long,

    @Column(columnDefinition = "TEXT")
    val payload: String,  // JSON

    val createdAt: LocalDateTime = LocalDateTime.now()
)

// 조회 예시
SELECT event_type, created_at
FROM saga_event_log
WHERE order_id = 123
ORDER BY created_at ASC;

/*
결과:
PAYMENT_REQUESTED      2024-01-01 10:00:00
BALANCE_DEDUCTED       2024-01-01 10:00:05
INVENTORY_CONFIRMED    2024-01-01 10:00:10
ORDER_COMPLETED        2024-01-01 10:00:15

→ 진행 상황 명확히 추적 가능
*/
```

---

## 4. 종합 아키텍처 (개선 후)

```
┌────────────────────────────────────────────────────────────┐
│                   Load Balancer (HA)                        │
└──────────────────────┬─────────────────────────────────────┘
                       │
        ┌──────────────┼──────────────┐
        │              │              │
   ┌────▼───┐    ┌────▼───┐    ┌────▼───┐
   │ Order  │    │ User   │    │Inventory│
   │Service │    │Service │    │Service  │
   └────┬───┘    └────┬───┘    └────┬───┘
        │             │             │
        └─────────────┼─────────────┘
                      │
            ┌─────────▼──────────┐
            │   Kafka Cluster     │
            │  (Event Streaming)  │
            └─────────┬───────────┘
                      │
        ┌─────────────┼─────────────┐
        │             │             │
   ┌────▼───────┐ ┌──▼──────┐ ┌───▼────────┐
   │ PostgreSQL │ │  Redis  │ │   Kafka    │
   │ (SAGA DB)  │ │ (Cache) │ │ (Events)   │
   └────────────┘ └─────────┘ └────────────┘
```

**핵심 개선 사항:**
1. ✅ **SPOF 해결**: SAGA 상태 DB 영속화 + Auto Recovery
2. ✅ **이벤트 추적**: completedSteps + SagaEventLog로 진행 상황 파악
3. ✅ **자동 재조정**: @Scheduled로 실패 SAGA 자동 재시도
4. ✅ **멱등성 관리**: TTL + Cleanup으로 키 자동 정리
5. ✅ **비동기 확장**: Kafka Choreography 패턴 도입 계획

---

## 5. 마이그레이션 로드맵

### Phase 1: SAGA 영속화 (현재 구현 중)
- [x] SagaInstanceJpaEntity 설계
- [x] SagaRecoveryService 구현
- [ ] PaymentSagaOrchestrator 연동
- [ ] 모니터링 대시보드

### Phase 2: 멱등성 개선 (다음 Sprint)
- [ ] IdempotencyKeyEntity 설계
- [ ] IdempotencyService 구현
- [ ] Cleanup 스케줄러
- [ ] 통합 테스트

### Phase 3: Kafka 도입 (차주)
- [ ] Kafka 클러스터 구성
- [ ] Topic 설계 완료
- [ ] Producer/Consumer 구현
- [ ] Choreography 패턴 전환

### Phase 4: 운영 안정화
- [ ] Distributed Tracing (Zipkin/Jaeger)
- [ ] 알림 시스템 (STUCK SAGA)
- [ ] A/B 테스팅 (HTTP vs Kafka)
- [ ] 성능 튜닝
