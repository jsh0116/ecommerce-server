# Service Layer 설계 가이드

이 문서는 프로젝트의 Service Layer 아키텍처 패턴과 설계 철학을 설명합니다.

## 목차
1. [Domain Service vs Entity Service 비교](#domain-service-vs-entity-service-비교)
2. [Validator/Executor/Publisher 패턴](#validatorexecutorpublisher-패턴)
3. [적용된 서비스 목록](#적용된-서비스-목록)

---

## Domain Service vs Entity Service 비교

### OrderService vs OrderCreationService 사례

#### 📦 OrderService (Entity Service - 단일 도메인)

```kotlin
@Service
class OrderService(
    private val orderRepository: OrderRepository  // Repository만 의존
) {
    // ✅ Order 엔티티에 집중한 단순 비즈니스 로직
    fun createOrder(user: User, items: List<OrderItem>, coupon: UserCoupon?): Order
    fun getById(orderId: Long): Order
    fun completeOrder(orderId: Long): Order
    fun cancelOrder(orderId: Long, userId: Long): Order
}
```

**역할:**
- Order 도메인 엔티티의 **CRUD 및 상태 관리**
- 할인 금액 계산, 주문 엔티티 생성
- **단일 책임**: Order 데이터에만 집중

**특징:**
- ❌ 다른 도메인(User, Product, Inventory, Coupon)을 모름
- ❌ 재고 예약, 쿠폰 검증 같은 외부 로직 처리 불가
- ✅ 순수하게 Order 엔티티만 다룸
- ✅ 여러 곳에서 재사용 가능

---

#### 🎯 OrderCreationService (Domain Service - 다중 도메인 조율)

```kotlin
@Service
class OrderCreationService(
    private val orderValidator: OrderValidator,      // User, Product, Coupon 검증
    private val orderExecutor: OrderExecutor,        // Inventory, Order 실행
    private val orderEventPublisher: OrderEventPublisher
) {
    // ✅ 여러 도메인을 조율하는 복잡한 워크플로우
    @Transactional
    fun createOrder(userId: Long, items: List<OrderItemRequest>, couponId: Long?): Order {
        // 1. 사용자 검증 (User 도메인)
        val user = orderValidator.validateUser(userId)

        // 2. 상품 검증 및 재고 예약 (Product + Inventory 도메인)
        val orderItems = items.map { req ->
            val product = orderValidator.validateProduct(req.productId)
            orderExecutor.reserveStockAndCreateOrderItem(product, req.quantity)
        }

        // 3. 쿠폰 검증 (Coupon 도메인)
        val userCoupon = orderValidator.validateCoupon(userId, couponId)

        // 4. 주문 생성 (Order 도메인)
        return orderExecutor.createOrder(user, orderItems, userCoupon)
    }
}
```

**역할:**
- **여러 도메인을 조율**하여 주문 생성 프로세스 완성
- User → Product → Inventory → Coupon → Order 전체 흐름 관리
- **복잡한 비즈니스 워크플로우** 캡슐화

**특징:**
- ✅ 4개 이상의 도메인 조율 (User, Product, Inventory, Coupon, Order)
- ✅ 트랜잭션 경계 내에서 복잡한 비즈니스 로직 실행
- ✅ UseCase의 복잡도를 낮춤
- ✅ Validator/Executor/Publisher 패턴 적용 가능

---

### 🎭 두 서비스로 나눈 이유

#### 1. Single Responsibility Principle (단일 책임 원칙)

| 서비스 | 책임 |
|--------|------|
| **OrderService** | Order 엔티티의 CRUD만 책임 |
| **OrderCreationService** | 주문 생성 **프로세스 전체**를 책임 |

만약 OrderService에 모든 로직을 넣으면:
```kotlin
// ❌ 안 좋은 예: OrderService가 너무 많은 책임
class OrderService(
    private val orderRepository,
    private val userService,
    private val productService,
    private val inventoryService,  // 너무 많은 의존성!
    private val couponService
) {
    fun createOrderWithEverything(...) {
        // 100줄 이상의 복잡한 로직...
        // God Object 안티패턴!
    }
}
```

---

#### 2. Domain Service Pattern (도메인 서비스 패턴)

DDD에서는 **여러 도메인을 조율하는 복잡한 로직**을 Domain Service로 분리합니다:

```
[UseCase] → [Domain Service] → [여러 Entity Service]
                ↓
        OrderCreationService
                ↓
    ┌───────────┼───────────┐
    ↓           ↓           ↓
UserService  InventoryService  OrderService
```

**OrderCreationService = 여러 도메인을 조율하는 Domain Service**

---

#### 3. 재사용성과 확장성

**OrderService는 다른 곳에서도 재사용 가능:**
```kotlin
// PaymentProcessingService에서도 OrderService 사용
class PaymentExecutor(
    private val orderService: OrderService
) {
    fun completeOrder(orderId: Long): Order {
        return orderService.completeOrder(orderId)  // 재사용
    }
}
```

**OrderCreationService는 주문 생성 전용 워크플로우:**
```kotlin
// UseCase에서만 주문 생성 워크플로우 호출
class OrderUseCase(
    private val orderCreationService: OrderCreationService
) {
    fun createOrder(...) = orderCreationService.createOrder(...)
}
```

---

#### 4. 작업별 책임 분담

| 작업 | OrderService | OrderCreationService |
|------|--------------|----------------------|
| 주문 엔티티만 생성 | ✅ `createOrder(user, items, coupon)` | - |
| 재고 예약 포함 주문 생성 | ❌ 불가능 | ✅ `createOrder(userId, items, couponId)` |
| 주문 조회 | ✅ `getById(orderId)` | - |
| 주문 완료 처리 | ✅ `completeOrder(orderId)` | - |
| 주문 취소 + 재고 복구 | ❌ 재고 복구 불가 | ✅ `cancelOrder(orderId, userId)` |

---

## Validator/Executor/Publisher 패턴

Medium 아티클에서 제안한 **책임 분리 패턴**을 적용하여 Domain Service의 복잡도를 관리합니다.

### 패턴 구성 요소

```kotlin
// Domain Service 구조
@Service
class SomeDomainService(
    private val validator: SomeValidator,           // 검증 로직
    private val executor: SomeExecutor,             // 실행 로직
    private val eventPublisher: SomeEventPublisher, // 이벤트 발행
    private val cacheManager: SomeCacheManager?     // 캐시 관리 (옵션)
)
```

#### 1. Validator (검증 컴포넌트)

**책임:** 비즈니스 규칙 검증만 수행

```kotlin
@Component
class PaymentValidator {
    fun validateOrderOwnership(order: Order, userId: Long)
    fun validatePayableStatus(order: Order)
    fun validatePayment(order: Order, userId: Long) // 편의 메서드
}
```

**특징:**
- ✅ 검증 로직만 집중 (Single Responsibility)
- ✅ 독립적으로 테스트 가능
- ✅ 검증 규칙 변경 시 Validator만 수정

---

#### 2. Executor (실행 컴포넌트)

**책임:** 실제 비즈니스 로직 실행

```kotlin
@Component
class PaymentExecutor(
    private val userService: UserService,
    private val inventoryService: InventoryService,
    private val couponService: CouponService,
    private val orderService: OrderService
) {
    fun deductBalance(userId: Long, amount: Long): User
    fun confirmInventoryAndUpdateSales(order: Order)
    fun useCouponIfPresent(userId: Long, couponId: Long?)
    fun completeOrder(orderId: Long): Order
}
```

**특징:**
- ✅ 실행 로직만 집중
- ✅ 여러 서비스 조율 가능
- ✅ 트랜잭션 내에서 실행

---

#### 3. EventPublisher (이벤트 발행 컴포넌트)

**책임:** 도메인 이벤트 발행

```kotlin
@Component
class PaymentEventPublisher(
    private val eventPublisher: ApplicationEventPublisher
) {
    fun publishOrderPaidEvent(order: Order) {
        eventPublisher.publishEvent(OrderPaidEvent.from(order))
    }
}
```

**특징:**
- ✅ 이벤트 발행만 집중
- ✅ 이벤트 타입 변경 시 Publisher만 수정
- ✅ 비동기 처리와 분리

---

#### 4. CacheManager (캐시 관리 컴포넌트) - 옵션

**책임:** 캐시 전략 관리

```kotlin
@Component
class CouponCacheManager(
    private val redisTemplate: RedisTemplate<String, String>
) {
    fun getCouponStatus(couponId: Long): CouponStatus
    fun incrementIssuedCount(couponId: Long): Long
    fun invalidateCache(couponId: Long)
}
```

**특징:**
- ✅ 캐시 키 전략 캡슐화
- ✅ Redis/Memcached 전환 시 CacheManager만 수정
- ✅ TTL, Eviction 정책 중앙 관리

---

### 패턴 적용 전후 비교

#### ❌ 패턴 적용 전 (113줄)

```kotlin
@Service
class PaymentProcessingService(
    private val orderService: OrderService,
    private val userService: UserService,
    private val inventoryService: InventoryService,
    private val couponService: CouponService,
    private val productRankingService: ProductRankingService,
    private val eventPublisher: ApplicationEventPublisher
) {
    @Transactional
    fun processPayment(orderId: Long, userId: Long): PaymentResult {
        // 1. 주문 검증 (10줄)
        val order = orderService.getById(orderId)
        if (order.userId != userId) {
            throw OrderException.UnauthorizedOrderAccess()
        }
        if (!order.canPay()) {
            throw OrderException.CannotPayOrder()
        }

        // 2. 잔액 차감 (5줄)
        val user = userService.deductBalance(userId, order.finalAmount)

        // 3. 재고 확정 및 판매량 증가 (15줄)
        for (item in order.items) {
            inventoryService.confirmReservation(...)
            productRankingService.incrementSales(...)
        }

        // 4. 쿠폰 사용 (7줄)
        if (order.couponId != null) {
            val userCoupon = couponService.validateUserCoupon(...)
            couponService.useCoupon(userCoupon)
        }

        // 5. 주문 완료 (3줄)
        val completedOrder = orderService.completeOrder(orderId)

        // 6. 이벤트 발행 (2줄)
        eventPublisher.publishEvent(OrderPaidEvent.from(completedOrder))

        // 7. 결과 반환 (5줄)
        return PaymentResult(...)
    }
}
```

**문제점:**
- 🔴 검증, 실행, 이벤트 발행 로직이 뒤섞임
- 🔴 메서드가 길고 복잡 (50줄 이상)
- 🔴 새로운 검증 규칙 추가 시 메서드 전체를 이해해야 함

---

#### ✅ 패턴 적용 후 (35줄)

```kotlin
@Service
class PaymentProcessingService(
    private val orderService: OrderService,
    private val paymentValidator: PaymentValidator,
    private val paymentExecutor: PaymentExecutor,
    private val paymentEventPublisher: PaymentEventPublisher
) {
    @Transactional
    fun processPayment(orderId: Long, userId: Long): PaymentResult {
        // 1. 주문 조회
        val order = orderService.getById(orderId)

        // 2. 검증 (PaymentValidator)
        paymentValidator.validatePayment(order, userId)

        // 3. 잔액 차감 (PaymentExecutor)
        val user = paymentExecutor.deductBalance(userId, order.finalAmount)

        // 4. 재고 확정 및 판매량 증가 (PaymentExecutor)
        paymentExecutor.confirmInventoryAndUpdateSales(order)

        // 5. 쿠폰 사용 처리 (PaymentExecutor)
        paymentExecutor.useCouponIfPresent(userId, order.couponId)

        // 6. 주문 완료 처리 (PaymentExecutor)
        val completedOrder = paymentExecutor.completeOrder(orderId)

        // 7. 이벤트 발행 (PaymentEventPublisher)
        paymentEventPublisher.publishOrderPaidEvent(completedOrder)

        // 8. 결제 결과 반환
        return PaymentResult(
            orderId = completedOrder.id,
            paidAmount = completedOrder.finalAmount,
            remainingBalance = user.balance,
            status = "SUCCESS"
        )
    }
}
```

**개선점:**
- ✅ 각 단계가 명확히 분리됨 (주석만 봐도 이해 가능)
- ✅ Domain Service가 순수한 orchestration만 수행
- ✅ 검증 로직 변경 시 PaymentValidator만 수정
- ✅ 테스트 작성 용이 (Validator, Executor 각각 독립 테스트)

---

## 적용된 서비스 목록

### ✅ 강력 권장 (패턴 적용 완료)

#### 1. PaymentProcessingService
- **복잡도:** 높음 (7단계 프로세스)
- **적용 패턴:** Validator, Executor, Publisher
- **파일 위치:**
  - `PaymentValidator.kt`
  - `PaymentExecutor.kt`
  - `PaymentEventPublisher.kt`
  - `PaymentProcessingService.kt`

#### 2. OrderCreationService
- **복잡도:** 높음 (4단계 프로세스)
- **적용 패턴:** Validator, Executor, Publisher (미래 확장용)
- **파일 위치:**
  - `OrderValidator.kt`
  - `OrderExecutor.kt`
  - `OrderEventPublisher.kt`
  - `OrderCreationService.kt`

#### 3. CouponIssuanceService (예정)
- **복잡도:** 높음 (Redis 기반 동시성 제어)
- **적용 패턴:** Validator, Executor, Publisher, **CacheManager** ⭐
- **예정 파일:**
  - `CouponValidator.kt`
  - `CouponExecutor.kt`
  - `CouponEventPublisher.kt`
  - `CouponCacheManager.kt` (Redis 키 전략 캡슐화)
  - `CouponIssuanceService.kt`

---

### 🤔 선택적 적용 고려

#### 4. InventoryService
- **복잡도:** 중간 (재고 예약/확정/취소 + 캐시)
- **적용 여부:** 재고 비즈니스 규칙이 더 복잡해지면 적용 고려
- **현재 상태:** 유지 (메서드가 3개밖에 없어서 현재는 충분)

---

### ❌ 패턴 적용 불필요

#### 5. ProductService
- **복잡도:** 낮음 (단순 CRUD)
- **이유:** 검증 로직이 거의 없고, 조회/저장만 수행

#### 6. UserService
- **복잡도:** 낮음 (잔액 충전/차감)
- **이유:** 메서드당 3-4줄의 단순 로직

#### 7. TransmissionLogService
- **복잡도:** 낮음 (Repository 래퍼)
- **이유:** 단순히 Repository 호출을 Service로 감싼 것

---

## 패턴 적용 판단 기준

### ✅ 패턴을 적용하면 좋은 경우

1. **복잡한 비즈니스 로직** - 3단계 이상의 프로세스
2. **여러 검증 단계** - 2개 이상의 독립적인 검증 규칙
3. **이벤트 발행이 중요** - 비동기 처리 트리거
4. **캐시 관리 필요** - Redis/Memcached 전략 복잡

### ❌ 오버엔지니어링이 될 수 있는 경우

1. **단순 CRUD만 하는 서비스**
2. **검증 로직이 1-2개밖에 없는 서비스**
3. **이벤트가 필요 없는 서비스**
4. **메서드가 10줄 이하인 단순 서비스**

---

## 결론

**OrderService (Entity Service):**
- Order 엔티티의 **기본 CRUD 서비스** (단일 도메인)
- 재사용 가능, 단순 명확

**OrderCreationService (Domain Service):**
- 주문 생성 **워크플로우를 조율하는 Domain Service** (다중 도메인)
- Validator/Executor/Publisher 패턴 적용
- 복잡도 관리, 확장성 확보

**분리 이유:**
1. **단일 책임 원칙** - 각자 명확한 책임
2. **DDD Domain Service 패턴** - 여러 도메인 조율 로직 분리
3. **재사용성** - Entity Service는 여러 곳에서 재사용
4. **복잡도 관리** - UseCase와 Service가 각자 적절한 복잡도 유지
5. **Validator/Executor/Publisher 패턴 적용 기반** - 책임을 더 세분화
