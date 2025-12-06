# UseCase와 Service Layer 분리 리팩토링 계획

## 📋 현재 구조 문제점 분석

### 1. UseCase의 과도한 책임
```kotlin
// 문제: OrderUseCase가 Repository를 직접 의존
@Service
class OrderUseCase(
    private val orderRepository: OrderRepository,           // ❌ 인프라 직접 의존
    private val productRepository: ProductRepository,       // ❌ 인프라 직접 의존
    private val userRepository: UserRepository,             // ❌ 인프라 직접 의존
    private val couponRepository: CouponRepository,         // ❌ 인프라 직접 의존
    private val inventoryRepository: InventoryRepository,   // ❌ 인프라 직접 의존
    private val productUseCase: ProductUseCase,             // ❌ UseCase 간 의존
    private val productRankingService: ProductRankingService,
    private val eventPublisher: ApplicationEventPublisher
)
```

**문제점:**
- UseCase가 비즈니스 로직 + 데이터 접근을 모두 처리
- 인프라 레이어(Repository)에 직접 의존 → Clean Architecture 위반
- 테스트 시 모든 Repository를 Mock해야 함
- 재사용성 낮음 (다른 UseCase에서 동일 로직 사용 불가)

### 2. Service와 UseCase의 역할 불명확

**현재 구조:**
- `PaymentService`: 비즈니스 로직 + 트랜잭션 관리 + Repository 직접 접근
- `ProductRankingService`: Redis 연산만 처리 (도메인 로직 없음)
- `CouponIssuanceService`: Redis 연산만 처리 (도메인 로직 없음)

**문제점:**
- Service의 역할이 일관되지 않음
- 일부는 도메인 Service, 일부는 Infrastructure Service
- 비즈니스 로직의 위치가 불명확

### 3. 계층 간 의존성 방향 문제

```
현재 (잘못된 의존성):
Controller → UseCase → Repository (Infrastructure)
                    ↘ Service

문제: UseCase가 인프라에 직접 의존
```

---

## 🎯 개선 목표

### Clean Architecture 원칙 적용

```
개선 후 (올바른 의존성):

Presentation Layer (Controller)
        ↓
Application Layer (UseCase) - 비즈니스 흐름 오케스트레이션
        ↓
Domain Layer (Service + Entity) - 비즈니스 규칙
        ↓
Repository Interface (Domain)
        ↑
Infrastructure Layer (Repository Implementation)
```

**핵심 원칙:**
1. **의존성 역전 (DIP)**: 상위 레이어가 하위 레이어 인터페이스에 의존
2. **단일 책임 (SRP)**: 각 레이어는 명확한 책임만 가짐
3. **계층 분리**: 비즈니스 로직과 인프라 로직 완전 분리

---

## 📐 리팩토링 설계

### 계층별 책임 정의

#### 1. UseCase (Application Layer)
**책임**: 비즈니스 유스케이스 흐름 오케스트레이션

```kotlin
// 개선 후: OrderUseCase
@Service
class OrderUseCase(
    private val orderService: OrderService,           // ✅ Domain Service
    private val productService: ProductService,       // ✅ Domain Service
    private val userService: UserService,             // ✅ Domain Service
    private val couponService: CouponService,         // ✅ Domain Service
    private val inventoryService: InventoryService,   // ✅ Domain Service
    private val paymentService: PaymentService,       // ✅ Domain Service
    private val eventPublisher: ApplicationEventPublisher
) {
    @Transactional
    fun createOrder(request: CreateOrderRequest): Order {
        // 1. 사용자 조회
        val user = userService.getById(request.userId)

        // 2. 상품 및 재고 검증
        val orderItems = productService.validateAndCreateOrderItems(request.items)

        // 3. 쿠폰 검증 (옵션)
        val coupon = request.couponId?.let { couponService.validateUserCoupon(request.userId, it) }

        // 4. 주문 생성
        val order = orderService.createOrder(user, orderItems, coupon)

        // 5. 재고 예약
        inventoryService.reserveStock(orderItems)

        return order
    }
}
```

**특징:**
- Repository 직접 의존 제거
- Service들을 조합하여 비즈니스 흐름만 제어
- 각 단계를 명확하게 표현
- 테스트 시 Service만 Mock하면 됨

#### 2. Service (Domain Layer)
**책임**: 단일 도메인의 비즈니스 로직 처리

```kotlin
// 개선 후: OrderService (신규 생성)
@Service
class OrderService(
    private val orderRepository: OrderRepository
) {
    fun createOrder(
        user: User,
        orderItems: List<OrderItem>,
        coupon: UserCoupon?
    ): Order {
        val totalAmount = orderItems.sumOf { it.subtotal }
        val discountAmount = coupon?.calculateDiscount(totalAmount) ?: 0L
        val finalAmount = totalAmount - discountAmount

        val order = Order(
            id = UUID.randomUUID().toString(),
            userId = user.id,
            items = orderItems,
            totalAmount = totalAmount,
            discountAmount = discountAmount,
            finalAmount = finalAmount,
            status = "PENDING",
            createdAt = LocalDateTime.now()
        )

        return orderRepository.save(order)
    }

    fun getById(orderId: String): Order {
        return orderRepository.findById(orderId)
            ?: throw OrderException.OrderNotFound(orderId)
    }

    fun updateOrderStatus(orderId: String, newStatus: String): Order {
        val order = getById(orderId)
        order.updateStatus(newStatus)
        return orderRepository.save(order)
    }
}

// 개선 후: ProductService (신규 생성)
@Service
class ProductService(
    private val productRepository: ProductRepository
) {
    fun getById(productId: Long): Product {
        return productRepository.findById(productId)
            ?: throw ProductException.ProductNotFound(productId.toString())
    }

    fun validateAndCreateOrderItems(
        items: List<OrderItemRequest>
    ): List<OrderItem> {
        return items.map { request ->
            val product = getById(request.productId)
            OrderItem.create(product, request.quantity)
        }
    }
}

// 개선 후: UserService (신규 생성)
@Service
class UserService(
    private val userRepository: UserRepository
) {
    fun getById(userId: Long): User {
        return userRepository.findById(userId)
            ?: throw UserException.UserNotFound(userId.toString())
    }

    fun chargeBalance(userId: Long, amount: Long): User {
        val user = getById(userId)
        user.charge(amount)
        return userRepository.save(user)
    }

    fun deductBalance(userId: Long, amount: Long): User {
        val user = getById(userId)
        user.pay(amount)
        return userRepository.save(user)
    }
}

// 개선 후: CouponService (신규 생성)
@Service
class CouponService(
    private val couponRepository: CouponRepository
) {
    fun getById(couponId: Long): Coupon {
        return couponRepository.findById(couponId)
            ?: throw CouponException.CouponNotFound(couponId.toString())
    }

    fun validateUserCoupon(userId: Long, couponId: Long): UserCoupon {
        val userCoupon = couponRepository.findUserCoupon(userId, couponId)
            ?: throw CouponException.CouponNotFound("User coupon not found")

        if (!userCoupon.isValid()) {
            throw CouponException.InvalidCoupon()
        }

        return userCoupon
    }

    fun useCoupon(userCoupon: UserCoupon): UserCoupon {
        userCoupon.use()
        return couponRepository.saveUserCoupon(userCoupon)
    }
}
```

**특징:**
- 단일 도메인에 집중
- Repository에만 의존 (인터페이스)
- 도메인 엔티티와 함께 동작
- 재사용 가능한 비즈니스 로직

#### 3. Infrastructure Service
**책임**: 외부 시스템/기술 연동

```kotlin
// ProductRankingService - 위치 변경 필요 없음 (이미 적절)
// infrastructure/services/ 또는 application/services/infrastructure/

@Service
class ProductRankingService(
    private val redisTemplate: RedisTemplate<String, String>,
    private val productRepository: ProductRepository
) {
    // Redis 연산 처리
    fun incrementSales(productId: Long, quantity: Int) { ... }
    fun getTopProductsDaily(limit: Int): List<RankingItem> { ... }
}

// CouponIssuanceService - 위치 변경 필요 없음
@Service
class CouponIssuanceService(
    private val redisTemplate: RedisTemplate<String, String>
) {
    // Redis 연산 처리
    fun checkIssuanceEligibility(couponId: Long, userId: Long) { ... }
    fun recordIssuance(couponId: Long, userId: Long): Long { ... }
}
```

---

## 🔄 리팩토링 단계

### Phase 1: Domain Service 생성 (Week 1)
**목표**: 비즈니스 로직을 Service로 분리

#### Step 1.1: 핵심 Domain Service 생성
- [ ] `OrderService` 생성
  - `createOrder()`: 주문 생성 로직
  - `getById()`: 주문 조회
  - `updateOrderStatus()`: 주문 상태 변경
  - 테스트: `OrderServiceTest` 작성

- [ ] `ProductService` 생성
  - `getById()`: 상품 조회
  - `validateAndCreateOrderItems()`: 주문 아이템 생성 및 검증
  - 테스트: `ProductServiceTest` 작성

- [ ] `UserService` 생성
  - `getById()`: 사용자 조회
  - `chargeBalance()`: 잔액 충전
  - `deductBalance()`: 잔액 차감
  - 테스트: `UserServiceTest` 작성

- [ ] `CouponService` 생성
  - `getById()`: 쿠폰 조회
  - `validateUserCoupon()`: 사용자 쿠폰 검증
  - `useCoupon()`: 쿠폰 사용 처리
  - 테스트: `CouponServiceTest` 작성

#### Step 1.2: 통합 테스트 작성
- [ ] 각 Service의 단위 테스트 작성
- [ ] Repository Mock을 사용한 테스트
- [ ] 테스트 커버리지 80% 이상 확보

### Phase 2: UseCase 리팩토링 (Week 2)
**목표**: UseCase가 Service만 의존하도록 변경

#### Step 2.1: OrderUseCase 리팩토링
- [ ] Repository 의존성 제거
- [ ] Service 의존성으로 교체
- [ ] 비즈니스 흐름만 오케스트레이션
- [ ] 기존 테스트 수정 (Repository Mock → Service Mock)
- [ ] 통합 테스트 통과 확인

#### Step 2.2: CouponUseCase 리팩토링
- [ ] Repository 의존성 제거
- [ ] CouponService 활용
- [ ] 쿠폰 발급 로직 정리
- [ ] 테스트 수정 및 통과 확인

#### Step 2.3: ProductUseCase 리팩토링
- [ ] Repository 의존성 제거
- [ ] ProductService 활용
- [ ] 테스트 수정 및 통과 확인

#### Step 2.4: InventoryUseCase 리팩토링
- [ ] Repository 의존성 제거
- [ ] InventoryService 활용
- [ ] 테스트 수정 및 통과 확인

### Phase 3: PaymentService 리팩토리 (Week 3)
**목표**: PaymentService를 Domain Service로 개선

#### Step 3.1: PaymentService 분리
- [ ] 비즈니스 로직 추출 → `PaymentService` (Domain)
- [ ] 인프라 로직 → `PaymentProcessorService` (Infrastructure)
- [ ] 분산락 로직 → UseCase로 이동 고려
- [ ] 테스트 재작성

#### Step 3.2: PaymentUseCase 생성
- [ ] 결제 흐름 오케스트레이션
- [ ] PaymentService + PaymentProcessorService 조합
- [ ] 멱등성 처리 로직 정리
- [ ] 통합 테스트 작성

### Phase 4: 전체 통합 및 검증 (Week 4)
**목표**: 리팩토링 결과 검증 및 문서화

#### Step 4.1: 전체 테스트 실행
- [ ] 모든 단위 테스트 통과 확인
- [ ] 모든 통합 테스트 통과 확인
- [ ] 테스트 커버리지 확인 (80% 이상)

#### Step 4.2: 성능 테스트
- [ ] 리팩토링 전후 성능 비교
- [ ] 병목 지점 확인 및 개선
- [ ] 동시성 테스트 재실행

#### Step 4.3: 문서화
- [ ] 아키텍처 문서 업데이트
- [ ] 계층별 책임 문서화
- [ ] 의존성 다이어그램 작성
- [ ] README 업데이트

---

## 📊 리팩토링 전후 비교

### Before (현재)
```
Controller
    ↓
OrderUseCase
    ├─ orderRepository (직접 의존) ❌
    ├─ productRepository (직접 의존) ❌
    ├─ userRepository (직접 의존) ❌
    ├─ couponRepository (직접 의존) ❌
    ├─ inventoryRepository (직접 의존) ❌
    └─ productRankingService
```

**문제점:**
- UseCase가 인프라에 직접 의존
- 비즈니스 로직이 UseCase에 분산
- 재사용성 낮음
- 테스트 복잡도 높음

### After (개선 후)
```
Controller
    ↓
OrderUseCase (오케스트레이션만)
    ├─ orderService ✅
    ├─ productService ✅
    ├─ userService ✅
    ├─ couponService ✅
    ├─ inventoryService ✅
    └─ paymentService ✅
           ↓
    Domain Service (비즈니스 로직)
           ↓
    Repository Interface
           ↑
    Repository Implementation (Infrastructure)
```

**장점:**
- 의존성 역전 원칙 준수 ✅
- 비즈니스 로직 재사용 가능 ✅
- 테스트 용이성 향상 ✅
- 계층 간 책임 명확 ✅

---

## 🎯 예상 효과

### 1. 코드 품질
- **응집도 향상**: 각 Service가 단일 도메인에 집중
- **결합도 감소**: UseCase가 Repository에 직접 의존하지 않음
- **재사용성 증가**: Service를 다른 UseCase에서도 활용 가능

### 2. 테스트 용이성
- **Mock 감소**: UseCase 테스트 시 Service만 Mock
- **단위 테스트 증가**: Service 단위로 독립적 테스트 가능
- **테스트 속도 향상**: 인프라 의존성 제거로 빠른 테스트

### 3. 유지보수성
- **변경 영향 최소화**: Repository 변경 시 Service만 수정
- **비즈니스 로직 위치 명확**: Service에 집중
- **새로운 기능 추가 용이**: Service 조합으로 쉽게 확장

### 4. Clean Architecture
- **의존성 방향 준수**: 상위 → 하위 인터페이스
- **계층 분리 명확**: Presentation → Application → Domain → Infrastructure
- **테스트 가능한 설계**: 각 계층을 독립적으로 테스트

---

## ⚠️ 주의사항

### 1. 점진적 리팩토링
- 한 번에 모든 것을 바꾸지 말 것
- Phase 단위로 진행하고 각 Phase마다 테스트 통과 확인
- 기능 동작에 영향 없도록 주의

### 2. 테스트 우선
- 리팩토링 전에 기존 테스트가 모두 통과하는지 확인
- 리팩토링 중에도 테스트 유지
- 리팩토링 후 테스트 커버리지 유지 또는 향상

### 3. 트랜잭션 범위
- Service에 `@Transactional` 적용 여부 신중하게 결정
- UseCase에서 트랜잭션 범위 제어 (여러 Service 조합 시)
- 분산 트랜잭션 이슈 주의

### 4. 성능
- Service 호출 체인이 깊어지지 않도록 주의
- N+1 쿼리 발생 가능성 확인
- 성능 테스트로 검증

---

## 📚 참고 자료
- Clean Architecture (Robert C. Martin)
- DDD (Domain-Driven Design)
- Hexagonal Architecture
- SOLID Principles
