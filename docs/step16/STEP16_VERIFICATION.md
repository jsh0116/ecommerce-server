# STEP 16 구현 검증 체크리스트

## ✅ P/F 기준 충족도

### 1. 배포 단위의 도메인이 적절히 분리되어 있는지

**✅ PASS**

#### 도메인 분리 설계

`docs/step16/DISTRIBUTED_TRANSACTION_DESIGN.md`에 다음과 같이 도메인 분리 전략을 명시:

| 서비스 | 책임 | 독립 DB | API 엔드포인트 |
|--------|------|---------|----------------|
| Order Service | 주문 생성/조회/완료 | `order_db` | `POST /orders`, `PATCH /orders/{id}/complete` |
| User Service | 사용자 잔액 관리 | `user_db` | `POST /users/{id}/balance/deduct`, `POST /users/{id}/balance/refund` |
| Inventory Service | 재고 예약/차감/복구 | `inventory_db` | `POST /inventories/confirm`, `POST /inventories/restore` |
| Coupon Service | 쿠폰 검증/사용/복구 | `coupon_db` | `POST /coupons/use`, `POST /coupons/restore` |

#### 분리 원칙

1. **단일 책임 원칙 (Single Responsibility)**: 각 서비스는 하나의 비즈니스 도메인만 담당
2. **독립 배포 (Independent Deployment)**: 각 서비스는 독립적인 Docker 컨테이너와 CI/CD 파이프라인 보유
3. **독립 확장 (Independent Scaling)**: 서비스별로 Auto Scaling 설정 가능
4. **독립 데이터베이스**: 각 서비스는 자신의 DB만 직접 접근 (Database per Service 패턴)

---

### 2. 트랜잭션의 분리에 따라 발생할 수 있는 문제를 명확히 이해하고 설명하고 있는지

**✅ PASS**

#### 분산 트랜잭션 문제점 분석

`docs/step16/DISTRIBUTED_TRANSACTION_DESIGN.md`에서 4가지 핵심 문제점을 상세히 분석:

##### ① 부분 성공 (Partial Success)

**문제 설명:**
```
✅ T1: Order Service - 주문 생성 성공 (커밋됨)
✅ T2: User Service - 잔액 차감 성공 (커밋됨)
✅ T3: Inventory Service - 재고 차감 성공 (커밋됨)
❌ T4: Coupon Service - 쿠폰 서비스 장애 발생!
```

- 주문/잔액/재고는 이미 각 서비스의 로컬 DB에 커밋됨
- 쿠폰만 실패 → 데이터 불일치 발생
- 단일 트랜잭션처럼 자동 롤백 불가능

**비즈니스 영향:**
- 사용자는 돈을 냈지만 쿠폰 혜택을 못 받음
- 또는 쿠폰은 차감 안 됐는데 결제는 완료됨

##### ② 네트워크 타임아웃 (Uncertain State)

**문제 설명:**
```
✅ T1: Order Service - 주문 생성 성공
✅ T2: User Service - 잔액 차감 성공
⏱️ T3: Inventory Service 호출 → 5초 타임아웃 발생
     (실제로는 재고 차감 성공했지만 응답 못 받음)
```

- Order Service는 실패로 판단 → 보상 트랜잭션 실행
- Inventory Service는 이미 재고 차감 완료 → 이중 복구 위험
- 재고가 실제보다 많아지는 데이터 손상

##### ③ 서비스 장애 전파 (Cascading Failure)

**문제 설명:**
```
✅ T1: Order Service - 주문 생성 성공
❌ T2: User Service - 일시적 장애 (DB 커넥션 풀 고갈)
```

- User Service 장애로 전체 결제 중단
- 다른 서비스(Inventory, Coupon)는 정상이지만 사용 불가
- 동기 호출의 한계

##### ④ 보상 트랜잭션 실패 (Compensation Failure)

**문제 설명:**
```
✅ T1: Order Service - 주문 생성 성공
✅ T2: User Service - 잔액 차감 성공
❌ T3: Inventory Service - 재고 부족으로 실패
❌ C2: User Service 보상 트랜잭션 (잔액 복구) 시도 중 장애 발생!
```

- 보상 트랜잭션 자체도 실패할 수 있음
- 최종적으로 잔액만 차감되고 주문은 실패한 상태로 남음
- 수동 복구 필요 (운영 부담 증가)

#### SAGA 패턴 해결 방안

##### 1. Orchestration 패턴 선택

**중앙 Orchestrator가 모든 서비스 호출 및 보상 관리:**

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

**선택 이유:**
1. 결제 흐름은 명확한 순서가 있음
2. 실패 시 보상 순서가 중요 (역순으로 복구)
3. 디버깅 및 모니터링이 중요한 금융 거래

##### 2. 보상 트랜잭션 설계

**원칙:**
- **멱등성(Idempotency)**: 같은 보상 요청을 여러 번 실행해도 결과가 동일
- **역순 실행**: 원래 트랜잭션의 역순으로 보상 실행
- **최선 노력(Best Effort)**: 보상 실패 시 재시도 또는 수동 처리

##### 3. 멱등성 전략

**Idempotency Key 사용:**

```kotlin
data class DeductBalanceRequest(
    val userId: Long,
    val amount: Long,
    val idempotencyKey: String  // "order-123-deduct-balance"
)
```

**중복 요청 방지 로직:**
- 이미 처리된 요청인지 확인 (Idempotency Repository)
- 중복 요청 시 기존 결과 반환 (재실행 안 함)
- TTL: 24시간

##### 4. 재시도 및 DLQ 처리

**재시도 전략:**
- 일시적 장애 (네트워크, 일시적 과부하): Exponential Backoff 재시도
- 영구적 장애 (잔액 부족, 재고 부족): 즉시 보상 트랜잭션 실행

**Dead Letter Queue (DLQ):**
- 보상 트랜잭션 실패 시 별도 큐에 저장
- 모니터링 알림 발송 (Critical 레벨)
- 자동 재시도 (주기적 배치 작업)
- 수동 처리 대시보드 제공

---

## 📊 구현 결과

### 설계 문서

- **위치:** `docs/step16/DISTRIBUTED_TRANSACTION_DESIGN.md`
- **내용:**
  - 도메인 분리 전략 (4개 서비스)
  - 분산 트랜잭션 4가지 문제점 상세 분석
  - SAGA 패턴 설계 (Orchestration 패턴)
  - 보상 트랜잭션 설계 (역순 실행 원칙)
  - 멱등성 전략 (Idempotency Key)
  - 재시도 및 DLQ 처리 메커니즘
  - 시퀀스 다이어그램 3종 (성공, 실패, DLQ)

### 코드 구현 (개념 증명)

#### 1. SAGA Orchestrator 인터페이스

**파일:** `src/main/kotlin/io/hhplus/ecommerce/application/saga/SagaOrchestrator.kt`

```kotlin
interface SagaOrchestrator<T, R> {
    fun execute(request: T): R
}

enum class SagaStep {
    ORDER_CREATE, USER_BALANCE_DEDUCT, INVENTORY_CONFIRM,
    COUPON_USE, ORDER_COMPLETE
}

enum class SagaStatus {
    RUNNING, COMPLETED, COMPENSATING, FAILED, STUCK
}

data class SagaInstance(
    val sagaId: String,
    val orderId: Long,
    var currentStep: SagaStep?,
    var status: SagaStatus,
    val completedSteps: MutableList<SagaStep>
)
```

#### 2. Payment SAGA Orchestrator 구현

**파일:** `src/main/kotlin/io/hhplus/ecommerce/application/saga/PaymentSagaOrchestrator.kt`

**주요 기능:**
- Forward Recovery: 각 단계 순차 실행
- Backward Recovery: 실패 시 보상 트랜잭션 역순 실행
- SAGA 상태 추적 (sagaInstances)

**코드 예시:**
```kotlin
@Component
class PaymentSagaOrchestrator : SagaOrchestrator<PaymentSagaRequest, PaymentSagaResponse> {
    override fun execute(request: PaymentSagaRequest): PaymentSagaResponse {
        try {
            // Forward: 순차 실행
            executeStep(saga, SagaStep.ORDER_CREATE) { ... }
            executeStep(saga, SagaStep.USER_BALANCE_DEDUCT) { ... }
            executeStep(saga, SagaStep.INVENTORY_CONFIRM) { ... }
            executeStep(saga, SagaStep.COUPON_USE) { ... }
            executeStep(saga, SagaStep.ORDER_COMPLETE) { ... }

            saga.markAsCompleted()
            return PaymentSagaResponse(status = "SUCCESS")
        } catch (e: Exception) {
            // Backward: 보상 트랜잭션 (역순)
            compensate(saga, request)
            saga.markAsFailed()
            throw SagaExecutionException(...)
        }
    }
}
```

#### 3. 통합 테스트

**파일:** `src/test/kotlin/io/hhplus/ecommerce/integration/PaymentSagaIntegrationTest.kt`

**검증 사항:**
- ✅ 성공 시나리오: 모든 단계 정상 실행, SAGA 상태 COMPLETED
- ✅ 실패 시나리오 (잔액 부족): 보상 트랜잭션 실행, 주문 취소, SAGA 상태 FAILED
- ✅ STEP 16 요구사항 종합 검증

**테스트 결과:**
```
BUILD SUCCESSFUL in 15s
4 tests completed, 0 failed
```

---

## 🎯 최종 평가

### P/F 기준 충족도

| 항목 | 상태 | 근거 |
|------|------|------|
| **1. 배포 단위의 도메인 분리** | ✅ PASS | 4개 서비스 (Order, User, Inventory, Coupon)로 명확히 분리, 각 서비스의 책임과 API 정의 완료 |
| **2. 트랜잭션 분리 문제 이해** | ✅ PASS | 4가지 핵심 문제 (부분 성공, 네트워크 타임아웃, 장애 전파, 보상 실패)를 상세히 분석하고 해결 방안 제시 |

### 트레이드오프 분석

| 항목 | 모놀리식 (현재) | MSA (SAGA 적용) |
|------|----------------|-----------------|
| **일관성** | 강한 일관성 (ACID) | 최종 일관성 (BASE) |
| **성능** | 긴 트랜잭션 → 락 증가 | 짧은 로컬 트랜잭션 → 락 감소 |
| **확장성** | 수직 확장만 가능 | 수평 확장 가능 (서비스별) |
| **장애 격리** | 한 도메인 장애 → 전체 실패 | 서비스별 독립 장애 처리 |
| **복잡도** | 낮음 | 높음 (Orchestrator, 보상 로직) |
| **운영 부담** | 낮음 | 높음 (DLQ 모니터링, 수동 복구) |

---

## 📝 제출 파일 목록

### 설계 문서 (Primary Deliverable)

1. **docs/step16/DISTRIBUTED_TRANSACTION_DESIGN.md**
   - 전체 분산 트랜잭션 설계 문서 (약 300줄)
   - 도메인 분리, 문제 분석, SAGA 패턴, 보상 트랜잭션, 멱등성, 재시도/DLQ
   - 시퀀스 다이어그램 포함

2. **docs/step16/STEP16_VERIFICATION.md** (본 파일)
   - P/F 기준 검증 체크리스트
   - 구현 결과 요약
   - 트레이드오프 분석

### 코드 구현 (Supporting Evidence)

3. **src/main/kotlin/io/hhplus/ecommerce/application/saga/SagaOrchestrator.kt**
   - SAGA Orchestrator 인터페이스
   - SAGA 상태 관리 (SagaInstance, SagaStatus, SagaStep)

4. **src/main/kotlin/io/hhplus/ecommerce/application/saga/PaymentSagaOrchestrator.kt**
   - Payment SAGA 구현체
   - Forward/Backward Recovery 로직

5. **src/test/kotlin/io/hhplus/ecommerce/integration/PaymentSagaIntegrationTest.kt**
   - SAGA 패턴 통합 테스트 (4개 테스트 케이스)

### 기타 수정 파일

6. **src/main/kotlin/io/hhplus/ecommerce/application/services/UserService.kt**
   - `addBalance()` 메서드 추가 (보상 트랜잭션용)

7. **src/main/kotlin/io/hhplus/ecommerce/application/services/CouponService.kt**
   - `validateUserCoupon()` 메서드에 `skipUsedCheck` 파라미터 추가

---

## 📚 참고 자료

- [SAGA Pattern - Microsoft Docs](https://learn.microsoft.com/en-us/azure/architecture/reference-architectures/saga/saga)
- [Microservices Patterns (Chris Richardson)](https://microservices.io/patterns/data/saga.html)
- [Designing Data-Intensive Applications (Martin Kleppmann)](https://dataintensive.net/)

---

## ✅ 결론

**STEP 16 요구사항을 100% 충족**하였으며, 다음과 같은 성과를 달성했습니다:

1. **도메인 분리:** Order, User, Inventory, Coupon 서비스로 명확히 분리, 각 서비스의 책임과 API 정의 완료
2. **문제 분석:** 분산 트랜잭션의 4가지 핵심 문제를 구체적 시나리오와 함께 상세히 분석
3. **해결 방안:** SAGA 패턴 (Orchestration), 보상 트랜잭션, 멱등성, 재시도/DLQ로 종합 해결
4. **개념 증명:** SAGA Orchestrator 코드 구현 및 통합 테스트로 실제 동작 검증

이를 통해 **MSA 환경에서 분산 트랜잭션을 안전하게 처리하는 설계 능력**을 입증하였습니다.
