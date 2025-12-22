# 쿠폰 발급 성능 비교: Redis Lock vs Kafka

## 목차
1. [개요](#개요)
2. [테스트 환경](#테스트-환경)
3. [아키텍처 비교](#아키텍처-비교)
4. [성능 측정 지표](#성능-측정-지표)
5. [테스트 시나리오](#테스트-시나리오)
6. [성능 비교 결과](#성능-비교-결과)
7. [장단점 분석](#장단점-분석)
8. [권장사항](#권장사항)

---

## 개요

본 문서는 대규모 동시 접속 환경에서의 쿠폰 발급 시스템의 두 가지 구현 방식을 비교합니다:

- **Redis Lock 방식**: 분산 락을 사용한 동기식 처리
- **Kafka 방식**: 메시지 큐를 사용한 비동기식 처리

## 테스트 환경

### 하드웨어 및 소프트웨어
- **OS**: macOS / Linux
- **JVM**: OpenJDK 17
- **Spring Boot**: 3.2.0
- **Redis**: 7.0 (Redisson 사용)
- **Kafka**: 3.x (EmbeddedKafka)

### 테스트 설정
```kotlin
동시 사용자 수: 100명
쿠폰 재고: 50개
스레드 풀 크기: 20
Kafka 파티션: 3개
```

---

## 아키텍처 비교

### Redis Lock 방식 (동기)

```
Client Request
      ↓
Controller
      ↓
UseCase
      ↓
[Redis Lock 획득]
      ↓
Redis INCR (재고 차감)
      ↓
DB INSERT (UserCoupon)
      ↓
[Redis Lock 해제]
      ↓
Response (즉시 응답)
```

**특징:**
- 동기식 처리: 요청 → 처리 → 응답이 즉시 이루어짐
- 분산 락으로 동시성 제어
- 트랜잭션 일관성 보장

### Kafka 방식 (비동기)

```
Client Request
      ↓
Controller
      ↓
UseCase
      ↓
Kafka Producer (이벤트 발행)
      ↓
Response (requestId 반환) ← 즉시 응답

[별도 프로세스]
Kafka Consumer
      ↓
IdempotencyService (중복 방지)
      ↓
Redis INCR (재고 차감)
      ↓
DB INSERT (UserCoupon)
      ↓
이벤트 발행 (완료 알림)
```

**특징:**
- 비동기식 처리: 요청 접수와 처리가 분리됨
- 파티션 기반 순서 보장 (같은 userId는 같은 파티션)
- 최종 일관성 (Eventual Consistency)
- At-least-once 전달 보장

---

## 성능 측정 지표

### 1. Throughput (처리량)
- **정의**: 단위 시간당 처리 가능한 요청 수
- **단위**: requests/second
- **측정 방법**: `총 성공 요청 수 / 총 소요 시간`

### 2. Latency (응답 시간)
- **평균 응답 시간**: 모든 요청의 평균 처리 시간
- **P95 응답 시간**: 95% 요청이 이 시간 내에 완료
- **P99 응답 시간**: 99% 요청이 이 시간 내에 완료

### 3. Success Rate (성공률)
- **정의**: 전체 요청 대비 성공한 요청의 비율
- **단위**: %
- **측정 방법**: `(성공 요청 수 / 전체 요청 수) * 100`

### 4. Concurrency (동시성 처리 능력)
- **Lock Contention**: 락 대기로 인한 지연
- **Queue Depth**: Kafka의 경우 메시지 큐 깊이

---

## 테스트 시나리오

### 시나리오 1: 정상 부하 테스트
- **목적**: 일반적인 부하 상황에서의 성능 측정
- **조건**: 100명 동시 요청, 50개 쿠폰
- **기대 결과**: 50개 쿠폰이 정확히 발급됨

### 시나리오 2: 과부하 테스트
- **목적**: 재고보다 많은 요청 시 처리 능력 측정
- **조건**: 쿠폰보다 2배 많은 요청
- **기대 결과**: 초과 요청 적절히 거부

### 시나리오 3: 확장성 테스트
- **목적**: 부하 증가 시 성능 변화 측정
- **조건**: 동시 사용자 100 → 500 → 1000 증가
- **기대 결과**: Kafka는 선형 확장, Redis Lock은 성능 저하

---

## 성능 비교 결과

### 예상 결과 (100명 동시 요청, 50개 쿠폰)

| 지표 | Redis Lock | Kafka | 비고 |
|------|-----------|-------|------|
| **평균 응답 시간** | 500-1000ms | 10-50ms | Kafka는 요청 접수만 측정 |
| **P95 응답 시간** | 1500ms | 100ms | Lock 대기 시간 포함 |
| **P99 응답 시간** | 2500ms | 150ms | 극단적 지연 케이스 |
| **처리량** | 50-100 req/s | 500-1000 req/s | Kafka가 10배 이상 높음 |
| **성공률** | 50% | 100% | Kafka는 요청 접수 기준 |
| **Lock Contention** | 높음 | 없음 | Kafka는 Lock-free |

### 실제 측정 방법

테스트를 실행하려면:

```bash
# 성능 테스트 실행 (통합 테스트)
./gradlew testIntegration --tests "*CouponIssuancePerformanceComparisonTest*"

# 로그 확인
# 테스트 로그에서 상세한 성능 지표 확인 가능
```

---

## 장단점 분석

### Redis Lock 방식

#### 장점 ✅
1. **즉시 응답**: 동기 처리로 사용자가 결과를 즉시 확인 가능
2. **강한 일관성**: 트랜잭션 일관성 보장 (ACID)
3. **구현 단순성**: 이해하기 쉬운 코드 흐름
4. **디버깅 용이**: 요청-응답 추적이 간단

#### 단점 ❌
1. **낮은 처리량**: Lock contention으로 인한 처리량 제한
2. **높은 지연시간**: Lock 대기로 인한 응답 지연
3. **확장성 한계**: 수평 확장이 어려움 (Redis 성능에 의존)
4. **장애 전파**: Redis 장애 시 전체 시스템 영향

#### 적합한 사용 사례
- ✅ 즉시 응답이 필요한 경우 (실시간 재고 확인)
- ✅ 동시 요청 수가 많지 않은 경우 (< 100 req/s)
- ✅ 트랜잭션 일관성이 중요한 경우
- ✅ 간단한 아키텍처를 선호하는 경우

---

### Kafka 방식

#### 장점 ✅
1. **높은 처리량**: Lock-free로 초당 수천 건 처리 가능
2. **빠른 응답**: 요청 접수만 하고 즉시 응답 (10-50ms)
3. **수평 확장**: 파티션 증가로 처리량 선형 확장
4. **내결함성**: 메시지 영구 저장, 재처리 가능
5. **비동기 처리**: 시스템 부하 분산
6. **순서 보장**: 파티션 내에서 메시지 순서 보장

#### 단점 ❌
1. **최종 일관성**: 즉시 결과를 알 수 없음 (Eventual Consistency)
2. **복잡한 인프라**: Kafka, Zookeeper, Consumer 관리 필요
3. **디버깅 난이도**: 비동기 흐름 추적이 복잡
4. **운영 오버헤드**: 모니터링, 장애 대응 복잡

#### 적합한 사용 사례
- ✅ 대량의 동시 요청 처리 (> 1000 req/s)
- ✅ 선착순 이벤트 (쿠폰 발급, 티켓 예매)
- ✅ 비동기 처리 가능한 경우
- ✅ 수평 확장이 필요한 경우
- ✅ 높은 가용성이 요구되는 경우

---

## 권장사항

### 시스템 규모별 권장 아키텍처

#### 소규모 서비스 (MAU < 10만)
```
권장: Redis Lock 방식
이유:
  - 구현 및 운영 단순
  - 즉시 응답으로 UX 향상
  - 인프라 비용 절감
```

#### 중규모 서비스 (MAU 10만 ~ 100만)
```
권장: Redis Lock + Queue 하이브리드
이유:
  - Redis Lock으로 빠른 응답
  - Queue로 피크 부하 처리
  - Kafka 도입 전 단계로 적합
```

#### 대규모 서비스 (MAU > 100만)
```
권장: Kafka 방식
이유:
  - 높은 처리량으로 대량 트래픽 처리
  - 수평 확장으로 성장에 대응
  - 마이크로서비스 아키텍처에 적합
```

### 하이브리드 아키텍처 고려

```kotlin
// 빠른 응답 + 비동기 처리 조합
fun issueCoupon(couponId: Long, userId: Long): CouponIssueResponse {
    // 1. Redis에서 빠르게 재고 확인 (READ)
    val available = couponIssuanceService.checkAvailability(couponId)

    if (!available) {
        return CouponIssueResponse(status = "SOLD_OUT")
    }

    // 2. Kafka로 비동기 처리 요청
    val requestId = asyncCouponIssuanceService.requestIssuance(couponId, userId)

    // 3. 즉시 응답 (요청 접수 완료)
    return CouponIssueResponse(
        status = "PENDING",
        requestId = requestId,
        message = "발급 처리 중입니다. requestId로 상태를 확인하세요."
    )
}
```

---

## 성능 최적화 팁

### Redis Lock 방식 최적화

1. **Lock 타임아웃 최소화**
   ```kotlin
   lockService.executeWithLock(
       couponId = couponId,
       waitTime = 1000,  // 1초로 제한
       leaseTime = 3000  // 3초로 제한
   )
   ```

2. **쿠폰별 개별 Lock**
   ```kotlin
   // ❌ 전역 Lock (모든 쿠폰에 영향)
   lockService.lock("coupon-issuance")

   // ✅ 쿠폰별 Lock (격리)
   lockService.lock("coupon-issuance:$couponId")
   ```

3. **Redis Connection Pool 튜닝**
   ```yaml
   redisson:
     connection-pool-size: 64
     connection-minimum-idle-size: 32
   ```

### Kafka 방식 최적화

1. **파티션 수 증가**
   ```yaml
   # 파티션 수 = Consumer 병렬 처리 수
   partitions: 10  # 처리량에 맞게 조정
   ```

2. **Batch 처리**
   ```kotlin
   @KafkaListener(
       topics = ["coupon.issuance.requested"],
       concurrency = "5",  // Consumer 인스턴스 5개
       batch = "true"       // 배치 처리 활성화
   )
   ```

3. **Consumer 수 증가**
   ```yaml
   spring:
     kafka:
       listener:
         concurrency: 10  # 파티션 수와 동일하게
   ```

---

## 결론

- **즉시 응답이 중요**하다면: **Redis Lock**
- **높은 처리량이 중요**하다면: **Kafka**
- **둘 다 중요**하다면: **하이브리드**

대부분의 선착순 이벤트는 **Kafka 방식**을 권장하며,
초기 단계에서는 **Redis Lock**으로 시작하여 트래픽 증가 시 **Kafka로 전환**하는 것이 안전합니다.
