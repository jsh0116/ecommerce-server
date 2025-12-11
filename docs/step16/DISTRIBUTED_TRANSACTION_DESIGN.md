# 분산 트랜잭션 설계 문서 (STEP 16)

## 목차

1. [개요](#개요)
2. [도메인 분리 전략](#도메인-분리-전략)
3. [분산 트랜잭션 문제점](#분산-트랜잭션-문제점)
4. [SAGA 패턴 설계](#saga-패턴-설계)
5. [보상 트랜잭션 설계](#보상-트랜잭션-설계)
6. [멱등성 전략](#멱등성-전략)
7. [재시도 및 실패 처리](#재시도-및-실패-처리)
8. [시퀀스 다이어그램](#시퀀스-다이어그램)

---

## 개요

### 목적

본 문서는 e-commerce 시스템의 확장에 따라 **모놀리식 아키텍처에서 MSA(Microservices Architecture)로 전환**할 때 발생하는 **분산 트랜잭션 문제**와 **SAGA 패턴을 활용한 해결 방안**을 설계합니다.

### 배경

현재 시스템은 단일 트랜잭션(`@Transactional`)으로 다음 작업을 원자적으로 처리합니다:

```kotlin
@Transactional
fun processPayment(orderId: Long, userId: Long): PaymentResult {
    // 1. 주문 확인 (Order 도메인)
    val order = orderService.getById(orderId)

    // 2. 잔액 차감 (User 도메인)
    userService.deductBalance(userId, order.finalAmount)

    // 3. 재고 차감 (Inventory 도메인)
    for (item in order.items) {
        inventory.confirmReservation(item.quantity)
        inventoryRepository.save(inventory)
    }

    // 4. 쿠폰 사용 (Coupon 도메인)
    if (order.couponId != null) {
        couponService.useCoupon(userCoupon)
    }

    // 5. 주문 완료 (Order 도메인)
    orderService.completeOrder(orderId)

    return PaymentResult(...)
}
```

**문제점:**
- 모든 도메인이 하나의 DB에 종속 → 확장성 제약
- 긴 트랜잭션으로 DB 락 증가 → 성능 병목
- 한 도메인 장애가 전체 결제 실패로 전파 → 가용성 저하

---

## 도메인 분리 전략

### MSA 서비스 분리

| 서비스 | 책임 | 독립 DB | API 엔드포인트 예시 |
|--------|------|---------|---------------------|
| **Order Service** | 주문 생성/조회/완료 | `order_db` | `POST /orders`, `PATCH /orders/{id}/complete` |
| **User Service** | 사용자 잔액 관리 | `user_db` | `POST /users/{id}/balance/deduct`, `POST /users/{id}/balance/refund` |
| **Inventory Service** | 재고 예약/차감/복구 | `inventory_db` | `POST /inventories/reserve`, `POST /inventories/confirm`, `POST /inventories/restore` |
| **Coupon Service** | 쿠폰 검증/사용/복구 | `coupon_db` | `POST /coupons/use`, `POST /coupons/restore` |

### 통신 방식

- **동기 통신:** HTTP/REST API (서비스 간 직접 호출)
- **비동기 통신:** 메시지 큐 (Kafka, RabbitMQ) - 이벤트 발행/구독

### 배포 독립성

각 서비스는:
- 독립적인 Docker 컨테이너
- 독립적인 CI/CD 파이프라인
- 독립적인 확장 (Auto Scaling)

---

## 분산 트랜잭션 문제점

### 1. 부분 성공 (Partial Success)

**시나리오:**
```
✅ T1: Order Service - 주문 생성 성공 (커밋됨)
✅ T2: User Service - 잔액 차감 성공 (커밋됨)
✅ T3: Inventory Service - 재고 차감 성공 (커밋됨)
❌ T4: Coupon Service - 쿠폰 서비스 장애 발생!
```

**결과:**
- 주문/잔액/재고는 이미 각 서비스의 로컬 DB에 커밋됨
- 쿠폰만 실패 → **데이터 불일치 발생**
- 단일 트랜잭션처럼 자동 롤백 불가능

**비즈니스 영향:**
- 사용자는 돈을 냈지만 쿠폰 혜택을 못 받음
- 또는 쿠폰은 차감 안 됐는데 결제는 완료됨

### 2. 네트워크 타임아웃 (Uncertain State)

**시나리오:**
```
✅ T1: Order Service - 주문 생성 성공
✅ T2: User Service - 잔액 차감 성공
⏱️ T3: Inventory Service 호출 → 5초 타임아웃 발생
     (실제로는 재고 차감 성공했지만 응답 못 받음)
```

**문제:**
- Order Service는 실패로 판단 → 보상 트랜잭션 실행
- Inventory Service는 이미 재고 차감 완료 → **이중 복구 위험**
- 재고가 실제보다 많아지는 데이터 손상

### 3. 서비스 장애 전파

**시나리오:**
```
✅ T1: Order Service - 주문 생성 성공
❌ T2: User Service - 일시적 장애 (DB 커넥션 풀 고갈)
```

**문제:**
- User Service 장애로 전체 결제 중단
- 다른 서비스(Inventory, Coupon)는 정상이지만 사용 불가
- 동기 호출의 한계 (Cascading Failure)

### 4. 보상 트랜잭션 실패

**시나리오:**
```
✅ T1: Order Service - 주문 생성 성공
✅ T2: User Service - 잔액 차감 성공
❌ T3: Inventory Service - 재고 부족으로 실패
❌ C2: User Service 보상 트랜잭션 (잔액 복구) 시도 중 장애 발생!
```

**문제:**
- 보상 트랜잭션 자체도 실패할 수 있음
- 최종적으로 **잔액만 차감되고 주문은 실패**한 상태로 남음
- 수동 복구 필요 (운영 부담 증가)

---

## SAGA 패턴 설계

### SAGA 패턴이란?

분산 트랜잭션을 **로컬 트랜잭션의 시퀀스**로 분해하고, 실패 시 **보상 트랜잭션(Compensating Transaction)**으로 이전 단계를 취소하는 패턴입니다.

### SAGA 패턴 유형

#### 1. Choreography (이벤트 기반)

각 서비스가 이벤트를 발행/구독하여 자율적으로 동작:

```
Order Service: OrderCreated 이벤트 발행
  ↓
User Service: 이벤트 수신 → 잔액 차감 → BalanceDeducted 이벤트 발행
  ↓
Inventory Service: 이벤트 수신 → 재고 차감 → StockDeducted 이벤트 발행
  ↓
Coupon Service: 이벤트 수신 → 쿠폰 사용 → CouponUsed 이벤트 발행
```

**장점:** 느슨한 결합, 서비스 독립성
**단점:** 복잡한 흐름 추적 어려움, 디버깅 어려움

#### 2. Orchestration (중앙 조율)

**Order Saga Orchestrator**가 모든 서비스 호출을 조율:

```
Order Saga Orchestrator:
  1. Order Service.createOrder()
  2. User Service.deductBalance()
  3. Inventory Service.confirmReservation()
  4. Coupon Service.useCoupon()
  5. Order Service.completeOrder()

  실패 시:
  - Coupon Service.restoreCoupon()
  - Inventory Service.restoreStock()
  - User Service.refundBalance()
  - Order Service.cancelOrder()
```

**장점:** 명확한 흐름, 중앙 집중식 관리, 디버깅 용이
**단점:** Orchestrator 자체가 SPOF(Single Point of Failure)

### 본 프로젝트 선택: **Orchestration 패턴**

**이유:**
1. 결제 흐름은 명확한 순서가 있음 (주문 → 잔액 → 재고 → 쿠폰)
2. 실패 시 보상 순서가 중요 (역순으로 복구)
3. 디버깅 및 모니터링이 중요한 금융 거래
4. 이미 `OrderUseCase`가 유사한 역할 수행 중

---

## 보상 트랜잭션 설계

### 보상 트랜잭션 원칙

1. **멱등성(Idempotency):** 같은 보상 요청을 여러 번 실행해도 결과가 동일
2. **역순 실행:** 원래 트랜잭션의 역순으로 보상 실행
3. **최선 노력(Best Effort):** 보상 실패 시 재시도 또는 수동 처리

### Payment SAGA 흐름

#### ✅ 성공 시나리오 (Forward Recovery)

```
Step 1: Order Service - 주문 생성
  ↓ 성공
Step 2: User Service - 잔액 차감
  ↓ 성공
Step 3: Inventory Service - 재고 차감
  ↓ 성공
Step 4: Coupon Service - 쿠폰 사용
  ↓ 성공
Step 5: Order Service - 주문 완료
  ↓
✅ SAGA 성공
```

#### ❌ 실패 시나리오 (Backward Recovery)

**Case 1: Step 2 (User Service)에서 실패**

```
Step 1: Order Service - 주문 생성 (✅ 완료)
Step 2: User Service - 잔액 차감 (❌ 실패: 잔액 부족)

보상 트랜잭션:
  Compensate 1: Order Service - 주문 취소 (CANCELLED 상태로 변경)
```

**Case 2: Step 3 (Inventory Service)에서 실패**

```
Step 1: Order Service - 주문 생성 (✅ 완료)
Step 2: User Service - 잔액 차감 (✅ 완료)
Step 3: Inventory Service - 재고 차감 (❌ 실패: 재고 부족)

보상 트랜잭션 (역순 실행):
  Compensate 2: User Service - 잔액 복구 (환불)
  Compensate 1: Order Service - 주문 취소
```

**Case 3: Step 4 (Coupon Service)에서 실패**

```
Step 1: Order Service - 주문 생성 (✅ 완료)
Step 2: User Service - 잔액 차감 (✅ 완료)
Step 3: Inventory Service - 재고 차감 (✅ 완료)
Step 4: Coupon Service - 쿠폰 사용 (❌ 실패: 쿠폰 만료)

보상 트랜잭션 (역순 실행):
  Compensate 3: Inventory Service - 재고 복구
  Compensate 2: User Service - 잔액 복구
  Compensate 1: Order Service - 주문 취소
```

### 보상 트랜잭션 API 설계

각 서비스는 **Forward Operation**과 **Compensating Operation**을 모두 제공:

| 서비스 | Forward API | Compensating API |
|--------|-------------|------------------|
| Order Service | `POST /orders` | `POST /orders/{id}/cancel` |
| User Service | `POST /users/{id}/balance/deduct` | `POST /users/{id}/balance/refund` |
| Inventory Service | `POST /inventories/confirm` | `POST /inventories/restore` |
| Coupon Service | `POST /coupons/use` | `POST /coupons/restore` |

---

## 멱등성 전략

### 문제 상황

네트워크 타임아웃 시 **같은 요청이 중복 실행**될 수 있음:

```
Order Service → User Service: deductBalance(userId=1, amount=10000)
  ↓
User Service: 처리 완료, 응답 전송
  ↓
❌ 네트워크 타임아웃 (응답 손실)
  ↓
Order Service: 실패로 판단 → 재시도
  ↓
User Service: deductBalance(userId=1, amount=10000) 다시 실행
  ↓
결과: 20000원이 차감됨 (이중 차감!)
```

### 해결책: Idempotency Key

모든 요청에 **고유 식별자(Idempotency Key)**를 포함:

```kotlin
data class DeductBalanceRequest(
    val userId: Long,
    val amount: Long,
    val idempotencyKey: String  // 예: "order-123-deduct-balance"
)
```

**서비스 측 처리:**

```kotlin
@Transactional
fun deductBalance(request: DeductBalanceRequest): DeductBalanceResponse {
    // 1. 이미 처리된 요청인지 확인
    val existing = idempotencyRepository.findByKey(request.idempotencyKey)
    if (existing != null) {
        logger.info("중복 요청 감지: ${request.idempotencyKey}, 기존 결과 반환")
        return existing.response  // 기존 결과 반환 (재실행 안 함)
    }

    // 2. 잔액 차감 실행
    val user = userRepository.findById(request.userId)
    user.deductBalance(request.amount)
    userRepository.save(user)

    // 3. 멱등성 키 저장 (TTL: 24시간)
    val response = DeductBalanceResponse(newBalance = user.balance)
    idempotencyRepository.save(
        IdempotencyRecord(
            key = request.idempotencyKey,
            response = response,
            createdAt = LocalDateTime.now()
        )
    )

    return response
}
```

**Idempotency Key 생성 규칙:**

```kotlin
fun generateIdempotencyKey(orderId: Long, step: String): String {
    return "order-${orderId}-${step}"
}

// 사용 예시:
val deductBalanceKey = generateIdempotencyKey(orderId, "deduct-balance")
val confirmStockKey = generateIdempotencyKey(orderId, "confirm-stock")
val useCouponKey = generateIdempotencyKey(orderId, "use-coupon")
```

### 멱등성 보장이 필요한 API

| API | Idempotency Key 예시 | 중복 실행 시 위험 |
|-----|----------------------|-------------------|
| `POST /users/{id}/balance/deduct` | `order-123-deduct-balance` | 이중 결제 |
| `POST /inventories/confirm` | `order-123-confirm-stock-product-456` | 재고 이중 차감 |
| `POST /coupons/use` | `order-123-use-coupon-789` | 쿠폰 이중 사용 |
| `POST /users/{id}/balance/refund` | `order-123-refund-balance` | 이중 환불 |
| `POST /inventories/restore` | `order-123-restore-stock-product-456` | 재고 이중 복구 |

---

## 재시도 및 실패 처리

### 재시도 전략

#### 1. 일시적 장애 (Transient Failure)

**원인:** 네트워크 타임아웃, 일시적 서비스 과부하, DB 커넥션 풀 고갈

**대응:** Exponential Backoff 재시도

```kotlin
fun executeWithRetry(
    maxAttempts: Int = 3,
    initialDelay: Long = 100L,
    maxDelay: Long = 2000L,
    action: () -> Unit
) {
    var attempt = 0
    var delay = initialDelay

    while (attempt < maxAttempts) {
        try {
            action()
            return  // 성공 시 즉시 반환
        } catch (e: TransientException) {
            attempt++
            if (attempt >= maxAttempts) {
                throw SagaException("재시도 횟수 초과", e)
            }

            logger.warn("일시적 장애 발생, ${delay}ms 후 재시도 (${attempt}/${maxAttempts})")
            Thread.sleep(delay)
            delay = minOf(delay * 2, maxDelay)  // Exponential backoff
        }
    }
}
```

**재시도 가능 예외:**
- `ConnectException`, `SocketTimeoutException` (네트워크)
- `HttpServerErrorException.ServiceUnavailable` (503)
- `DataAccessResourceFailureException` (DB 일시 장애)

#### 2. 영구적 장애 (Permanent Failure)

**원인:** 잔액 부족, 재고 부족, 쿠폰 만료, 잘못된 요청 데이터

**대응:** 즉시 보상 트랜잭션 실행 (재시도 하지 않음)

```kotlin
fun handlePermanentFailure(orderId: Long, failedStep: SagaStep) {
    logger.error("영구적 장애 발생, 보상 트랜잭션 실행: orderId=$orderId, step=$failedStep")

    // 역순으로 보상 실행
    when (failedStep) {
        SagaStep.COUPON_USE -> {
            compensateInventory(orderId)
            compensateUserBalance(orderId)
            compensateOrder(orderId)
        }
        SagaStep.INVENTORY_CONFIRM -> {
            compensateUserBalance(orderId)
            compensateOrder(orderId)
        }
        SagaStep.USER_BALANCE_DEDUCT -> {
            compensateOrder(orderId)
        }
    }
}
```

**재시도 불가 예외:**
- `InsufficientBalanceException` (잔액 부족)
- `InsufficientStockException` (재고 부족)
- `CouponExpiredException` (쿠폰 만료)
- `HttpClientErrorException.BadRequest` (400)

### 보상 트랜잭션 실패 시 처리

#### Dead Letter Queue (DLQ) 패턴

보상 트랜잭션도 실패할 수 있음 → **별도 큐에 저장하여 수동 처리**:

```kotlin
@Service
class SagaCompensationService(
    private val deadLetterQueueRepository: DeadLetterQueueRepository
) {
    fun compensateWithFallback(orderId: Long, compensationStep: String, action: () -> Unit) {
        try {
            executeWithRetry(maxAttempts = 3) {
                action()
            }
            logger.info("보상 트랜잭션 성공: orderId=$orderId, step=$compensationStep")
        } catch (e: Exception) {
            logger.error("보상 트랜잭션 실패, DLQ 저장: orderId=$orderId, step=$compensationStep", e)

            // Dead Letter Queue에 저장
            deadLetterQueueRepository.save(
                DeadLetterMessage(
                    orderId = orderId,
                    step = compensationStep,
                    errorMessage = e.message,
                    createdAt = LocalDateTime.now(),
                    status = "PENDING_MANUAL_REVIEW"
                )
            )

            // 모니터링 알림 발송
            alertService.sendAlert(
                severity = "CRITICAL",
                message = "보상 트랜잭션 실패 - 수동 처리 필요: Order #$orderId"
            )
        }
    }
}
```

**DLQ 처리 프로세스:**
1. 자동 재시도 (주기적 배치 작업)
2. 수동 검토 대시보드 제공 (운영팀)
3. 수동 보상 실행 API 제공

### SAGA 상태 저장

각 SAGA 인스턴스의 진행 상태를 DB에 저장:

```kotlin
data class SagaInstance(
    val sagaId: String,  // 예: "order-123-payment-saga"
    val orderId: Long,
    val currentStep: SagaStep,
    val status: SagaStatus,  // RUNNING, COMPLETED, COMPENSATING, FAILED
    val completedSteps: List<SagaStep>,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

enum class SagaStep {
    ORDER_CREATE,
    USER_BALANCE_DEDUCT,
    INVENTORY_CONFIRM,
    COUPON_USE,
    ORDER_COMPLETE
}

enum class SagaStatus {
    RUNNING,         // 진행 중
    COMPLETED,       // 성공 완료
    COMPENSATING,    // 보상 트랜잭션 실행 중
    FAILED,          // 실패 (보상 완료)
    STUCK            // 보상 실패 (수동 처리 필요)
}
```

**활용:**
- 장애 복구 시 중단된 SAGA 재개
- 모니터링 및 디버깅
- 감사 로그 (Audit Log)

---

## 시퀀스 다이어그램

### 1. 성공 시나리오 (Happy Path)

```
Client                 OrderSagaOrchestrator   OrderService   UserService   InventoryService   CouponService
  |                              |                   |              |                 |               |
  |--processPayment()----------->|                   |              |                 |               |
  |                              |                   |              |                 |               |
  |                              |--createOrder()-->|              |                 |               |
  |                              |<--orderId: 123----|              |                 |               |
  |                              |                   |              |                 |               |
  |                              |--deductBalance(userId=1, amount=10000, key="order-123-deduct")-->|
  |                              |<--newBalance: 90000------------------------------|                 |
  |                              |                   |              |                 |               |
  |                              |--confirmReservation(sku="456", qty=2, key="order-123-confirm")-->|
  |                              |<--confirmed: true--------------------------------------------|     |
  |                              |                   |              |                 |               |
  |                              |--useCoupon(couponId=789, userId=1, key="order-123-use-coupon")-->|
  |                              |<--applied: true-------------------------------------------------------|
  |                              |                   |              |                 |               |
  |                              |--completeOrder(123)->|          |                 |               |
  |                              |<--status: PAID-------|          |                 |               |
  |                              |                   |              |                 |               |
  |<--PaymentResult: SUCCESS-----|                   |              |                 |               |
```

### 2. 실패 시나리오 (Inventory 재고 부족)

```
Client                 OrderSagaOrchestrator   OrderService   UserService   InventoryService   CouponService
  |                              |                   |              |                 |               |
  |--processPayment()----------->|                   |              |                 |               |
  |                              |                   |              |                 |               |
  |                              |--createOrder()-->|              |                 |               |
  |                              |<--orderId: 123----|              |                 |               |
  |                              |                   |              |                 |               |
  |                              |--deductBalance(userId=1, amount=10000)--------->|                 |
  |                              |<--newBalance: 90000------------------------------|                 |
  |                              |                   |              |                 |               |
  |                              |--confirmReservation(sku="456", qty=2)-------------------------->|
  |                              |<--ERROR: InsufficientStockException---------------------------|     |
  |                              |                   |              |                 |               |
  |                              | [보상 트랜잭션 시작]  |              |                 |               |
  |                              |--refundBalance(userId=1, amount=10000, key="order-123-refund")-->|
  |                              |<--refunded: true--------------------------------|                 |
  |                              |                   |              |                 |               |
  |                              |--cancelOrder(123)->|              |                 |               |
  |                              |<--status: CANCELLED|             |                 |               |
  |                              |                   |              |                 |               |
  |<--ERROR: 재고 부족으로 결제 실패--|                  |              |                 |               |
```

### 3. 보상 트랜잭션 실패 시나리오 (DLQ 처리)

```
Client                 OrderSagaOrchestrator   UserService   InventoryService   DeadLetterQueue   AlertService
  |                              |                   |              |                 |               |
  |--processPayment()----------->|                   |              |                 |               |
  |                              |                   |              |                 |               |
  |                              |--deductBalance()-->|             |                 |               |
  |                              |<--success----------|             |                 |               |
  |                              |                   |              |                 |               |
  |                              |--confirmReservation()---------->|                 |               |
  |                              |<--ERROR: 재고 부족---------------|                 |               |
  |                              |                   |              |                 |               |
  |                              | [보상 트랜잭션 시작] |              |                 |               |
  |                              |--refundBalance()-->|             |                 |               |
  |                              |<--ERROR: 503 Service Unavailable |                 |               |
  |                              |                   |              |                 |               |
  |                              | [재시도 1차]         |              |                 |               |
  |                              |--refundBalance()-->|             |                 |               |
  |                              |<--ERROR: 503-------|             |                 |               |
  |                              |                   |              |                 |               |
  |                              | [재시도 2차]         |              |                 |               |
  |                              |--refundBalance()-->|             |                 |               |
  |                              |<--ERROR: 503-------|             |                 |               |
  |                              |                   |              |                 |               |
  |                              | [재시도 3차]         |              |                 |               |
  |                              |--refundBalance()-->|             |                 |               |
  |                              |<--ERROR: 503-------|             |                 |               |
  |                              |                   |              |                 |               |
  |                              | [DLQ 저장]          |              |                 |               |
  |                              |--save(orderId=123, step="refund", status="STUCK")-->|             |
  |                              |                   |              |                 |               |
  |                              |--sendAlert("보상 트랜잭션 실패, 수동 처리 필요: Order #123")------>|
  |                              |                   |              |                 |               |
  |<--ERROR: 시스템 장애로 결제 실패, 고객센터 문의 바랍니다-------------------------|               |
```

---

## 구현 우선순위

### Phase 1: 설계 문서 및 개념 증명 (STEP 16 제출)

- [✅] 현재 시스템 분석
- [✅] 분산 트랜잭션 문제점 식별
- [✅] SAGA 패턴 설계
- [✅] 보상 트랜잭션 설계
- [ ] 코드 구현 (간단한 Orchestrator 프로토타입)

### Phase 2: 실제 MSA 전환 (미래 작업)

- [ ] 서비스 분리 (Order, User, Inventory, Coupon)
- [ ] 독립 DB 구성
- [ ] HTTP 클라이언트 구현 (RestTemplate, WebClient)
- [ ] 멱등성 키 저장소 구현
- [ ] DLQ 및 모니터링 시스템 구축
- [ ] 통합 테스트 (WireMock으로 외부 서비스 모킹)

---

## 결론

### 트랜잭션 분리에 따른 문제점

1. **부분 성공:** 일부 서비스만 성공하고 나머지 실패 시 데이터 불일치
2. **네트워크 타임아웃:** 응답 손실로 인한 불확실한 상태
3. **서비스 장애 전파:** 한 서비스 장애가 전체 시스템 중단으로 이어짐
4. **보상 트랜잭션 실패:** 롤백 자체도 실패할 수 있음

### SAGA 패턴 해결 방안

1. **Orchestration 패턴:** 중앙 Orchestrator가 모든 서비스 호출 및 보상 관리
2. **보상 트랜잭션:** 실패 시 역순으로 이전 단계 취소
3. **멱등성 보장:** Idempotency Key로 중복 실행 방지
4. **재시도 및 DLQ:** 일시적 장애는 재시도, 영구적 실패는 수동 처리

### 최종 일관성 (Eventual Consistency)

MSA 환경에서는 **강한 일관성(Strong Consistency)** 대신 **최종 일관성(Eventual Consistency)**을 수용해야 합니다:

- 일시적으로 데이터 불일치 가능 (예: 주문은 성공했지만 재고 차감 지연)
- 시간이 지나면 모든 서비스가 일관된 상태로 수렴
- 비즈니스 로직으로 불일치 허용 범위 정의 (예: 재고 차감은 3초 이내 완료 보장)

### 트레이드오프

| 항목 | 모놀리식 (현재) | MSA (SAGA 적용) |
|------|----------------|-----------------|
| **일관성** | 강한 일관성 (ACID) | 최종 일관성 (BASE) |
| **성능** | 긴 트랜잭션 → 락 증가 | 짧은 로컬 트랜잭션 → 락 감소 |
| **확장성** | 수직 확장만 가능 | 수평 확장 가능 (서비스별) |
| **장애 격리** | 한 도메인 장애 → 전체 실패 | 서비스별 독립 장애 처리 |
| **복잡도** | 낮음 | 높음 (Orchestrator, 보상 로직) |
| **운영 부담** | 낮음 | 높음 (DLQ 모니터링, 수동 복구) |

---

## 참고 자료

- [SAGA Pattern - Microsoft Docs](https://learn.microsoft.com/en-us/azure/architecture/reference-architectures/saga/saga)
- [Designing Data-Intensive Applications (Martin Kleppmann)](https://dataintensive.net/)
- [Microservices Patterns (Chris Richardson)](https://microservices.io/patterns/data/saga.html)
