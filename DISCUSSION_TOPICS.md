# STEP09-10 팀 논의 주제

## 🎯 주제 1: 단일 서버 vs 멀티 서버 동시성 제어 방식의 trade-off

### 📌 개요
STEP09-10에서 구현한 **3가지 동시성 제어 방식**의 선택 기준과 trade-off를 논의합니다.

```
Inventory (재고)         → 비관적 락 (SELECT FOR UPDATE)
Payment (결제)           → 멱등성 키 (Idempotency Key)
Coupon (쿠폰)            → Redis 분산 락 (Redisson)
```

---

## 📊 주제 1-1: 왜 이 3가지를 다르게 적용했는가?

### 각 방식의 특징

#### **1. 비관적 락 (SELECT FOR UPDATE) - Inventory**
```kotlin
// InventoryService.kt
@Query("SELECT i FROM Inventory i WHERE i.sku = :sku FOR UPDATE")
fun findBySku(sku: String): Inventory?
```

**선택 이유:**
- 재고는 단일 DB에서 관리되는 **강한 일관성 필요**
- 한 번에 차감되는 수량이 **즉시 반영**되어야 함
- 멀티 서버여도 DB가 중앙집중식이므로 OK

**장점:**
- DB 레벨에서 원자성 보장
- Redis 같은 외부 의존성 없음
- 구현이 간단

**단점:**
- 높은 동시성에서 **스루풋 감소**
- 3초 타임아웃으로 인한 실패 가능성

---

#### **2. 멱등성 키 (Idempotency Key) - Payment**
```kotlin
// PaymentService.kt
fun processPayment(
    orderId: Long,
    amount: Long,
    idempotencyKey: String  // 클라이언트가 생성
): PaymentResult
```

**선택 이유:**
- 외부 결제 시스템과의 통신은 **재시도 불가피**
- 네트워크 장애로 중복 호출 가능
- **멱등성(idempotency)** 보장이 핵심

**장점:**
- 분산 시스템에 최적화 (stateless)
- 성능 영향 최소화 (DB 조회만)
- 멀티 서버 자동 지원

**단점:**
- 클라이언트 측에서 idempotencyKey 생성 필요
- 결제 완료 후 충돌 검사 로직 필요

---

#### **3. Redis 분산 락 (Redisson) - Coupon**
```kotlin
// CouponUseCase.kt
val lockAcquired = couponLockService.tryLock(
    couponId,
    waitTime = 3L,
    holdTime = 10L,
    unit = TimeUnit.SECONDS
)
```

**선택 이유:**
- 선착순 쿠폰은 **확률적으로 동시 경합**
- 멀티 서버 환경에서 **쿠폰별 동시성 제어** 필요
- 재고처럼 즉시 차감될 필요 없음 (심사/발급 단계 존재)

**장점:**
- 멀티 서버 환경에서 안전
- 쿠폰별 락으로 **다른 쿠폰에 영향 없음**
- 락 자동 해제로 deadlock 방지

**단점:**
- Redis 의존성 추가
- 약간의 오버헤드 (네트워크 왕복)
- Redis 장애 시 영향

---

### 📋 의사결정 프레임워크

| 기준 | 비관적 락 | 멱등성 키 | 분산 락 |
|------|---------|--------|-------|
| **단일 서버** | ✅ 최고 | ✅ 좋음 | ⚠️ 과도 |
| **멀티 서버** | ✅ DB 중앙 | ✅ 최고 | ✅ 최고 |
| **강한 일관성** | ✅✅ | ⚠️ 최종 | ✅ |
| **성능** | ⚠️ 중간 | ✅✅ | ✅ |
| **구현 복잡도** | ✅ 낮음 | ⚠️ 중간 | ⚠️ 중간 |
| **외부 의존성** | ❌ 없음 | ❌ 없음 | ⚠️ Redis |

---

## 💡 주제 1-2: 실제 프로덕션 시나리오

### 시나리오 1: QPS 10배 증가 (1,000 → 10,000)

**Inventory (비관적 락)**
```
현재: DB 락 타임아웃 3초
문제:
  - 10,000 req/s는 처리 불가 (DB 커넥션 풀 고갈)
  - 스루풋: 수백~천 req/s만 가능

개선 방안:
  - 재고 분산 (샤딩)
  - 캐시 추가 (먼저 캐시에서 check)
  - 큐잉 시스템 도입
```

**Coupon (분산 락)**
```
현재: Redisson 1초 waiting → 대부분 성공
문제:
  - Redis가 병목 (단일 인스턴스)

개선 방안:
  - Redis Cluster로 수평 확장
  - 쿠폰 ID별 Redis 노드 샤딩
```

**Payment (멱등성 키)**
```
현재: 가장 robust (상태 변화 없음)
문제: 거의 없음 (이미 분산 설계)

개선:
  - 멱등성 키 저장소를 별도 캐시로 분리
```

---

### 시나리오 2: Redis 장애 발생

**Coupon 서비스 영향:**
```
Redis 연결 불가 → tryLock() 실패 → CouponException.CouponExhausted()

현재 코드:
fun tryLock(...): Boolean {
    val lock = redissonClient.getLock(lockKey)
    return lock.tryLock(waitTime, holdTime, unit)  // Redis 네트워크 타임아웃
}

개선 안:
1. Circuit Breaker 패턴으로 빠른 실패
2. Fallback: DB 기반 분산 락으로 전환
3. 모니터링: Redis 상태 지속 관찰
```

**Inventory 서비스는?**
```
✅ 영향 없음 (DB 기반)
```

---

## 🧠 주제 1-3: 토론 질문

1. **선택의 정당성**
   - "왜 Inventory는 DB락인데 Coupon은 Redis락으로 했나?"
   - 그 선택이 지금도 맞다고 생각하는가?
   - 다시 한다면?

2. **성능 예측**
   - 지금 구현이 초당 요청 몇 개까지 감당할 수 있을까?
   - 각 방식별로 bottleneck은 어디일까?

3. **실제 운영**
   - Redis 없이 Coupon을 구현할 수 있을까?
   - 있다면 어떤 트레이드오프가 생길까?

4. **팀의 경험**
   - 다른 팀원들은 이 3가지를 어떻게 구현했나?
   - 다른 선택지를 고민해본 적 있나?

---

---

## 🎯 주제 2: Mock vs Real 테스트 환경 분리의 실제 효과

### 📌 개요
**3단계 테스트 전략**의 효과와 trade-off를 논의합니다.

```
단위 테스트          →  Mock Redis (10초)
통합 테스트 (로컬)   →  Docker Redis (30초)
CI/CD (GitHub Actions) →  실제 Redis (자동)
```

---

## 📊 주제 2-1: 3단계 전략을 왜 도입했는가?

### 각 레이어의 목적

#### **1단계: Mock Redis (단위 테스트)**
```kotlin
// TestRedissonConfig.kt
@TestConfiguration
class TestRedissonConfig {
    @Bean
    @Primary
    fun redissonClient(): RedissonClient {
        return mockk(relaxed = true)  // 모든 호출 허용
    }
}
```

**목적:**
- **빠른 피드백** (개발 시 반복 테스트)
- Redis 없이 로컬 개발 환경 구성

**특징:**
- 테스트 시간: ~10초
- Redis 의존성: ❌ 없음
- 테스트 커버리지: 기본 로직만

**한계:**
```
문제: tryLock() 호출이 실제로 동작하는지 알 수 없음
      mockk가 모든 호출을 통과시키므로

예시:
// 이 코드가 깨져도 Mock은 통과
val lockAcquired = couponLockService.tryLock(...)
if (!lockAcquired) {
    throw CouponException()
}
// lockAcquired가 항상 true라고 가정 (실제론 false 가능)
```

---

#### **2단계: Docker Redis (로컬 통합 테스트)**
```bash
# run-integration-tests.sh
docker run -d --name hhplus-redis -p 6379:6379 redis:7-alpine
docker run -d --name hhplus-mysql -e MYSQL_ROOT_PASSWORD=root mysql:8.0

sleep 15
./gradlew clean testIntegration
```

**목적:**
- **실제 Redis 동작 검증**
- 로컬에서 CI 환경 재현

**특징:**
- 테스트 시간: ~30초
- Redis 의존성: ✅ 있음
- 테스트 커버리지: 실제 동시성 상황

**장점:**
```
실제 이슈 발견 예시:

Mock 테스트 (통과):
- lockAcquired = true로 항상 통과

Docker 테스트 (실패):
- 동시 요청 100개 시도
- 1개만 lock 획득 성공, 나머지 false 반환
- if (!lockAcquired) 로직 정상 작동 검증
```

---

#### **3단계: GitHub Actions (자동화된 CI/CD)**
```yaml
# .github/workflows/ci.yml
services:
  redis:
    image: redis:7-alpine
    options: >-
      --health-cmd="redis-cli ping"
      --health-interval=10s
      --health-timeout=5s
      --health-retries=3
    ports:
      - 6379:6379
```

**목적:**
- **모든 PR에서 자동 검증**
- 실제 CI 환경에서의 신뢰도

**특징:**
- 테스트 시간: ~30초 (자동)
- Redis 의존성: ✅ 있음 (service 자동 제공)
- 테스트 빈도: 매 PR마다

**효과:**
```
개발자가 놓친 버그도 자동 감지:
- 로컬에서 skip한 테스트
- 특정 환경에서만 발생하는 race condition
```

---

## 🔍 주제 2-2: Mock vs Real에서 발견한 버그

### 실제 케이스 분석

#### **케이스 1: 락 획득 실패 처리**
```kotlin
// Mock 테스트 (문제 없음)
val lockAcquired = couponLockService.tryLock(...)
// mockk는 항상 true 반환

// Docker 테스트 (실패 발견)
val lockAcquired = couponLockService.tryLock(...)
if (!lockAcquired) {
    throw CouponException.CouponExhausted()  // 이 로직 검증됨
}
```

**배운 점:**
- Mock만으로는 **exception flow 검증 불가**
- 실제 경합 상황에서 오류 처리가 정상인지 확인 필수

---

#### **케이스 2: 동시성 상황에서의 상태 불일치**
```kotlin
// 100개 동시 요청 시나리오

Mock 테스트:
✅ 모든 요청 통과 (Mock이 모두 성공으로 반환)
⚠️ 실제론 50개만 성공, 50개 실패

Docker 테스트:
✅ 정확히 100개 → 100개 판매 (올바른 결과)
❌ Mock 테스트와 결과 불일치 발견!

의문점:
1. Mock이 뭘 잘못했나?
2. 실제 타이밍이 뭘까?
3. 스트레스 테스트는 어떤 수준까지?
```

---

#### **케이스 3: Redis 타임아웃 처리**
```kotlin
// Mock 테스트
val lockAcquired = lock.tryLock(3, 10, TimeUnit.SECONDS)
// 즉시 true 반환 (네트워크 지연 없음)

// Docker 테스트
val lockAcquired = lock.tryLock(3, 10, TimeUnit.SECONDS)
// 3초 대기 → timeout → false 반환
// 또는 Redis 응답 약간 늦으면 약간의 지연 발생

의문점:
- 3초 timeout이 실제로는 몇 초?
- Redis 느려지면 감지할 수 있나?
```

---

## 📈 주제 2-3: 테스트 시간 vs 신뢰도 트레이드오프

### 테스트 수행 시간 분석

```
프로젝트 규모별 전체 테스트 시간:

단위 테스트만 (Mock):
  ├─ 테스트 개수: 23개
  ├─ 예상 시간: 3-5초
  └─ 개발자 피드백: ⭐⭐⭐⭐⭐ (빠름)

단위 + 통합 (Docker):
  ├─ 테스트 개수: 30개
  ├─ 예상 시간: 25-35초 (Docker 시작 포함)
  └─ 개발자 피드백: ⭐⭐⭐ (중간)

시사점:
- 통합 테스트는 **7배 느림**
- 근데 신뢰도는 **훨씬 높음**
- 개발 중에는 단위 테스트로 빠르게 반복
- 커밋 전에만 통합 테스트 실행
```

### 개발자 경험 최적화 전략

```bash
# 개발 중: Mock만 (빠름)
./gradlew test

# 테스트 전 커밋: 통합까지 (철저함)
./gradlew test testIntegration

# CI/CD: 자동 (항상 100% 검증)
(GitHub Actions에서 자동 실행)
```

---

## 🧠 주제 2-4: 놓친 버그 케이스

### 가능한 시나리오

#### **Scenario 1: Mock으로는 통과, Docker에선 실패**
```
예상되는 케이스:
- 동시성 관련 race condition
- 타임아웃 처리 로직
- 락 자동 해제 타이밍

실제 경험:
- "Mock에선 왜 통과했는데..." 라는 순간이 있었나?
```

#### **Scenario 2: Docker 로컬에선 통과, GitHub Actions에선 실패**
```
예상되는 케이스:
- 네트워크 지연 (GitHub Actions이 더 느림)
- 리소스 제약 (컨테이너 환경)
- 타이밍 issue

해당 사항:
- 스트레스 테스트 20초 제한을 왜 설정했나?
- 처음엔 5초였는데 왜 변경했나?
```

---

## 💡 주제 2-5: 테스트 환경 구성 과정

### TestRedissonConfig 작성 시 고민

```kotlin
@TestConfiguration
class TestRedissonConfig {
    @Bean
    @Primary
    fun redissonClient(): RedissonClient {
        return mockk(relaxed = true)
    }
}
```

**의사결정 포인트:**
1. **왜 @Primary?**
   - 같은 타입의 Bean이 여러 개일 때 우선순위 설정
   - 테스트에선 Mock을, 프로덕션에선 실제 사용

2. **왜 relaxed = true?**
   - mockk(relaxed = false) = 호출 시 실패
   - mockk(relaxed = true) = 모든 호출 통과 (기본값 반환)
   - 트레이드오프: 편하지만 타입 검증 부족

3. **@TestConfiguration vs @Configuration?**
   - @TestConfiguration = 테스트에서만 로드
   - 프로덕션 코드는 건드리지 않음

---

### run-integration-tests.sh 작성 시 고민

```bash
#!/bin/bash
set -e  # 한 줄이라도 실패하면 stop

echo "🚀 통합 테스트 실행 시작"

# 1. 컨테이너 시작
docker run -d --name hhplus-redis -p 6379:6379 redis:7-alpine 2>/dev/null || echo "Redis already running"

# 2. 왜 sleep 15?
sleep 15  # Redis, MySQL이 완전히 시작될 때까지 대기

# 3. 테스트 실행
./gradlew clean testIntegration

# 4. 정리 (사용자 선택)
read -p "Docker 컨테이너를 중지하시겠습니까? (y/n) " -n 1 -r
```

**의사결정 포인트:**
1. **sleep 15는 충분한가?**
   - 너무 짧으면: DB 시작 전에 테스트 시작 → 실패
   - 너무 길면: 개발자가 매번 15초 기다림 → 짜증

2. **에러 처리**
   - "Redis already running"은 왜?
   - 스크립트 재실행 시 컨테이너 충돌 방지

3. **정리 단계가 선택사항?**
   - 개발자가 수동으로 선택하게 함
   - 또 테스트 필요하면 정리 안 해도 됨

---

## 🧠 주제 2-6: 토론 질문

1. **Mock의 한계**
   - Mock만으로는 뭘 검증할 수 없나?
   - 다른 팀원들은 Mock 테스트로 충분하다고 생각하나?

2. **테스트 빈도와 시간**
   - 지금처럼 3단계 분리가 최적인가?
   - 2단계나 1단계로 줄일 수 있을까?

3. **CI/CD 신뢰도**
   - GitHub Actions에서의 자동 테스트가 실제로 버그를 찾은 적 있나?
   - "로컬에선 통과했는데 CI에서 실패"한 적 있나?

4. **환경 간 차이**
   - 로컬 Docker와 GitHub Actions Redis의 성능 차이를 느낀 적 있나?
   - 테스트 결과가 일관되게 나오나?

5. **다른 선택지**
   - Mock과 Real 사이에 다른 대안이 있나?
   - (예: Test Containers, embedded Redis, 등)

---

## 📋 토론 진행 가이드

### 1단계: 개인 의견 정리 (5분)
```
- 각자 "이 주제 중에 가장 인상 깊었던 부분"을 1가지씩 메모
- 혹은 "반대 의견이 있는 부분" 정리
```

### 2단계: 주제별 토론 (15분 × 2)
```
주제 1 (동시성 제어):
  - 왜 이렇게 선택했나? (5분)
  - 성능 예측 (5분)
  - 개선 방향 (5분)

주제 2 (테스트 전략):
  - Mock vs Real 효과 (5분)
  - 실제 발견 버그 (5분)
  - 테스트 환경 개선 (5분)
```

### 3단계: 팀 결론 도출 (10분)
```
- 우리 팀의 베스트 프랙티스는?
- 다음 프로젝트에 적용할 원칙은?
- 아직 해결 못 한 의문은?
```

---

## 🎯 팀 토론의 가치

이 두 주제를 통해:
1. **단순 구현 → 설계 철학** 수준으로 상승
2. **trade-off 의식** 생성 (실제 선택 기준 공유)
3. **다음 프로젝트의 가이드라인** 도출
4. **팀 간 학습 격차 해소** (경험 공유)

---

## 📌 추가 자료

### 참고 코드
- `src/main/kotlin/io/hhplus/ecommerce/application/services/impl/RedissonCouponLockService.kt`
- `src/test/kotlin/io/hhplus/ecommerce/config/TestRedissonConfig.kt`
- `.github/workflows/ci.yml`

### 테스트 결과
```
API 예외 처리:        3/3 PASS ✅
DB 동시성 제어:      9/9 PASS ✅
주문 통합:           6/6 PASS ✅
DTO 직렬화:          5/5 PASS ✅
동시성 통합:         7/7 PASS ✅
────────────────────────────────
총 테스트:          30/30 PASS ✅
```

### 성능 지표
- Mock 테스트: ~10초
- Docker 테스트: ~30초
- 스트레스 테스트: 1000개 동시 요청 20초 이내

---

**이 자료를 토대로 팀과 깊이 있는 논의를 진행해보세요! 🚀**
