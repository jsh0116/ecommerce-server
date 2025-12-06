# Architecture Documentation

## 개요

이 문서는 hhplus-ecommerce 프로젝트의 아키텍처 리팩토링 내역을 정리합니다.

**리팩토링 기간**: 2024-12-06
**주요 목표**: Clean Architecture 원칙 적용 및 Repository 패턴 도입

---

## 리팩토링 개요

### Phase 1: 초기 구조 분석 (완료)
프로젝트의 기존 구조를 분석하고 리팩토링 계획 수립

### Phase 2: UseCase 레이어 리팩토링 (완료)
**목표**: UseCase들이 Repository가 아닌 Service에 의존하도록 변경

**변경된 UseCases**:
- `CouponUseCase`
- `ProductUseCase`
- ~~`InventoryUseCase`~~ (Repository 직접 사용 유지)

### Phase 3: Payment 도메인 Repository 패턴 적용 (완료)
**목표**: PaymentService를 Domain과 Infrastructure로 분리

**생성된 파일**:
- `domain/Payment.kt` - 순수 도메인 모델
- `infrastructure/repositories/PaymentRepository.kt` - Repository 인터페이스
- `infrastructure/repositories/PaymentRepositoryImpl.kt` - Repository 구현체
- 리팩토링된 `application/services/PaymentService.kt` - 비즈니스 로직

### Phase 4: 통합 테스트 및 문서화 (완료)
**목표**: 전체 시스템 검증 및 문서화

**추가된 테스트**:
- `OrderFlowE2ETest.kt` - E2E 주문 플로우 통합 테스트
- `PaymentRepositoryIntegrationTest.kt` - PaymentRepository 통합 테스트

---

## 아키텍처 원칙

### 1. Clean Architecture 레이어

```
presentation/          → Presentation Layer (REST Controllers)
    └── controllers/

application/           → Application Layer (Use Cases & Services)
    ├── usecases/     → 워크플로우 조율
    └── services/     → 비즈니스 로직

domain/                → Domain Layer (비즈니스 엔티티 & 규칙)
    ├── Payment.kt
    ├── Product.kt
    └── ...

infrastructure/        → Infrastructure Layer (외부 시스템 연동)
    ├── repositories/ → 데이터 접근
    └── persistence/  → JPA Entities & Repositories
```

### 2. 의존성 방향

```
Controller → UseCase → Service → Repository (Interface) → Repository (Implementation)
                ↓         ↓
             Domain    Domain
```

**핵심 원칙**:
- 상위 레이어는 하위 레이어에 의존
- Domain은 어떤 레이어에도 의존하지 않음
- Infrastructure는 Domain 인터페이스를 구현

---

## 주요 리팩토링 내역

### Phase 2: UseCase → Service 의존성 변경

#### Before (문제점)
```kotlin
@Service
class CouponUseCase(
    private val couponRepository: CouponRepository,  // Repository 직접 의존
    private val userRepository: UserRepository
)
```

**문제**:
- UseCase가 Infrastructure 레이어에 직접 의존
- 비즈니스 로직이 UseCase와 Repository에 분산
- 테스트 시 Repository를 mock해야 함

#### After (개선)
```kotlin
@Service
class CouponUseCase(
    private val couponService: CouponService,  // Service에 의존
    private val userService: UserService,
    private val distributedLockService: DistributedLockService,
    private val couponIssuanceService: CouponIssuanceService
)
```

**개선점**:
- ✅ UseCase가 Service 레이어에 의존
- ✅ 비즈니스 로직이 Service에 집중
- ✅ 테스트 용이성 향상

#### 적용된 UseCases
1. **CouponUseCase**
   - `CouponRepository`, `UserRepository` → `CouponService`, `UserService`
   - 쿠폰 발급, 검증 로직을 Service로 위임

2. **ProductUseCase**
   - `ProductRepository` → `ProductService`
   - 상품 조회, 인기 상품 계산 로직을 Service로 위임

3. **InventoryUseCase**
   - 변경 없음 (기존 InventoryService가 JPA-specific이므로 Repository 유지)

---

### Phase 3: Payment 도메인 Repository 패턴 적용

#### 1. Payment 도메인 모델 생성

**파일**: `domain/Payment.kt`

```kotlin
data class Payment(
    val id: Long = 0L,
    val orderId: Long,
    val idempotencyKey: String,
    val method: PaymentMethod,
    var status: PaymentStatus = PaymentStatus.PENDING,
    val amount: Long,
    ...
) {
    fun approve(transactionId: String): Payment
    fun fail(failReason: String, pgCode: String? = null): Payment
    fun refund(): Payment
    fun isApproved(): Boolean
}
```

**특징**:
- Infrastructure 의존성 없음 (JPA 어노테이션 없음)
- 불변성 선호 (`val` 사용)
- 비즈니스 로직 포함 (`approve`, `fail`, `refund`)
- `companion object`에 팩토리 메서드 (`create`)

#### 2. PaymentRepository 인터페이스

**파일**: `infrastructure/repositories/PaymentRepository.kt`

```kotlin
interface PaymentRepository {
    fun save(payment: Payment): Payment
    fun saveInNewTransaction(payment: Payment): Payment
    fun findByIdempotencyKey(idempotencyKey: String): Payment?
    fun findByOrderId(orderId: Long): Payment?
    fun findById(id: Long): Payment?
    fun <T> withDistributedLock(
        idempotencyKey: String,
        waitTime: Long = 60L,
        holdTime: Long = 30L,
        block: () -> T
    ): T
}
```

**특징**:
- `infrastructure/repositories/`에 위치 (프로젝트 컨벤션)
- 도메인 모델(`Payment`)을 반환
- 분산 락 추상화 포함

#### 3. PaymentRepositoryImpl 구현체

**파일**: `infrastructure/repositories/PaymentRepositoryImpl.kt`

```kotlin
@Repository
class PaymentRepositoryImpl(
    private val paymentJpaRepository: PaymentJpaRepository,
    private val distributedLockService: DistributedLockService
) : PaymentRepository {

    override fun save(payment: Payment): Payment {
        val entity = toEntity(payment)
        val saved = paymentJpaRepository.save(entity)
        return toDomain(saved)
    }

    private fun toEntity(payment: Payment): PaymentJpaEntity { ... }
    private fun toDomain(entity: PaymentJpaEntity): Payment { ... }
}
```

**역할**:
- JPA Repository와 통신
- `PaymentJpaEntity` ↔ `Payment` 도메인 모델 변환
- 트랜잭션 관리
- 분산 락 관리

#### 4. PaymentService 리팩토링

**Before**:
```kotlin
@Service
class PaymentService(
    private val paymentRepository: PaymentJpaRepository,  // JPA 직접 의존
    private val distributedLockService: DistributedLockService
) {
    fun processPayment(...): PaymentJpaEntity  // JPA Entity 반환
}
```

**After**:
```kotlin
@Service
class PaymentService(
    private val paymentRepository: PaymentRepository  // Repository 인터페이스에 의존
) {
    fun processPayment(...): Payment {  // 도메인 모델 반환
        return paymentRepository.withDistributedLock(idempotencyKey) {
            val existingPayment = paymentRepository.findByIdempotencyKeyInNewTransaction(idempotencyKey)
            if (existingPayment != null) return@withDistributedLock existingPayment

            val newPayment = Payment.create(orderId, idempotencyKey, method, amount)
            paymentRepository.saveInNewTransaction(newPayment)
        }
    }
}
```

**개선점**:
- ✅ JPA 의존성 제거
- ✅ 도메인 모델 반환
- ✅ 비즈니스 로직에 집중
- ✅ 분산 락이 Repository로 추상화됨

---

## 아키텍처 다이어그램

### Before (기존 구조)

```
┌─────────────────┐
│  Controller     │
└────────┬────────┘
         │
┌────────▼────────┐
│  UseCase        │
└────────┬────────┘
         │
┌────────▼────────────────┐
│  JPA Repository         │
│  (Infrastructure)       │
└─────────────────────────┘
         │
┌────────▼────────┐
│  Database       │
└─────────────────┘
```

**문제**:
- UseCase가 Infrastructure에 직접 의존
- 비즈니스 로직 분산
- 도메인 모델 부재

### After (리팩토링 후)

```
┌─────────────────┐
│  Controller     │
│  (Presentation) │
└────────┬────────┘
         │
┌────────▼────────┐
│  UseCase        │
│  (Application)  │
└────────┬────────┘
         │
┌────────▼────────┐
│  Service        │◄────────┐
│  (Application)  │         │
└────────┬────────┘         │
         │              ┌───┴──────┐
┌────────▼────────┐     │  Domain  │
│  Repository     │     │  Models  │
│  (Interface)    │     └──────────┘
└────────┬────────┘
         │
┌────────▼──────────────┐
│  RepositoryImpl       │
│  (Infrastructure)     │
└────────┬──────────────┘
         │
┌────────▼────────┐
│  JPA Repository │
└────────┬────────┘
         │
┌────────▼────────┐
│  Database       │
└─────────────────┘
```

**개선**:
- ✅ 레이어 분리 명확화
- ✅ 도메인 모델 중심 설계
- ✅ Repository 패턴으로 데이터 접근 추상화

---

## 테스트 전략

### Phase 4에서 추가한 테스트

#### 1. OrderFlowE2ETest (E2E 통합 테스트)

**테스트 시나리오**:
- ✅ 정상적인 주문 플로우 (주문 생성 → 결제 → 주문 완료)
- ✅ 재고 부족 시 주문 생성 실패
- ✅ 잔액 부족 테스트
- ✅ 멱등성 키로 중복 결제 요청 검증
- ✅ 여러 상품 주문 및 결제
- ✅ 주문 취소 시 상태 변경

**특징**:
- TestContainers를 사용한 실제 MySQL & Redis 테스트
- 전체 시스템 통합 검증
- 실제 비즈니스 시나리오 재현

#### 2. PaymentRepositoryIntegrationTest

**테스트 시나리오**:
- ✅ 결제 저장 및 조회
- ✅ 멱등성 키로 결제 조회
- ✅ 주문 ID로 결제 조회
- ✅ 결제 상태 변경 (승인, 실패, 환불)
- ✅ 새 트랜잭션에서 결제 저장
- ✅ 중복 멱등성 키 처리
- ✅ 분산 락을 사용한 동시성 제어
- ✅ Entity ↔ Domain 변환 검증
- ✅ 다양한 결제 수단 테스트

**특징**:
- Repository 레이어의 실제 동작 검증
- 데이터베이스 통합 테스트
- 동시성 제어 검증

---

## 테스트 결과

### 전체 테스트 실행 결과

```bash
./gradlew test testIntegration
```

**결과**: ✅ BUILD SUCCESSFUL

- 단위 테스트: 통과
- 통합 테스트: 통과
- E2E 테스트: 통과

---

## 추가 개선 사항

### 1. Service 레이어 강화

**적용된 Services**:
- `CouponService`: 쿠폰 비즈니스 로직
  - `save()`, `saveUserCoupon()` 추가
  - `getById()`, `validateUserCoupon()` 등

- `ProductService`: 상품 비즈니스 로직
  - `save()`, `getByIdOrNull()` 추가
  - `findAll()`, `findTopSelling()` 등

### 2. 도메인 모델 개선

**Payment 도메인**:
- `PaymentMethod`, `PaymentStatus` enum 추가
- 비즈니스 로직 메서드 (`approve`, `fail`, `refund`)
- 불변성 및 data class 활용

### 3. Repository 패턴

**PaymentRepository**:
- 인터페이스로 데이터 접근 추상화
- 도메인 모델 중심 API
- 분산 락 추상화 (`withDistributedLock`)

---

## 향후 개선 방향

### 1. 다른 도메인에도 Repository 패턴 적용
- `OrderRepository` 인터페이스 생성
- `ProductRepository` 인터페이스 생성
- `CouponRepository` 인터페이스 생성

### 2. 도메인 모델 강화
- 더 많은 비즈니스 로직을 도메인 모델로 이동
- Value Objects 도입 (Money, OrderStatus 등)

### 3. CQRS 패턴 고려
- Command와 Query 분리
- 읽기 전용 모델 최적화

### 4. Event-Driven Architecture
- 도메인 이벤트 도입
- 비동기 처리 개선

---

## 결론

### 달성한 목표
- ✅ Clean Architecture 레이어 분리
- ✅ Repository 패턴 도입
- ✅ 도메인 모델 중심 설계
- ✅ 테스트 커버리지 향상
- ✅ 비즈니스 로직 집중화

### 개선 효과
- **유지보수성**: 레이어 분리로 변경 영향 범위 최소화
- **테스트 용이성**: Repository 인터페이스로 mock 테스트 간편화
- **확장성**: 새로운 요구사항 추가 시 레이어별 독립적 확장 가능
- **가독성**: 비즈니스 로직이 명확히 분리되어 코드 이해 향상

---

**Last Updated**: 2024-12-06
**Version**: 1.0.0
