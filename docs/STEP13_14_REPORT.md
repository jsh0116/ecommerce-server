# STEP 13 & 14 구현 보고서

## 📋 목차
1. [프로젝트 개요](#프로젝트-개요)
2. [STEP 13: Redis 기반 상품 랭킹 시스템](#step-13-redis-기반-상품-랭킹-시스템)
3. [STEP 14: Redis 기반 선착순 쿠폰 발급 시스템](#step-14-redis-기반-선착순-쿠폰-발급-시스템)
4. [테스트 결과](#테스트-결과)
5. [한계점 및 개선 방향](#한계점-및-개선-방향)
6. [회고](#회고)

---

## 프로젝트 개요

### 목표
- Redis의 다양한 자료구조를 활용하여 실시간 랭킹 시스템과 선착순 쿠폰 발급 시스템을 구현
- 기존 RDBMS 기반 로직을 Redis 기반으로 전환하여 성능 개선 및 동시성 제어 강화
- 통합 테스트를 통한 기능 검증 및 동시성 안정성 확보

### 기술 스택
- **Backend**: Kotlin 1.9.21 + Spring Boot 3.2.0
- **Cache**: Redis 7-alpine
- **Testing**: JUnit 5, AssertJ, TestContainers

---

## STEP 13: Redis 기반 상품 랭킹 시스템

### 1. 배경 및 문제 정의

**문제점**
- 기존 RDBMS 기반 랭킹 조회는 매번 집계 쿼리를 실행해야 하므로 성능 저하 발생
- 실시간 랭킹 변동사항을 즉시 반영하기 어려움
- 대량의 주문 발생 시 집계 쿼리가 DB에 부하를 가중

**해결 방안**
- Redis Sorted Set을 활용한 실시간 랭킹 시스템 구축
- 주문 결제 시점에 Redis에 판매량 증가 (원자적 연산)
- TTL을 활용한 일간/주간 랭킹 자동 만료 및 메모리 관리

### 2. 설계 및 구현

#### 2.1 Redis 자료구조 선택
**Sorted Set (ZSet) 선택 이유**
- score 기반 자동 정렬 (O(log N))
- ZINCRBY를 통한 원자적 카운터 증가
- ZREVRANGE, ZREVRANK를 통한 빠른 랭킹 조회
- score 값으로 판매량을 저장하여 자동 순위 계산

#### 2.2 Key 네이밍 전략
```
ranking:products:daily:{yyyyMMdd}    # 일간 랭킹
ranking:products:weekly:{yyyy-Www}   # 주간 랭킹
```
- 날짜별 키 분리로 기간별 랭킹 관리
- TTL 설정으로 자동 만료 (일간: 7일, 주간: 30일)

#### 2.3 핵심 구현 코드

**ProductRankingService.kt**
```kotlin
fun incrementSales(productId: Long, quantity: Int, date: LocalDate = LocalDate.now()) {
    val dailyKey = getDailyRankingKey(date)
    val weeklyKey = getWeeklyRankingKey(date)

    // Redis Sorted Set에 판매량 증가 (원자적)
    redisTemplate.opsForZSet().incrementScore(dailyKey, productId.toString(), quantity.toDouble())
    redisTemplate.opsForZSet().incrementScore(weeklyKey, productId.toString(), quantity.toDouble())

    // TTL 설정 (최초 1회만 적용)
    setTTLIfNotExists(dailyKey, DAILY_TTL_DAYS, TimeUnit.DAYS)
    setTTLIfNotExists(weeklyKey, WEEKLY_TTL_DAYS, TimeUnit.DAYS)
}

fun getTopProductsDaily(limit: Int = 10, date: LocalDate = LocalDate.now()): List<RankingItem> {
    val key = getDailyRankingKey(date)
    // ZREVRANGE: 높은 score순으로 조회
    val productIds = redisTemplate.opsForZSet()
        .reverseRangeWithScores(key, 0, (limit - 1).toLong()) ?: emptySet()

    return productIds.mapIndexed { index, scoreValue ->
        val productId = scoreValue.value?.toLongOrNull()
        val salesCount = scoreValue.score?.toLong() ?: 0L
        val product = productRepository.findById(productId)

        RankingItem(
            rank = index + 1,
            productId = productId,
            productName = product?.name,
            salesCount = salesCount
        )
    }
}
```

**OrderUseCase 통합**
```kotlin
fun processPayment(orderId: String): PaymentResult {
    // ... 주문 처리 로직 ...

    // Redis 랭킹 업데이트 (주문 완료 시)
    order.items.forEach { item ->
        productRankingService.incrementSales(item.productId, item.quantity)
    }

    return PaymentResult.success(order)
}
```

#### 2.4 API 엔드포인트

**ProductController.kt**
```kotlin
@GetMapping("/ranking/daily")
fun getDailyRanking(@RequestParam(defaultValue = "10") limit: Int): ResponseEntity<ProductRankingResponse>

@GetMapping("/ranking/weekly")
fun getWeeklyRanking(@RequestParam(defaultValue = "10") limit: Int): ResponseEntity<ProductRankingResponse>

@GetMapping("/{productId}/rank")
fun getProductRank(@PathVariable productId: String): ResponseEntity<ProductRankResponse>
```

### 3. 기술적 특징

#### 3.1 원자성 보장
- `ZINCRBY`: Redis의 단일 스레드 특성으로 원자적 증가 보장
- 동시에 100개의 주문이 들어와도 정확한 카운팅

#### 3.2 성능 최적화
- O(log N) 시간 복잡도로 빠른 랭킹 계산
- DB 쿼리 없이 Redis에서 즉시 응답
- 주문 처리 트랜잭션과 분리되어 DB 부하 최소화

#### 3.3 메모리 관리
- TTL 설정으로 오래된 랭킹 데이터 자동 삭제
- 일간 랭킹: 7일 보관
- 주간 랭킹: 30일 보관

### 4. 테스트 결과

**ProductRankingIntegrationTest.kt**
- 총 13개 테스트 시나리오 작성
- 모든 테스트 통과 (100% 성공률)

**주요 테스트 시나리오**
1. 판매량 증가 및 누적 테스트
2. 일간/주간 TOP N 조회 테스트
3. 특정 상품 순위 조회 테스트
4. 동시성 테스트 (10개 스레드 * 10회 증가 = 100회 정확성)
5. Redis Key 전략 및 TTL 검증 테스트

**동시성 테스트 결과**
```kotlin
@Test
fun `여러 스레드에서 동시에 판매량을 증가시켜도 정확하게 집계된다`() {
    val productId = testProducts[0].id
    val threadCount = 10
    val incrementPerThread = 10

    // 10개 스레드가 각각 10번씩 증가 (총 100번)
    val threads = (1..threadCount).map {
        Thread {
            repeat(incrementPerThread) {
                productRankingService.incrementSales(productId, 1)
            }
        }
    }

    threads.forEach { it.start() }
    threads.forEach { it.join() }

    // 검증: 정확히 100개 카운트
    val totalSales = productRankingService.getProductSalesCount(productId)
    assertThat(totalSales).isEqualTo(100L)
}
```
✅ **결과**: 정확히 100개 카운트, race condition 없음

---

## STEP 14: Redis 기반 선착순 쿠폰 발급 시스템

### 1. 배경 및 문제 정의

**문제점**
- 기존 분산락 기반 쿠폰 발급은 모든 요청이 락을 획득해야 하므로 성능 병목 발생
- 수량 체크와 발급 기록 사이에 race condition 발생 가능
- 중복 발급 체크를 DB에서 수행하여 DB 부하 증가

**해결 방안**
- Redis를 사전 체크 레이어로 활용하여 불필요한 락 획득 차단
- Redis Set으로 중복 발급 빠르게 체크
- Redis String INCR의 원자성을 활용한 정확한 수량 관리

### 2. 설계 및 구현

#### 2.1 Redis 자료구조 선택

**1) Set (중복 발급 방지)**
- `SADD`: O(1) 시간 복잡도로 빠른 중복 체크
- `SISMEMBER`: 사용자가 이미 발급받았는지 즉시 확인

**2) String with INCR (수량 관리)**
- `INCR`: 원자적 카운터 증가
- quota 초과 시 `DECR`로 롤백

#### 2.2 Key 네이밍 전략
```
coupon:issued:{couponId}    # Set: 발급받은 userId 저장
coupon:count:{couponId}     # String: 현재 발급 수량
coupon:quota:{couponId}     # String: 최대 발급 가능 수량
```

#### 2.3 핵심 구현 코드

**CouponIssuanceService.kt**
```kotlin
fun checkIssuanceEligibility(couponId: Long, userId: Long) {
    val issuedSetKey = "coupon:issued:$couponId"

    // 1. 중복 발급 체크 (Redis Set)
    val alreadyIssued = redisTemplate.opsForSet().isMember(issuedSetKey, userId.toString())
    if (alreadyIssued == true) {
        throw CouponException.AlreadyIssuedCoupon()
    }

    // 2. 수량 체크 (Redis String)
    val countKey = "coupon:count:$couponId"
    val quotaKey = "coupon:quota:$couponId"

    val quota = redisTemplate.opsForValue().get(quotaKey)?.toLongOrNull()
        ?: throw CouponException.CouponNotFound(couponId.toString())

    val currentCount = redisTemplate.opsForValue().get(countKey)?.toLongOrNull() ?: 0L

    if (currentCount >= quota) {
        throw CouponException.CouponExhausted()
    }
}

fun recordIssuance(couponId: Long, userId: Long): Long {
    val issuedSetKey = "coupon:issued:$couponId"
    val countKey = "coupon:count:$couponId"
    val quotaKey = "coupon:quota:$couponId"

    try {
        // 1. 발급 수량 증가 (atomic - 가장 먼저 실행하여 동시성 제어)
        val newCount = redisTemplate.opsForValue().increment(countKey) ?: 1L
        redisTemplate.expire(countKey, TTL_DAYS, TimeUnit.DAYS)

        // 2. quota 체크 - 초과 시 롤백
        val quota = redisTemplate.opsForValue().get(quotaKey)?.toLongOrNull() ?: 0L
        if (newCount > quota) {
            // 롤백: 카운트 감소
            redisTemplate.opsForValue().decrement(countKey)
            throw CouponException.CouponExhausted()
        }

        // 3. Set에 userId 추가 (발급 기록)
        redisTemplate.opsForSet().add(issuedSetKey, userId.toString())
        redisTemplate.expire(issuedSetKey, TTL_DAYS, TimeUnit.DAYS)

        return quota - newCount
    } catch (e: BusinessRuleViolationException) {
        logger.warn("쿠폰 발급 실패: couponId={}, userId={}, reason={}", couponId, userId, e.message)
        throw e
    }
}
```

**CouponUseCase 통합**
```kotlin
fun issueCoupon(couponId: Long, userId: Long): CouponIssueResult {
    // Redis 사전 체크 (Lock 없이 빠르게 실패)
    try {
        couponIssuanceService.checkIssuanceEligibility(couponId, userId)
    } catch (e: BusinessRuleViolationException) {
        logger.info("쿠폰 발급 사전 체크 실패", e)
        throw e
    }

    // 분산락 획득 (사전 체크를 통과한 경우만)
    val lockKey = "coupon:lock:$couponId"
    val lockAcquired = distributedLockService.tryLock(lockKey, 3L, 10L, TimeUnit.SECONDS)

    if (!lockAcquired) {
        throw CouponException.CouponLockTimeout()
    }

    try {
        // 재검증
        couponIssuanceService.checkIssuanceEligibility(couponId, userId)

        // DB 저장 (사용자, 쿠폰, UserCoupon)
        val user = userRepository.findById(userId) ?: throw UserException.UserNotFound()
        val coupon = couponRepository.findById(couponId) ?: throw CouponException.CouponNotFound()

        if (!coupon.canIssue()) throw CouponException.CouponExhausted()

        coupon.issue()
        couponRepository.save(coupon)

        val userCoupon = UserCoupon(userId, couponId, ...)
        couponRepository.saveUserCoupon(userCoupon)

        // Redis에 발급 기록 (DB 저장 성공 후)
        val remaining = couponIssuanceService.recordIssuance(couponId, userId)

        return CouponIssueResult(couponId, remaining)
    } finally {
        distributedLockService.unlock(lockKey)
    }
}
```

### 3. 기술적 특징

#### 3.1 2단계 동시성 제어

**1단계: Redis 사전 체크 (Fast Fail)**
- 락 없이 빠르게 실패 처리
- 중복 발급 및 수량 초과를 즉시 감지
- 불필요한 락 획득 차단하여 성능 향상

**2단계: 분산락 + DB 저장**
- 사전 체크를 통과한 경우만 락 획득
- DB 정합성 보장
- Redis와 DB 이중 관리

#### 3.2 원자성 보장

**INCR의 원자성 활용**
```
1. INCR count → newCount = 51
2. if (newCount > 50) → quota 초과 감지
3. DECR count → 롤백 (50으로 복원)
4. throw CouponExhausted
```
- INCR 자체는 원자적이므로 정확한 카운팅 보장
- quota 초과 시 즉시 롤백하여 정확히 50장만 발급

#### 3.3 TTL 기반 메모리 관리
- 발급 완료 후 30일간 보관
- 만료된 쿠폰 데이터 자동 삭제

### 4. 테스트 결과

**CouponIssuanceIntegrationTest.kt**
- 총 10개 테스트 시나리오 작성
- 모든 테스트 통과 (100% 성공률)

**주요 테스트 시나리오**
1. 쿠폰 초기화 테스트
2. 중복 발급 체크 테스트 (Redis Set 활용)
3. 선착순 수량 관리 테스트 (Redis INCR 활용)
4. **동시성 테스트 (100명이 50장 쿠폰에 동시 요청)**
5. 쿠폰 상태 조회 테스트
6. Redis Key 전략 및 TTL 검증 테스트

**동시성 테스트 결과**
```kotlin
@Test
fun `100명이 동시에 50장 쿠폰에 요청해도 정확히 50장만 발급된다`() {
    val couponId = 1L
    val totalQuantity = 50
    val totalUsers = 100
    couponIssuanceService.initializeCoupon(couponId, totalQuantity)

    val successCount = AtomicInteger(0)
    val failCount = AtomicInteger(0)

    val threads = (1L..totalUsers.toLong()).map { userId ->
        Thread {
            try {
                couponIssuanceService.checkIssuanceEligibility(couponId, userId)
                couponIssuanceService.recordIssuance(couponId, userId)
                successCount.incrementAndGet()
            } catch (e: CouponException.CouponExhausted) {
                failCount.incrementAndGet()
            }
        }
    }

    threads.forEach { it.start() }
    threads.forEach { it.join() }

    // 검증
    assertThat(successCount.get()).isEqualTo(50)
    assertThat(failCount.get()).isEqualTo(50)

    val status = couponIssuanceService.getCouponStatus(couponId)
    assertThat(status.issuedCount).isEqualTo(50L)
    assertThat(status.remainingQuantity).isEqualTo(0L)
}
```
✅ **결과**: 정확히 50장만 발급, 초과 발급 없음

### 5. 동시성 문제 해결 과정

#### 5.1 초기 문제
- **현상**: 50장 쿠폰에 100명 요청 시 96장 발급됨
- **원인**: `checkIssuanceEligibility()`와 `recordIssuance()` 사이에 race condition 발생

#### 5.2 해결 방안
```kotlin
// Before (문제 있는 코드)
fun recordIssuance(couponId: Long, userId: Long): Long {
    // 1. Set에 userId 추가
    redisTemplate.opsForSet().add(issuedSetKey, userId.toString())

    // 2. 발급 수량 증가
    val newCount = redisTemplate.opsForValue().increment(countKey)

    // 문제: 1번과 2번 사이에 다른 스레드가 끼어들 수 있음
}

// After (수정된 코드)
fun recordIssuance(couponId: Long, userId: Long): Long {
    // 1. INCR 먼저 실행 (원자적)
    val newCount = redisTemplate.opsForValue().increment(countKey) ?: 1L

    // 2. quota 체크 및 롤백
    if (newCount > quota) {
        redisTemplate.opsForValue().decrement(countKey)  // 롤백
        throw CouponException.CouponExhausted()
    }

    // 3. Set에 userId 추가 (성공한 경우만)
    redisTemplate.opsForSet().add(issuedSetKey, userId.toString())
}
```

#### 5.3 핵심 개선 사항
1. **INCR을 가장 먼저 실행**: 원자적 연산을 통해 정확한 카운팅
2. **quota 초과 시 즉시 롤백**: DECR로 카운트 복원 후 예외 발생
3. **Set 추가는 성공 시에만**: quota 체크를 통과한 경우만 userId 기록

---

## 테스트 결과

### STEP 13: ProductRankingIntegrationTest
- ✅ 총 13개 테스트 (100% 통과)
- ✅ 동시성 테스트 통과 (10 스레드 * 10회 = 100회 정확성)
- ✅ Redis Key TTL 검증 통과

### STEP 14: CouponIssuanceIntegrationTest
- ✅ 총 10개 테스트 (100% 통과)
- ✅ 동시성 테스트 통과 (100명 → 50장 정확 발급)
- ✅ 중복 발급 방지 검증 통과

### 통합 테스트 환경
- TestContainers를 활용한 독립적 테스트 환경 구축
- MySQL 8.0 + Redis 7-alpine 컨테이너 자동 실행
- `@DirtiesContext`를 통한 테스트 격리

---

## 한계점 및 개선 방향

### 1. Redis와 DB 데이터 정합성

**현재 문제점**
- Redis는 캐시 역할, DB가 Source of Truth
- DB 저장 실패 시 Redis에만 기록되는 불일치 가능성

**개선 방향**
- 스케줄러를 통한 주기적 동기화
- Redis → DB 동기화 배치 작업 추가
- Eventual Consistency 전략 적용

### 2. Redis 장애 시 대응

**현재 문제점**
- Redis 다운 시 전체 기능 중단
- Single Point of Failure

**개선 방향**
- Redis Sentinel 구성으로 HA 확보
- Redis Cluster로 수평 확장
- Circuit Breaker 패턴 적용 (Resilience4j)

### 3. 대용량 트래픽 대응

**현재 문제점**
- 모든 요청이 분산락을 획득해야 함
- 락 대기 시간으로 인한 성능 저하 가능

**개선 방향**
- Lua Script를 활용한 원자적 다중 명령 실행
- Redis Pipeline을 통한 배치 처리
- 쿠폰 ID별 세분화된 락 키 전략

### 4. 랭킹 데이터 DB 영구 저장

**현재 문제점**
- TTL 만료 시 랭킹 데이터 소실
- 과거 랭킹 조회 불가

**개선 방향**
- 스케줄러를 통한 일간 랭킹 DB 저장
- 히스토리 테이블 구성 (daily_rankings, weekly_rankings)
- 랭킹 변동 추이 분석 가능

---

## 회고

### 잘한 점
1. **Redis 자료구조 선택의 적절성**
   - Sorted Set을 활용한 랭킹 시스템 구현으로 O(log N) 성능 달성
   - Set을 활용한 중복 발급 방지로 빠른 체크 구현
   - INCR의 원자성을 활용한 정확한 수량 관리

2. **통합 테스트를 통한 검증**
   - TestContainers를 활용한 실제 환경과 유사한 테스트 환경 구축
   - 동시성 테스트를 통해 race condition 완벽 제거 검증
   - 23개의 통합 테스트 시나리오로 핵심 기능 검증

3. **TTL 기반 메모리 관리**
   - 랭킹 데이터 자동 만료로 메모리 누수 방지
   - 키 네이밍 전략을 통한 체계적 데이터 관리

### 어려운 점
1. **동시성 제어의 복잡성**
   - 초기에 `checkIssuanceEligibility()`와 `recordIssuance()` 분리로 race condition 발생
   - INCR의 원자성을 이해하고 활용하는 과정에서 시행착오
   - quota 초과 시 롤백 로직 설계의 어려움

2. **Redis와 DB 이중 관리의 복잡도**
   - Redis 사전 체크 + 분산락 + DB 저장의 3단계 흐름 설계
   - 각 단계에서의 예외 처리 및 롤백 전략 수립
   - 데이터 정합성 보장을 위한 트랜잭션 범위 설정

3. **테스트 환경 구성의 어려움**
   - TestContainers 설정 및 통합 테스트 격리
   - JPA auto-generated ID와 하드코딩된 ID 충돌 문제 해결
   - 동시성 테스트의 불확실성 (타이밍 이슈)

### 다음 시도
1. **Lua Script 도입**
   - 다중 Redis 명령을 원자적으로 실행하여 성능 개선
   - 쿠폰 발급 로직을 Lua Script로 통합하여 왕복 횟수 감소

2. **Redis Sentinel 구성**
   - HA(High Availability) 확보를 위한 마스터-슬레이브 구조
   - 장애 발생 시 자동 페일오버

3. **스케줄러 기반 동기화**
   - Redis → DB 주기적 동기화 배치 작업
   - 랭킹 데이터 영구 저장 및 히스토리 관리

4. **모니터링 및 알림 시스템**
   - Redis 메모리 사용률 모니터링
   - 쿠폰 발급 속도 및 성공률 메트릭 수집
   - 장애 발생 시 알림 시스템 구축

---

## 참고 자료
- [Redis Sorted Set 공식 문서](https://redis.io/docs/data-types/sorted-sets/)
- [Redis Transactions 공식 문서](https://redis.io/docs/manual/transactions/)
- [Spring Data Redis 공식 문서](https://docs.spring.io/spring-data/redis/reference/)
- [TestContainers 공식 문서](https://testcontainers.com/)
