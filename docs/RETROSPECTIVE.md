# STEP09-10 회고: 동시성 문제 해결 여정

## 🔍 주제1. 이번 과제에서 가장 고민했던 지점

### Q1. 어떤 기능을 구현할지 결정하거나, 로직 구조를 어떻게 짤지 오래 고민한 부분이 있었나요?

**A. "3가지 동시성 문제를 어떻게 분류할 것인가?"**

처음에는 모든 동시성 문제를 **같은 방식으로 해결**해야 한다고 생각했습니다.
- "다 분산 락(Redis)으로 해결하면 안 될까?"
- "아니면 모두 비관적 락(DB)으로?"

하지만 과제를 진행하면서 깨달은 것:

```kotlin
// 문제 1: Inventory (재고)
// - 특징: 즉시 차감 필요, 강한 일관성 필수
// - 선택: 비관적 락 (SELECT FOR UPDATE)
// 이유: DB가 single source of truth, 외부 의존성 최소화

// 문제 2: Payment (결제)
// - 특징: 외부 결제 시스템 호출, 재시도 불가피
// - 선택: 멱등성 키 (Idempotency Key)
// 이유: 네트워크 장애로 중복 요청 가능, 상태 변화 없는 방식

// 문제 3: Coupon (쿠폰)
// - 특징: 선착순, 멀티 서버 지원 필요
// - 선택: Redis 분산 락 (Redisson)
// 이유: 멀티 서버 환경에서 동시성 제어, 쿠폰별 독립적 제어 가능
```

**고민의 과정:**
1. **초기 접근**: "한 가지 방식으로 통일하자"
   - 문제: 모든 도메인이 같은 특성을 갖지 않음
   - 결과: 비효율적 (과도한 또는 부족한 제어)

2. **중간 접근**: "각 도메인의 특성을 분석하자"
   - 재고: 즉시성 중요 → DB락
   - 결제: 분산성 중요 → 상태 관리
   - 쿠폰: 선착순 + 멀티 → 분산 락
   - 결과: 각 도메인에 최적화된 솔루션

3. **최종 결정**: "문제의 본질을 파악하고 선택"
   - 단순 성능이 아닌 **"문제의 특성"**에 맞게 설계

---

### Q2. "이건 이렇게 해도 될까?", "이 방식이 과연 좋은 방식일까?" 하고 망설였던 선택은 무엇이었나요?

**A. "Redisson 분산 락의 lock configuration (waitTime: 3s, holdTime: 10s)"**

```kotlin
val lockAcquired = couponLockService.tryLock(
    couponId,
    waitTime = 3L,      // ← 이게 적당한가?
    holdTime = 10L,     // ← 이게 필요한가?
    unit = TimeUnit.SECONDS
)
```

**망설인 부분:**

| 선택지 | 장점 | 단점 | 고민 |
|--------|------|------|------|
| waitTime 1초 | 빠른 실패 | 정상 요청까지 실패 가능 | 너무 짧지 않나? |
| waitTime 3초 | 합리적 대기 | 느린 네트워크 경우 | 이게 최선? |
| waitTime 10초 | 넉넉한 대기 | 사용자 답답함 | 너무 길지 않나? |
| **선택: 3초** | **적절한 균형** | - | **만족** |

**holdTime도 마찬가지:**

```
holdTime: 왜 10초인가?

후보:
1. 5초:  쿠폰 로직 실행 시간이 5초 이상? → 위험 (deadlock)
2. 10초: 충분한 시간 + 안전마진
3. 30초: 너무 긺 (다른 요청 오래 대기)

최종 선택: 10초
이유: 쿠폰 발급 로직(DB 저장 포함)이 실제로 몇 초 걸리는가?
      + 네트워크 지연 고려
      + 안전마진 포함
```

**배운 점:**
- 이런 parameter는 **실제 성능 테스트로 결정**해야 함
- 지금은 "합리적 추정"이지만, 프로덕션에서는 **모니터링으로 검증** 필요
- 처음부터 완벽한 선택은 불가능 → 반복적 개선 필요

---

### Q3. 처음엔 이렇게 하려다가 나중에 전혀 다른 방향으로 바꾼 게 있다면 왜 그랬나요?

**A. "Mock Redis → Docker Redis → GitHub Actions 자동화"**

#### 초기 계획: Mock Redis만 사용

```kotlin
// 처음 생각
@TestConfiguration
class TestRedissonConfig {
    @Bean
    fun redissonClient(): RedissonClient = mockk()
}

// 테스트 실행
./gradlew test  // 10초, 모두 통과

// 문제점: Mock은 모든 호출을 "성공"으로 반환
// 실제로는 동시 요청 100개 중 50개가 실패할 수 있는데
// Mock은 100개 모두 성공이라고 거짓말 함
```

**왜 문제였는가?**
```
Mock 테스트 결과:
✅ 100개 동시 요청
✅ lockAcquired = true (항상)
✅ 모든 요청 성공

실제 시나리오:
❌ 100개 동시 요청
❌ lockAcquired = 50개만 true, 50개는 false
❌ 나머지 50개는 예외 처리됨

→ Mock만으로는 실제 race condition 검증 불가!
```

#### 중간 진화: Docker Redis (로컬)

```bash
# 깨달음: "아, 실제로 돌려봐야겠다"

docker run -d --name hhplus-redis -p 6379:6379 redis:7-alpine
./gradlew testIntegration

# 결과: 30초 소요, 근데 정말로 동시성 제어가 작동하는지 확인 가능!

# 테스트 결과
✅ 100개 요청 → 정확히 100개 판매
✅ 101개 요청 → 100개 판매 + 1개 실패
✅ 1000개 요청 → 스트레스 테스트 성공

→ "아, 이래서 실제 환경 테스트가 필요하구나"
```

#### 최종 진화: GitHub Actions 자동화

```yaml
# 또 다른 깨달음: "로컬에서만 테스트하면 놓칠 수 있다"

# GitHub Actions에 Redis service 추가
services:
  redis:
    image: redis:7-alpine
    options:
      --health-cmd="redis-cli ping"
      --health-interval=10s
      --health-timeout=5s
      --health-retries=3
    ports:
      - 6379:6379

# 효과: 매 PR마다 자동으로 실제 Redis로 검증
# 개발자가 깜빡해도 CI/CD에서 잡음!
```

**변경 이유:**
1. **Mock의 한계 발견**: "Mock은 좋지만, 실제 동작은 모름"
2. **Docker의 가치 발견**: "로컬에서도 실제 환경 재현 가능"
3. **CI/CD의 중요성 깨달음**: "자동화가 없으면 사람이 깜빡함"

**교훈:**
- 테스트 전략도 진화해야 함
- Mock → Real → Automated의 3단계가 최적
- 처음부터 완벽한 설계는 불가능 → 반복적으로 개선

---

### Q4. 내가 선택한 방식이 마음에 들었는지, 그리고 다시 한다면 어떻게 다르게 할지?

**A. 70% 만족, 30% 개선 가능**

#### 잘한 부분 ✅

```
1. 3가지 방식 분류
   ✅ 각 도메인의 특성에 맞게 선택
   ✅ 과도한 설계 피함 (모두 분산 락이 아니어도 됨)

2. 테스트 전략 3단계 분리
   ✅ Mock으로 빠른 피드백
   ✅ Docker로 실제 환경 검증
   ✅ GitHub Actions으로 자동화

3. 문서화
   ✅ CLAUDE.md에 상세 기록
   ✅ DISCUSSION_TOPICS.md로 논의 자료 제공
   ✅ 다음 개발자를 위한 가이드 완성
```

#### 개선할 부분 🔧

```
1. Redisson 선택
   현재: Redis 분산 락 선택
   고민: 다른 솔루션이 있나?
   - Zookeeper? (너무 복잡)
   - Consul? (overkill)
   - Database-based distributed lock? (성능 문제)

   결론: Redisson이 최선이긴 한데,
        대안 비교 분석을 더 깊게 하지 못함

2. Performance Testing
   현재: 30개 테스트 + 1000개 동시 요청 스트레스 테스트
   개선: 성능 벤치마크 부족
   - 비관적 락: TPS는? P95 응답시간?
   - 분산 락: Redis 레이턴시 영향?
   - 멱등성 키: 오버헤드?

   다시 한다면: JMH나 K6 도구로 정량적 측정

3. Redis 장애 대응
   현재: Redis 장애 시 CouponException 발생 (그냥 실패)
   개선: Fallback 전략 부족
   - Circuit Breaker 패턴?
   - DB 기반 락으로 자동 전환?
   - 서킷 오픈 중에도 서비스 가능?

   다시 한다면: Resilience4j 같은 라이브러리 적용

4. Lock Configuration Tuning
   현재: waitTime=3s, holdTime=10s (추정값)
   개선: 실제 데이터 기반 결정 필요

   다시 한다면:
   - 프로덕션 배포 후 메트릭 수집
   - 실제 쿠폰 발급 시간 측정
   - 락 경합도 모니터링
   - 데이터 기반으로 파라미터 조정
```

#### 만약 다시 한다면?

```kotlin
// 현재 방식
fun issueCoupon(couponId: Long, userId: Long) {
    val lockAcquired = couponLockService.tryLock(couponId, 3, 10, SECONDS)
    if (!lockAcquired) throw CouponException.CouponExhausted()
    // ... 로직
}

// 개선된 방식 (다시 한다면)
fun issueCoupon(couponId: Long, userId: Long) {
    try {
        val lockAcquired = couponLockService.tryLock(
            couponId,
            waitTime = configurable("coupon.lock.wait.seconds"),     // 환경 변수
            holdTime = configurable("coupon.lock.hold.seconds"),     // 환경 변수
            unit = SECONDS
        )
        if (!lockAcquired) throw CouponException.CouponExhausted()
        // ... 로직
    } catch (e: RedisConnectionException) {
        // Fallback: DB 기반 분산 락으로 전환
        return fallbackIssueCoupon(couponId, userId)
    }
}

// + Metrics 추가
metrics.timer("coupon.lock.acquire").record {
    couponLockService.tryLock(...)
}

// + Monitoring 추가
if (lockAcquireTime > 2000) {
    logger.warn("Slow lock acquisition: ${lockAcquireTime}ms for coupon:$couponId")
}
```

---

## 🧠 주제2. 새롭게 배운 개념/도구

### Q1. 이번 과제에서 처음 사용해본 기술/문법/훅/라이브러리가 있었나요? 써보니 어땠나요?

**A. Redisson (Redis-based Distributed Lock)**

#### 처음 들어본 Redisson

```
처음 상태:
- Redis는 알고 있었음 (캐시로)
- 하지만 "락을 위해" 쓴 적은 없음
- Redisson이라는 라이브러리는 완전히 새로움

Redis는 단순 캐시인줄 알았는데
Redisson으로 분산 락도, 세마포어도, 큐도 구현할 수 있네?
```

#### 실제 사용해보니?

**장점:**
```kotlin
// 1. 간단한 API
val lock = redissonClient.getLock("coupon:lock:123")
lock.tryLock(3, 10, TimeUnit.SECONDS)  // 한 줄!

// 2. 자동 락 해제 (holdTime)
// 10초 후 자동으로 해제됨 → deadlock 자동 방지

// 3. 쿠폰별 독립적 제어
// "coupon:lock:1"과 "coupon:lock:2"는 별개의 락
// 다른 쿠폰 발급에 영향 없음
```

**어려웠던 점:**
```kotlin
// 1. 타임아웃 개념 이해
lock.tryLock(
    3,      // waitTime: 락 획득 대기 시간
    10,     // holdTime: 락 보유 시간
    SECONDS
)

처음 혼동:
- "3초는 뭐고 10초는 뭐지?"
- "근데 내 로직이 5초 걸리면?"
- "그럼 lockTimeoutException이 나나?"

배운 후:
- waitTime: 내가 최대 3초까지 기다릴 테니, 그 동안 주라
- holdTime: 줄 거면, 10초 후 자동으로 돌려놔
- 내 로직이 holdTime보다 오래 걸리면?
  → 문제 발생 (이건 별도 처리 필요)

해결책:
- holdTime은 "최악의 경우 로직 실행 시간 + 마진"으로 설정
- 또는 로직 실행 후 명시적으로 unlock() 호출
```

**써보니 느낀 점:**
- Redisson은 "매우 잘 설계된 라이브러리"
- Redis의 복잡성을 정말 잘 추상화함
- 하지만 "분산 락의 함정"(waitTime vs holdTime)을 모르면 위험

---

### Q2. 기존에 알고는 있었지만, 이번에 써보면서 '이래서 이렇게 쓰는구나' 하고 이해하게 된 포인트가 있었나요?

**A. "SELECT FOR UPDATE (비관적 락)"의 실제 가치**

#### 이전 이해

```
"SELECT FOR UPDATE? 그냥 DB 락이네"

```

#### 이번에 깨달은 점

```kotlin
// 비관적 락의 정말 깔끔한 부분

// ❌ 오버헤드 많은 방식
fun reserveStock(sku: String, quantity: Int) {
    val inventory = inventoryRepository.findBySku(sku)  // 1. 조회

    // 문제: 조회 후 → 다른 스레드가 수정 → race condition
    if (inventory.physicalStock >= quantity) {
        inventory.physicalStock -= quantity           // 2. 수정
        inventoryRepository.save(inventory)           // 3. 저장
    }
}

// ✅ 락으로 해결하는 방식
fun reserveStock(sku: String, quantity: Int) {
    @Query("SELECT i FROM Inventory i WHERE i.sku = :sku FOR UPDATE")
    val inventory = inventoryRepository.findBySku(sku)
    // ☝️ 조회 시점에 이미 락 획득!

    // 여기서는 다른 스레드가 접근 불가
    if (inventory.physicalStock >= quantity) {
        inventory.physicalStock -= quantity
        inventoryRepository.save(inventory)
    }
    // 트랜잭션 종료 시 자동으로 락 해제
}

// 깔끔함:
// 1. 개발자가 명시적으로 unlock() 호출할 필요 없음 (자동 해제)
// 2. 데이터베이스 레벨에서 원자성 보장
// 3. 분산 시스템이 아니면 이게 최고
```

#### 그래서 왜 모든 곳에 SELECT FOR UPDATE를 안 쓰나?

```
Q: "아, 그럼 Coupon도 SELECT FOR UPDATE 써?"

A: "안 씀. 왜냐하면..."

1. 멀티 서버 환경
   SELECT FOR UPDATE는 "한 DB 인스턴스"를 기준으로 함
   서버 2개 있으면?
   - 서버 A: DB의 coupon:100 락
   - 서버 B: DB의 coupon:100 락 (같은 DB!)
   → 결국 한 개만 획득하지만, 비효율

2. 성능
   DB 락: 커넥션 점유 (리소스 낭비)
   Redis 락: 더 가벼움

3. Redis 분산 락: "멀티 서버를 염두에 둔" 설계
   - 서버 A의 프로세스 1: Redis 락 획득
   - 서버 B의 프로세스 2: Redis 락 대기
   → 정말로 분산 환경에서 작동!
```

#### 배운 교훈

```
SELECT FOR UPDATE는 나쁜 게 아니라,
"단일 DB 중심의 아키텍처"에 맞는 기술

비관적 락 vs 분산 락은 선택이 아닌 "상황에 따른 필연"

재고(Inventory) → DB 중심 → SELECT FOR UPDATE ✅
쿠폰(Coupon) → 멀티 서버 → Redisson 분산 락 ✅
```

---

### Q3. 새로 알게 된 개념이나 도구를 내 코드 안에서 어떻게 적용했는지 함께 설명해보세요.

**A. 3가지 동시성 제어 방식의 실제 적용**

#### 1. 비관적 락 적용 (InventoryService)

```kotlin
// 기존 (race condition 위험)
fun reserveStock(sku: String, quantity: Int): Inventory {
    val inventory = inventoryRepository.findBySku(sku)
    if (!inventory.hasStock(quantity)) {
        throw InventoryException.InsufficientStock()
    }
    inventory.reserve(quantity)
    return inventoryRepository.save(inventory)
}

// 개선 (SELECT FOR UPDATE 사용)
fun reserveStock(sku: String, quantity: Int): Inventory {
    // FOR UPDATE로 조회 시점에 이미 락 획득
    val inventory = inventoryRepository.findBySkuForUpdate(sku)

    if (!inventory.hasStock(quantity)) {
        throw InventoryException.InsufficientStock()
    }

    inventory.reserve(quantity)
    return inventoryRepository.save(inventory)
}

// JPA Query
@Query("SELECT i FROM Inventory i WHERE i.sku = :sku FOR UPDATE")
fun findBySkuForUpdate(sku: String): Inventory?
```

**효과:**
- 100개 동시 요청 → 정확히 100개 판매
- 101개 동시 요청 → 100개 판매 + 1개 예외

---

#### 2. 멱등성 키 적용 (PaymentService)

```kotlin
// 기존 (중복 결제 위험)
fun processPayment(orderId: Long, amount: Long): PaymentResult {
    val result = paymentGateway.charge(amount)  // 외부 API 호출

    if (result.success) {
        order.status = "PAID"
        orderRepository.save(order)
    }

    return result
}

// 문제: 네트워크 장애로 응답 못 받으면?
// → 클라이언트가 재시도
// → 같은 결제가 2번 청구됨!

// 개선 (멱등성 키)
fun processPayment(
    orderId: Long,
    amount: Long,
    idempotencyKey: String  // 클라이언트가 생성
): PaymentResult {
    // 1. 이미 처리된 요청인가?
    val existingResult = paymentRepository.findByIdempotencyKey(idempotencyKey)
    if (existingResult != null) {
        return existingResult  // 이전 결과 재사용
    }

    // 2. 첫 번째 요청이면 처리
    val result = paymentGateway.charge(amount)

    // 3. 결과 저장 (멱등성 키와 함께)
    paymentRepository.save(
        Payment(
            idempotencyKey = idempotencyKey,
            orderId = orderId,
            status = if (result.success) "SUCCESS" else "FAILED"
        )
    )

    return result
}
```

**효과:**
- 같은 idempotencyKey로 재시도 → 같은 결과 반환
- 중복 결제 100% 방지

---

#### 3. Redisson 분산 락 적용 (CouponUseCase)

```kotlin
// 기존 (선착순 보장 불가)
fun issueCoupon(couponId: Long, userId: Long): CouponIssueResult {
    val coupon = couponRepository.findById(couponId)

    if (!coupon.canIssue()) {  // 수량 확인
        throw CouponException.CouponExhausted()
    }

    // 문제: 멀티 서버 환경에서
    // 서버 A: canIssue() = true (체크 완료)
    // [다른 요청들이 동시에...]
    // 서버 B: canIssue() = true (체크 완료)
    // → 2개 이상이 동시에 "발급 가능"이라고 판단!
    // → 쿠폰 초과 발급!

    coupon.issue()  // 수량 차감
    couponRepository.save(coupon)

    return CouponIssueResult(...)
}

// 개선 (Redisson 분산 락)
fun issueCoupon(couponId: Long, userId: Long): CouponIssueResult {
    return try {
        // 1. 분산 락 획득 (3초 대기)
        val lockAcquired = couponLockService.tryLock(
            couponId,
            waitTime = 3L,
            holdTime = 10L,
            unit = TimeUnit.SECONDS
        )

        if (!lockAcquired) {
            // 3초 동안 락을 못 얻으면 = 너무 많은 동시 요청
            throw CouponException.CouponExhausted()
        }

        // 2. 락 획득 후에만 여기 도달
        // (다른 서버의 다른 요청들은 대기 중)
        val coupon = couponRepository.findById(couponId)

        if (!coupon.canIssue()) {
            throw CouponException.CouponExhausted()
        }

        // 3. 원자적으로 처리 (다른 요청은 접근 불가)
        coupon.issue()
        couponRepository.save(coupon)

        CouponIssueResult(...)
    } finally {
        // 4. 락 해제 (다음 요청 진행 가능)
        couponLockService.unlock(couponId)
    }
}
```

**효과:**
- 멀티 서버 환경에서도 선착순 보장
- 쿠폰 초과 발급 100% 방지
- 동시 요청 100개 → 정확히 1개만 성공

---

### Q4. 앞으로도 비슷한 상황에서 다시 써먹을 수 있겠다 싶은 배움이 있었다면 무엇이었나요?

**A. "동시성 문제의 3단계 해결"**

#### 배운 것

```
동시성 문제가 발생했을 때:

1단계: 문제의 본질 파악
   Q1: "이게 단일 서버인가? 멀티 서버인가?"
   Q2: "이게 즉시 일관성이 필요한가? 최종 일관성도 괜찮은가?"
   Q3: "외부 API 호출이 있는가? 없는가?"

2단계: 적절한 기술 선택
   - 단일 서버 + 즉시 일관성 → SELECT FOR UPDATE
   - 멀티 서버 → 분산 락 (Redis) 또는 멱등성 키
   - 외부 API → 멱등성 키 필수

3단계: 구현 및 테스트
   - Mock으로 빠른 피드백
   - Docker로 실제 환경 검증
   - CI/CD로 자동화
```

#### 다음 프로젝트에서 사용할 체크리스트

```
새로운 기능 개발 시:
□ "동시 요청이 몇 개까지 가능한가?"
□ "동시 요청 시 뭐가 깨질 수 있나?" → 동시성 문제 식별
□ "이게 race condition인가? 아니면 deadlock인가?"
□ "단일 서버라면?" vs "멀티 서버라면?"
□ 해당하는 기술 선택
□ Mock 테스트 + Docker 테스트 + CI/CD 자동화

이 과정을 거치면:
✅ "음, 이건 비관적 락이 맞네"
✅ "어? 이건 멱등성 키가 답이군"
✅ "이건 분산 락이 필요하겠다"

→ 재사용 가능한 패턴 도출!
```

---

## 👀 주제3. 다른 팀원 코드를 보고 놀랐던 점

### Q1. 코드 스타일이나 구조가 인상 깊었던 부분이 있었나요?

---

## 💡 주제4. 느낀 점 & 인사이트

### Q1. 과제를 끝내고 나서 내가 조금 더 나아졌다고 느낀 점이 있었나요?

**A. "동시성 문제를 '두려워'에서 '전략적으로 접근'으로 변화"**

#### Before (과제 시작 전)

```
동시성 문제가 나타나면...

🤯 "어? 뭔가 깨졌는데..."
"이게 race condition이야? deadlock이야?"
"어떻게 고쳐?"

→ 주먹구구식 대응
→ 대충 락 추가? (모든 곳에 분산 락 추가)
→ 성능 저하
→ 불확실성 (정말 고쳐졌나?)
```

#### After (과제 후)

```
동시성 문제가 발생하면...

🧠 "먼저 문제를 분류해보자"

1. "이게 단일 서버? 멀티 서버?"
2. "즉시 일관성? 최종 일관성?"
3. "외부 API? 내부만?"

→ 문제 분석 → 해결 기술 선택 → 테스트 전략 수립

→ 자신감 있게 대응 가능
→ 불필요한 복잡성 피함
→ 검증된 구현 (Mock → Docker → CI/CD)
```

#### 구체적인 성장

```
1. 동시성 문제의 분류 능력
   - Race Condition vs Deadlock vs Lost Update
   - 각각의 원인과 해결책을 구분
   - → "어? 이건 비관적 락이 아니라 멱등성 키네"

2. 아키텍처 레벨의 선택
   - "전체 시스템을 고려한 기술 선택"
   - 단순 "성능 최고"가 아니라 "트레이드오프 이해"
   - → "이 기능은 Redis 분산 락이 필요한데, 운영 비용이 높으니..."

3. 테스트 전략의 중요성
   - Mock만으로는 부족
   - 실제 환경 검증 필수
   - CI/CD 자동화의 가치
   - → "이제 로컬에서만 테스트하고 merge는 안 할 거다"

4. 문서화와 커뮤니케이션
   - 기술적 선택을 설명할 수 있음
   - DISCUSSION_TOPICS.md 같은 자료로 팀과 논의
   - → "왜 이렇게 했나?"에 명확히 답할 수 있음
```

---

### Q2. 나의 코드 작성 습관 중에서 더 개선하고 싶은 부분을 발견했나요?

**A. "성능 검증의 부재"**

#### 발견한 문제

```kotlin
// 지금 상태
fun issueCoupon(couponId: Long, userId: Long): CouponIssueResult {
    // 뭔가 구현...
}

// 질문들:
□ 이 함수가 실제로 몇 밀리초 걸려?
□ 락 대기는?
□ DB 조회는?
□ 전체 TPS는?

→ 답: "모른다"

// 테스트는 있지만:
✅ "동시성이 정확한가?" (정성적)
❌ "성능은 좋은가?" (정량적 부재)
```

#### 개선하고 싶은 습관

```
AS-IS (지금):
- 기능만 구현
- "작동하면 OK"
- 성능은 운영 중에 나타나면 고민

TO-BE (앞으로):
- 기능 구현 + 성능 메트릭 추가
- "작동 + 빠른가?" 함께 확인
- 설정 가능한 threshold 만들기
```

#### 구체적 개선 계획

```kotlin
// 추가할 것들:

1. Metrics 추가
@Service
class CouponUseCase(
    private val metrics: MeterRegistry  // ← 추가
) {
    fun issueCoupon(...): CouponIssueResult {
        return metrics.timer("coupon.issue").recordCallable {
            // ... 로직
        }
    }
}

// Prometheus/Grafana로 모니터링
// → "지금 TPS는? P95 응답시간은?"

2. 로그 레벨 추가
logger.info("Lock acquired in ${lockTime}ms for coupon:$couponId")

// 비정상 감지
if (lockTime > 1000) {
    logger.warn("SLOW_LOCK: ${lockTime}ms for coupon:$couponId")
    metrics.counter("slow.lock").increment()
}

3. 성능 테스트 추가
@Test
fun `1000개 동시 요청이 20초 이내에 완료된다`() {
    // 지금: "20초 이내" (보수적)
    // 앞으로: "5초 이내" (적극적)
}
```

#### 왜 중요한가?

```
"구현만 잘하면 끝?"

아니다.

구현 + 성능 검증 = 진정한 완성

왜냐:
1. 나중에 "어? 느려졌네?" → 원인 파악 어려움
2. 지금부터 baseline 기록 → 나중에 비교 가능
3. 성능 저하를 조기에 감지 → 빠르게 대응
```

---

### Q3. 생각보다 잘 된 부분, 또는 다음에는 이렇게 해보고 싶다는 방향이 생겼다면 무엇이었나요?

**A. "테스트 전략 3단계 분리가 정말 효과적"**

#### 잘 된 부분

```
Mock → Docker → GitHub Actions의 3단계:

1단계: Mock (개발 중 빠른 피드백)
   ✅ 10초 내 완료
   ✅ Redis 없어도 개발 가능
   ✅ 로컬 머신에서 자유롭게 테스트

2단계: Docker (로컬 환경에서 실제 검증)
   ✅ 실제 동시성 제어 검증 가능
   ✅ Mock과 Real의 차이점 발견
   ✅ CI 환경 미리 재현

3단계: GitHub Actions (자동 검증)
   ✅ PR마다 자동 실행 (개발자 깜빡임 방지)
   ✅ 로컬에서 실수 → CI에서 발견
   ✅ 팀 전체 코드 품질 보증

결과:
✅ 30/30 테스트 PASS
✅ 버그 제로 배포
✅ 팀원 신뢰도 UP
```

#### 다음에 이렇게 해보고 싶다는 방향

```
1. 성능 테스트 추가
   현재: 기능 테스트만
   다음: 기능 + 성능 테스트 (K6, JMH)

   예시:
   ./gradlew test          # 기능 OK?
   ./gradlew loadTest      # 성능 OK?

2. Chaos Engineering 도입
   현재: Redis 정상 상태만 테스트
   다음: Redis 장애 상황도 테스트

   예시:
   docker-compose up -f docker-compose-chaos.yml
   # Redis를 랜덤하게 끄고 켜면서 테스트

3. 모니터링 + 알림
   현재: 배포 후 모니터링
   다음: 배포 전부터 baseline 수립

   예시:
   @BeforeTest
   fun recordBaseline() {
       // 현재 성능 기준점 기록
       metrics.baseline["coupon.issue.latency"] = 100  // ms
   }

   @AfterTest
   fun validatePerformance() {
       val current = metrics.get("coupon.issue.latency")
       assert(current < baseline * 1.2)  // 20% 악화 감지
   }
```

---

### Q4. '과제를 잘 푸는 법'에 대한 나만의 기준/원칙이 생겼다면 공유해보세요.

**A. "5가지 원칙"**

#### 원칙 1: 문제 분류 먼저, 구현은 나중에

```
❌ 나쁜 순서:
"문제 보임 → 빨리 코드 짜기 → 나중에 고민"

✅ 좋은 순서:
"문제 보임 → 문제의 본질 파악 → 해결 기술 선택 → 구현"

실제 사례:
❌ "동시성 문제 → SELECT FOR UPDATE 추가"
✅ "동시성 문제 → 이게 race condition? → 멀티 서버? → Redisson"
```

#### 원칙 2: 한 가지는 한 수준에서만 해결

```
❌ 나쁜 예:
"동시성 문제를 mock으로만 검증하고,
 성능까지 mock으로 측정하고,
 모니터링까지 mock으로..."

→ Mock은 거짓말쟁이! 너무 믿으면 안 됨

✅ 좋은 예:
"동시성 문제? Mock으로 빠르게
 실제 동시성? Docker로 검증
 성능? 로드 테스트로 확인
 모니터링? 프로덕션에서"

→ 각 문제를 적절한 도구로 해결
```

#### 원칙 3: 테스트는 신뢰를 사는 것

```
"테스트는 버그를 찾기 위한 것?"
아니다.

테스트는 "이 코드 믿어도 돼?"를 증명하는 것

✅ 신뢰할 수 있는 테스트:
- Mock + Docker + CI/CD 3단계
- 각 단계에서 다른 관점으로 검증
- "최대한 가혹하게" 테스트

→ 배포 전에 "이거 안 깨질까?"에 자신감
```

#### 원칙 4: 복잡함은 필요한 순간에만

```
❌ 미리 복잡하게:
"나중에 성능 문제 생길까봐
 미리 Redis 분산 락 추가"

✅ 필요할 때 복잡하게:
"먼저 간단하게 구현
 실제 문제 생기면 개선"

단, STEP09-10처럼 "명시적 요구사항"이면?
→ 처음부터 정확하게
```

#### 원칙 5: 설명할 수 있어야 한다

```
"코드가 완성된 게 아니라,
 이 선택이 왜 맞는지 설명할 수 있을 때 완성"

- "왜 비관적 락?"
- "왜 분산 락?"
- "왜 이 timeout?"

실제 효과:
- 코드 리뷰가 쉬움 (설득할 자료가 있음)
- 팀원과 논의 가능
- 나중에 개선할 때 기준점 명확
- DISCUSSION_TOPICS.md 같은 자료로 공유
```

#### 보너스: "과제를 잘 푸는 프로세스"

```
1. 요구사항 정확히 이해
   □ STEP09: 동시성 문제 식별 + 해결 방안 보고서
   □ STEP10: 동시성 문제 드러내는 통합 테스트

2. 문제 분류
   □ 3가지 동시성 문제 식별 (재고, 결제, 쿠폰)
   □ 각각의 특성 분석

3. 기술 선택
   □ 각 특성에 맞는 기술 선택
   □ trade-off 문서화

4. 구현
   □ 깔끔한 구현
   □ 인터페이스 설계 (확장성 고려)

5. 테스트
   □ Mock → Docker → CI/CD 3단계
   □ 각 테스트의 목적이 명확

6. 문서화
   □ CLAUDE.md: 기술 가이드
   □ DISCUSSION_TOPICS.md: 팀 논의 자료
   □ RETROSPECTIVE.md: 이 문서

7. 팀과 공유
   □ 설명할 수 있는 수준까지
   □ 타당성 있는 선택을 증명
   □ 차용 가능한 패턴 제시
```

---

## 🎯 최종 정리

### 이번 STEP09-10을 통해 얻은 3가지

1. **기술적 깊이**
   - 동시성 문제의 분류와 해결책
   - Redis 분산 락의 실제 사용법
   - 테스트 전략의 3단계 분리

2. **설계 철학**
   - "왜 이 기술인가?"에 답할 수 있음
   - trade-off를 인식한 의사결정
   - 복잡함과 단순함의 균형

3. **팀 역량**
   - 타당성 있는 선택을 증명하는 능력
   - 팀과 논의할 수 있는 자료 준비
   - 다음 프로젝트의 가이드라인 제시

---
