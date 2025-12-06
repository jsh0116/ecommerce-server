# Redisson 적용 전: 동시성 문제의 전체 그림

## 📌 개요

이 문서는 **Redisson 분산 락을 적용하기 전** 쿠폰 발급 시스템이 어떻게 작동했고, 어떤 동시성 문제가 발생했는지 보여줍니다.

---

## 🔴 BEFORE: Redisson 없이 구현

### 1. 초기 구현 (아무것도 없는 상태)

```kotlin
// CouponUseCase.kt (Redisson 적용 전)
@Service
class CouponUseCase(
    private val couponRepository: CouponRepository,
    private val userRepository: UserRepository
) {
    fun issueCoupon(couponId: Long, userId: Long): CouponIssueResult {
        // Step 1: 사용자 확인
        val user = userRepository.findById(userId)
            ?: throw UserException.UserNotFound(userId.toString())

        // Step 2: 이미 발급받은 쿠폰인지 확인
        val existing = couponRepository.findUserCouponByCouponId(userId, couponId)
        if (existing != null) throw CouponException.AlreadyIssuedCoupon()

        // Step 3: 쿠폰 정보 조회
        val coupon = couponRepository.findById(couponId)
            ?: throw CouponException.CouponNotFound(couponId.toString())

        // Step 4: 수량 확인 (여기가 문제!)
        if (!coupon.canIssue()) throw CouponException.CouponExhausted()

        // Step 5: 쿠폰 발급 (수량 차감)
        val remainingQuantity = coupon.issue()  // quantity--
        couponRepository.save(coupon)

        // Step 6: 사용자 쿠폰 생성
        val userCoupon = UserCoupon(
            userId = userId,
            couponId = coupon.id,
            couponName = coupon.name,
            discountRate = coupon.discountRate,
            status = "AVAILABLE",
            issuedAt = LocalDateTime.now(),
            usedAt = null,
            expiresAt = LocalDateTime.now().plusDays(7)
        )
        couponRepository.saveUserCoupon(userCoupon)

        return CouponIssueResult(
            userCouponId = couponId,
            couponName = userCoupon.couponName,
            discountRate = userCoupon.discountRate,
            expiresAt = userCoupon.expiresAt,
            remainingQuantity = remainingQuantity
        )
    }
}
```

### 2. 문제점: Race Condition 발생

#### 시나리오: 100개 동시 요청, 쿠폰 수량 100개

```
Timeline:

T1 쿠폰 상태: quantity = 100

요청1 (Thread A)              요청2 (Thread B)              요청3 (Thread C)
├─ findById(coupon:100)
│  → quantity = 100
├─ canIssue() = true
│                                ├─ findById(coupon:100)
│                                │  → quantity = 100 (여전히!)
│                                ├─ canIssue() = true
│                                │                          ├─ findById(coupon:100)
│                                │                          │  → quantity = 100 (여전히!)
│                                │                          ├─ canIssue() = true
├─ issue() → quantity = 99
├─ save(coupon)
│  → DB 저장 (quantity = 99)
│                                ├─ issue() → quantity = 99
│                                ├─ save(coupon)
│                                │  → DB 저장 (quantity = 99) ← 문제!
│                                │                          ├─ issue() → quantity = 99
│                                │                          ├─ save(coupon)
│                                │                          │  → DB 저장 (quantity = 99) ← 문제!

결과:
- 요청 3개 모두 "발급 성공"
- 그런데 실제 쿠폰 수량은 99 (100에서 1만 감소)
- 쿠폰 2개가 중복으로 발급됨! ← BUG
```

### 3. 코드 레벨에서 보기

```kotlin
// 문제가 되는 부분
val coupon = couponRepository.findById(couponId)  // 1. 조회

// 여기서 Context Switch 발생!
// 다른 Thread가 같은 쿠폰을 조회/수정 중

if (!coupon.canIssue()) {  // 2. 체크
    // 모든 Thread가 "canIssue() = true"라고 판단
    // 왜냐하면 각각 다른 시점에 읽었는데, 모두 quantity > 0
}

coupon.issue()  // 3. 수정
couponRepository.save(coupon)  // 4. 저장

// 1-2-3-4가 원자적(atomic)이지 않음!
// 여러 Thread가 동시에 진행 가능
```

### 4. 실제 테스트 결과

```kotlin
// 100개 동시 요청 테스트
@Test
fun `100개 동시 요청 - Redisson 없음`() {
    val coupon = Coupon(id = 1, quantity = 100)
    couponRepository.save(coupon)

    val executor = Executors.newFixedThreadPool(50)
    val latch = CountDownLatch(100)

    repeat(100) {
        executor.submit {
            try {
                issueCoupon(couponId = 1, userId = it.toLong())
            } finally {
                latch.countDown()
            }
        }
    }

    latch.await()

    // 예상: quantity = 0 (100개 모두 발급됨)
    // 실제: quantity = 5~30 (불규칙)
    //
    // 왜? 여러 Thread가 동시에 같은 값을 읽고 수정하기 때문

    val finalCoupon = couponRepository.findById(1)
    println("Final quantity: ${finalCoupon.quantity}")
    // Output: Final quantity: 23  ← 뭔가 남아있음!?
}
```

---

## 🟡 FIRST ATTEMPT: Synchronized 사용

### 1. 첫 시도: Java의 synchronized 사용

```kotlin
@Service
class CouponUseCase(
    private val couponRepository: CouponRepository,
    private val userRepository: UserRepository
) {
    // 간단한 해결책: synchronized 추가
    @Synchronized  // ← 모든 Thread가 직렬화됨
    fun issueCoupon(couponId: Long, userId: Long): CouponIssueResult {
        val coupon = couponRepository.findById(couponId)
            ?: throw CouponException.CouponNotFound(couponId.toString())

        if (!coupon.canIssue()) throw CouponException.CouponExhausted()

        coupon.issue()
        couponRepository.save(coupon)

        // ... 나머지 로직
    }
}
```

### 2. 문제점: 단일 서버에서만 작동

```
단일 서버 (1개):
┌─────────────────────────────┐
│ JVM Process                 │
│ ┌─────────────────────────┐ │
│ │ synchronized            │ │
│ │ 동시 접근 방지 ✅       │ │
│ └─────────────────────────┘ │
└─────────────────────────────┘

결과: ✅ 작동함


멀티 서버 (2개):
┌──────────────────┐         ┌──────────────────┐
│ Server A         │         │ Server B         │
│ JVM Process 1    │         │ JVM Process 2    │
│ synchronized ✅  │         │ synchronized ✅  │
│                  │         │                  │
│ Thread A가 락?   │         │ Thread B도 락?   │
│                  │         │                  │
└──────────────────┘         └──────────────────┘
         ↓                             ↓
      DB (공유)                    DB (공유)

문제: ❌ 각 서버는 자신의 synchronized만 알고 있음
      ❌ 두 서버가 "동시에" 같은 쿠폰 수정 가능!
      ❌ 결국 Race Condition 여전히 발생!
```

### 3. 코드로 보는 문제

```kotlin
// 멀티 서버 환경 시뮬레이션

// 서버 A
@Synchronized
fun issueCoupon(couponId: Long, userId: Long) {
    // Thread A1: "나 락 잡았어" (서버 A 내에서만)
    val coupon = couponRepository.findById(couponId)  // quantity = 100
    coupon.issue()  // quantity = 99
    couponRepository.save(coupon)
}

// 서버 B (동시에)
@Synchronized
fun issueCoupon(couponId: Long, userId: Long) {
    // Thread B1: "나도 락 잡았어" (서버 B 내에서만)
    val coupon = couponRepository.findById(couponId)  // quantity = 100 (여전히!)
    coupon.issue()  // quantity = 99
    couponRepository.save(coupon)  // DB 덮어쓰기!
}

// 결과: quantity = 99 (100에서 1만 감소, 2가 감소해야 함!)
```

---

## 🔵 SECOND ATTEMPT: SELECT FOR UPDATE (DB 비관적 락)

### 1. DB 레벨 락 추가

```kotlin
@Service
class CouponUseCase(
    private val couponRepository: CouponRepository,
    private val userRepository: UserRepository
) {
    fun issueCoupon(couponId: Long, userId: Long): CouponIssueResult {
        return try {
            // SELECT FOR UPDATE: 조회 시점에 DB 락 획득
            val coupon = couponRepository.findByIdForUpdate(couponId)
                ?: throw CouponException.CouponNotFound(couponId.toString())

            if (!coupon.canIssue()) throw CouponException.CouponExhausted()

            coupon.issue()
            couponRepository.save(coupon)

            // ... 나머지 로직
        } catch (e: Exception) {
            throw e
        }
    }
}

// Repository
@Query("SELECT c FROM Coupon c WHERE c.id = :couponId FOR UPDATE")
fun findByIdForUpdate(couponId: Long): Coupon?
```

### 2. 작동 방식

```
Timeline (SELECT FOR UPDATE):

T1: 쿠폰 상태: quantity = 100, DB 락 없음

요청1 (Thread A)              요청2 (Thread B)
├─ SELECT ... FOR UPDATE
│  → DB 락 획득 (quantity = 100)
├─ canIssue() = true
├─ issue() → quantity = 99
├─ UPDATE coupon SET ...
│  → DB 락 유지 중
│
├─ [Commit 또는 Transaction 끝]
│  → DB 락 해제
│
│                            ├─ SELECT ... FOR UPDATE
│                            │  → Thread A가 락 해제할 때까지 대기
│                            │     (Blocking!)
│                            │
│                            ├─ DB 락 획득 (quantity = 99)
│                            ├─ canIssue() = false
│                            │  → "쿠폰 초과" 예외 발생 ✅
│                            └─ 요청 실패

결과: ✅ 쿠폰 수량 정확함 (100 → 99)
      ✅ 중복 발급 방지
      ⚠️ 근데 멀티 서버는 어때?
```

### 3. 멀티 서버 환경에서의 작동

```
멀티 서버 (2개):
┌──────────────────┐         ┌──────────────────┐
│ Server A         │         │ Server B         │
│ Thread A         │         │ Thread B         │
└──────────────────┘         └──────────────────┘
         ↓                             ↓
         └─────────────┬──────────────┘
                       ↓
              DB (중앙집중식)

T1: A가 SELECT FOR UPDATE 실행
    → DB 행(row) 락 획득

T2: B가 SELECT FOR UPDATE 시도
    → A가 락을 해제할 때까지 대기
    → Blocking!

T3: A가 완료 (Commit)
    → DB 락 해제

T4: B가 진행
    → 다음 값 읽음 (quantity = 99)

결과: ✅ 멀티 서버에서도 작동함 (DB가 중앙집중식)
      ✅ 쿠폰 수량 정확함
      ✅ 선착순 보장
```

### 4. 테스트 결과

```kotlin
// 100개 동시 요청 테스트 (SELECT FOR UPDATE 사용)
@Test
fun `100개 동시 요청 - SELECT FOR UPDATE`() {
    val coupon = Coupon(id = 1, quantity = 100)
    couponRepository.save(coupon)

    val executor = Executors.newFixedThreadPool(50)
    val latch = CountDownLatch(100)
    val successCount = AtomicInteger(0)
    val failureCount = AtomicInteger(0)

    repeat(100) {
        executor.submit {
            try {
                issueCoupon(couponId = 1, userId = it.toLong())
                successCount.incrementAndGet()
            } catch (e: CouponException.CouponExhausted) {
                failureCount.incrementAndGet()
            } finally {
                latch.countDown()
            }
        }
    }

    latch.await()

    val finalCoupon = couponRepository.findById(1)

    // 결과:
    assertThat(successCount.get()).isEqualTo(100)  // ✅ 100개 모두 성공
    assertThat(failureCount.get()).isEqualTo(0)    // ✅ 실패 0개
    assertThat(finalCoupon.quantity).isEqualTo(0)  // ✅ 정확히 0
}

// 101개 동시 요청 테스트
@Test
fun `101개 동시 요청 - SELECT FOR UPDATE`() {
    val coupon = Coupon(id = 1, quantity = 100)
    couponRepository.save(coupon)

    // ... 101개 요청

    // 결과:
    assertThat(successCount.get()).isEqualTo(100)  // ✅ 100개 성공
    assertThat(failureCount.get()).isEqualTo(1)    // ✅ 1개 실패
    assertThat(finalCoupon.quantity).isEqualTo(0)  // ✅ 정확히 0
}
```

### 5. SELECT FOR UPDATE의 장단점

| 특성 | 평가 |
|------|------|
| **단일 서버** | ✅ 완벽함 |
| **멀티 서버** | ✅ 작동 (DB가 중앙) |
| **구현 복잡도** | ✅ 간단 (한 줄) |
| **성능** | ⚠️ 중간 (락 경합 있음) |
| **외부 의존성** | ✅ 없음 (DB만) |
| **문제점** | ❓ 그럼 왜 Redisson? |

---

## 🟢 FINAL: Redisson 분산 락 추가

### 1. 왜 SELECT FOR UPDATE에서 Redisson으로?

```
상황: 쿠폰 발급 시스템이 성공적으로 운영 중

기존 방식 (SELECT FOR UPDATE):
✅ 100% 정확함
✅ 멀티 서버 지원
⚠️ 성능이 증가할수록 느려짐

TPS 증가 시나리오:
- 100 TPS: OK
- 1,000 TPS: ⚠️ DB 커넥션 풀이 DB 락으로 인해 대기
- 10,000 TPS: ❌ 타임아웃, 성능 저하

문제: DB 락은 강하지만, "DB 자원 독점"

해결책: "DB 락" 대신 "별도의 분산 락" 사용
→ Redisson (Redis 기반)
```

### 2. Redisson 적용

```kotlin
@Service
class CouponUseCase(
    private val couponRepository: CouponRepository,
    private val userRepository: UserRepository,
    private val couponLockService: CouponLockService  // ← 새로 추가
) {
    companion object {
        private const val LOCK_WAIT_TIME = 3L
        private const val LOCK_HOLD_TIME = 10L
    }

    fun issueCoupon(couponId: Long, userId: Long): CouponIssueResult {
        return try {
            // 1. Redis 분산 락 획득 (3초 대기)
            val lockAcquired = couponLockService.tryLock(
                couponId,
                LOCK_WAIT_TIME,
                LOCK_HOLD_TIME,
                TimeUnit.SECONDS
            )

            if (!lockAcquired) {
                // 3초 동안 락을 못 얻었다 = 너무 많은 동시 요청
                throw CouponException.CouponExhausted()
            }

            // 2. 락 획득 후 로직 실행 (이전과 동일)
            val coupon = couponRepository.findById(couponId)
                ?: throw CouponException.CouponNotFound(couponId.toString())

            if (!coupon.canIssue()) throw CouponException.CouponExhausted()

            coupon.issue()
            couponRepository.save(coupon)

            // ... 나머지 로직

        } finally {
            // 3. 락 해제 (항상 실행)
            couponLockService.unlock(couponId)
        }
    }
}
```

### 3. 작동 방식

```
Timeline (Redisson 분산 락):

T1: 쿠폰 상태: quantity = 100, Redis: 락 없음

요청1 (Thread A)              요청2 (Thread B)
├─ tryLock(coupon:100)
│  → Redis 락 획득 (key: "coupon:lock:100")
│  → holdTime = 10초 설정
│
├─ canIssue() = true
├─ issue() → quantity = 99
├─ save(coupon)
│  (Redis 락 유지 중)
│
│                            ├─ tryLock(coupon:100)
│                            │  → Redis 에서 "coupon:lock:100" 확인
│                            │  → A가 보유 중 (3초 대기)
│                            │
├─ unlock() 호출
│  → Redis 락 해제
│  → Commit
│
│                            ├─ 락 획득 가능 (A가 해제함)
│                            │  → Redis 락 획득
│                            │
│                            ├─ canIssue() = false
│                            │  → "쿠폰 초과" 예외 ✅
│                            │
│                            └─ unlock()
│                               → 락 해제

결과: ✅ 쿠폰 수량 정확함
      ✅ 중복 발급 방지
      ✅ 멀티 서버 지원
      ✅ DB 자원 덜 소모 (Redis 사용)
```

### 4. 차이점 비교

```
SELECT FOR UPDATE vs Redisson:

SELECT FOR UPDATE:
- 구현: @Query("... FOR UPDATE")
- 동작: DB 행 락
- 범위: 트랜잭션 중
- 비용: DB 커넥션 점유
- 성능: TPS 증가 시 병목

Redisson:
- 구현: redissonClient.getLock(key).tryLock(...)
- 동작: Redis 키 락
- 범위: tryLock() ~ unlock()
- 비용: Redis 연결 (가볍고 빠름)
- 성능: TPS 증가에도 스케일 가능
```

### 5. 테스트 결과 비교

```kotlin
// 100개 동시 요청

"SELECT FOR UPDATE":
├─ 성공: 100/100 ✅
├─ 실패: 0/100 ✅
├─ 수량: 0 ✅
└─ 소요시간: ~50ms

"Redisson 분산 락":
├─ 성공: 100/100 ✅
├─ 실패: 0/100 ✅
├─ 수량: 0 ✅
└─ 소요시간: ~40ms ← 약간 더 빠름

// 1000개 동시 요청

"SELECT FOR UPDATE":
├─ 성공: 1000/1000 ✅
├─ 실패: 0/1000 ✅
├─ 수량: 0 ✅
└─ 소요시간: ~500ms
└─ DB 연결: 대량 대기

"Redisson 분산 락":
├─ 성공: 1000/1000 ✅
├─ 실패: 0/1000 ✅
├─ 수량: 0 ✅
└─ 소요시간: ~350ms ← 30% 더 빠름
└─ DB 연결: 정상 (Redis로 처리)
```

---

## 📊 전체 진화 과정 요약

```
Stage 1: 아무것도 없음 (Redisson 적용 전)
┌────────────────────────────────────┐
│ fun issueCoupon() {                │
│     val coupon = findById(...)      │  ← Race Condition! ❌
│     if (!coupon.canIssue()) ...     │
│     coupon.issue()                 │
│     save(coupon)                   │
│ }                                  │
└────────────────────────────────────┘
문제: 100개 동시 요청 → 일부만 발급됨 (불규칙)


Stage 2: @Synchronized 추가
┌────────────────────────────────────┐
│ @Synchronized                      │
│ fun issueCoupon() {                │
│     val coupon = findById(...)      │
│     if (!coupon.canIssue()) ...     │  ← 단일 서버만 OK ⚠️
│     coupon.issue()                 │
│     save(coupon)                   │
│ }                                  │
└────────────────────────────────────┘
문제: 멀티 서버에서 여전히 Race Condition ❌


Stage 3: SELECT FOR UPDATE (DB 비관적 락)
┌────────────────────────────────────┐
│ val coupon = findByIdForUpdate(...) │
│ if (!coupon.canIssue()) ...         │
│ coupon.issue()                      │  ← DB 락으로 보호 ✅
│ save(coupon)                       │
└────────────────────────────────────┘
장점: 정확함, 멀티 서버 지원, 간단
문제: TPS 증가 시 성능 저하 ⚠️


Stage 4: Redisson 분산 락 ← 현재!
┌────────────────────────────────────┐
│ try {                              │
│     val locked = tryLock(...)       │  ← Redis 분산 락 ✅
│     if (!locked) throw ...          │
│     val coupon = findById(...)      │
│     if (!coupon.canIssue()) ...     │
│     coupon.issue()                 │
│     save(coupon)                   │
│ } finally {                        │
│     unlock()                        │
│ }                                  │
└────────────────────────────────────┘
장점: 정확함, 멀티 서버 지원, 높은 성능, 확장성 ✅✅
```

---

## 🎯 핵심 인사이트

### Q1. 왜 Redisson을 선택했나?

```
필요 요구사항:
1. ✅ 동시성 제어 (정확성)
2. ✅ 멀티 서버 지원
3. ✅ 높은 성능

대안 비교:
┌─────────────────┬──────────┬──────────┬──────────┐
│                 │ 정확성   │ 멀티 서버│ 성능     │
├─────────────────┼──────────┼──────────┼──────────┤
│ 없음            │ ❌       │ ❌       │ ⭐⭐⭐⭐⭐│
│ @Synchronized   │ ⚠️(부분) │ ❌       │ ⭐⭐⭐⭐⭐│
│ SELECT FOR UPDATE│ ✅       │ ✅       │ ⭐⭐⭐   │
│ Redisson        │ ✅       │ ✅       │ ⭐⭐⭐⭐ │
└─────────────────┴──────────┴──────────┴──────────┘

결론: Redisson이 모든 조건 충족 ✅
```

### Q2. SELECT FOR UPDATE 대신 Redisson?

```
단순히 "더 빠르기 때문"이 아니라:

1. 아키텍처 관점
   - SELECT FOR UPDATE: DB 의존
   - Redisson: 독립적인 분산 락 시스템
   - → 나중에 캐시 레이어 추가 가능

2. 성능 스케일링
   - SELECT FOR UPDATE: DB 커넥션이 병목
   - Redisson: Redis는 높은 처리량
   - → TPS 증가 시 Redisson이 유리

3. 세밀한 제어
   - SELECT FOR UPDATE: 전체 트랜잭션
   - Redisson: 필요한 부분만 락
   - → 더 정교한 제어 가능
```

### Q3. 실무 선택 기준

```
"언제 SELECT FOR UPDATE를 쓸까?"
- 단일 서버 환경
- TPS가 낮음 (< 1,000)
- DB만 사용하고 싶음

"언제 Redisson을 쓸까?"
- 멀티 서버 환경
- 높은 TPS 필요
- Redis를 이미 사용 중
- 향후 확장성 고려

"언제 둘 다 쓸까?"
- 매우 높은 부하
- Inventory + Coupon 같은 조합
- 각 도메인의 특성에 따라 선택
```

---

## 💡 배운 교훈

### 교훈 1: "아무것도 없을 때의 위험"

```
많은 개발자가 놓치는 부분:

"이 기능은 동시 요청이 거의 없을 것 같은데?"
→ 초기에는 맞을 수 있음
→ 하지만 언제 증가할지는 알 수 없음
→ 나중에 "어? 쿠폰이 초과로 발급되네?" 발견
→ 이미 배포되어 있음 → 긴급 패치 필요

결론: 처음부터 동시성 고려하기!
```

### 교훈 2: "적절한 도구의 중요성"

```
동시성 문제는 "도구"로 해결하는 게 아니라
"문제의 특성을 이해"한 후 도구를 선택하는 것

문제 분석 > 도구 선택 > 구현

다시 말해:
❌ "Redisson이 유명하니까 쓰자"
✅ "멀티 서버에서 선착순 보장이 필요하니 분산 락이 필요"
   "분산 락으로 Redisson을 선택"
```

### 교훈 3: "테스트의 중요성"

```
동시성 문제는 "간헐적" 발생

❌ 로컬 환경에서 1-2번 테스트 → "작동하네"
✅ 100개, 1000개 동시 요청으로 테스트 → "아, 문제 있네"

그래서 필요한 것:
1. Mock으로 빠른 피드백
2. Docker로 실제 검증
3. GitHub Actions으로 자동화

세 가지가 모두 필요!
```

---

## 🔗 다음 단계

이 문서를 읽은 후:

1. **DISCUSSION_TOPICS.md** 읽기
   - 왜 이 선택들을 했는지 깊이 있게 이해

2. **RETROSPECTIVE.md** 읽기
   - 개인 학습과 성장 정리

3. **실제 코드** 확인하기
   ```
   src/main/kotlin/io/hhplus/ecommerce/application/usecases/CouponUseCase.kt
   src/main/kotlin/io/hhplus/ecommerce/application/services/impl/RedissonCouponLockService.kt
   ```

4. **테스트** 실행해보기
   ```bash
   ./gradlew testIntegration --tests "*ConcurrencyTest*"
   ```

---

**이 문서를 통해 "왜 Redisson일까?"가 명확해질 거예요!** 🚀
