# Step 09: 동시성 문제 해결 및 성능 최적화 - 종합 보고서

## 📋 목차

1. [프로젝트 개요](#프로젝트-개요)
2. [동시성 문제 요약](#동시성-문제-요약)
3. [보고서 구성](#보고서-구성)
4. [구현 로드맵](#구현-로드맵)
5. [성공 기준](#성공-기준)
6. [추천 읽기 순서](#추천-읽기-순서)

---

## 프로젝트 개요

### hhplus-ecommerce

- **언어**: Kotlin 1.9.21
- **프레임워크**: Spring Boot 3.2.0
- **데이터베이스**: MySQL + Redis
- **아키텍처**: 클린 아키텍처 + DDD
- **동시 사용자**: ~10,000명/일

### 목표

**"재고 관리, 선착순 쿠폰 발급, 결제 프로세스에서 발생하는 동시성 문제를 식별하고, 데이터베이스를 활용한 적절한 해결 방법을 선정하여 구현한다."**

---

## 동시성 문제 요약

### 1️⃣ 재고 차감 경쟁 상황

```
문제: Race Condition으로 음수 재고 발생
초기 재고: 1개
동시 요청: 2명이 동시에 구매
결과: 재고 = -1 (오류!)

영향도: 월간 50만원 손실 + 브랜드 신뢰도 하락
해결책: 비관적 락 (SELECT FOR UPDATE) 또는 Redis
```

### 2️⃣ 선착순 쿠폰 초과 발급

```
문제: Race Condition으로 초과 발급
정책: 선착순 100명
동시 요청: 101-102명
결과: 101명 발급 (정책 위반!)

영향도: 월간 50만원 손실 + 정책 신뢰도 하락
해결책: Redis Set + 원자적 연산 또는 분산 락
```

### 3️⃣ 결제 중복

```
문제: 네트워크 타임아웃으로 중복 결제
이유: 확인(Check) + 차감(Act) 사이의 갭
결과: 중복 차감으로 사용자 손실

영향도: 월간 500만원 손실 + 심각한 신뢰도 하락
해결책: 멱등성 키 (Idempotency Key) + Redis 캐싱
```

### 4️⃣ 부분 실패 (Saga 패턴)

```
문제: 결제 중간에 외부 API 실패
프로세스: 잔액 차감 → 재고 차감 → 쿠폰 사용 → 외부 API ❌
결과: 데이터 불일치 (DB는 차감됨, 외부는 안 됨)

영향도: 데이터 무결성 손상 + 운영 복잡도 증가
해결책: Saga 패턴 + 보상 트랜잭션
```

---

## 보고서 구성

### 📄 01_concurrency_issue_identification.md (Step 1)

**"동시성 문제 식별"**

```
내용:
1. 문제 식별
   - 재고 차감 경쟁 상황
   - 선착순 쿠폰 발급 경쟁
   - 결제 동시성 이슈

2. 현재 코드 분석
   - InventoryService 분석
   - CouponUseCase 분석
   - OrderUseCase 분석

3. 영향도 평가
   - 비즈니스 손실 계산
   - 기술적 영향도
   - 위험 평가 매트릭스

목표: 문제를 명확히 이해하고 공감대 형성
읽기 시간: ~20분
```

### 📄 02_inventory_concurrency_control.md (Step 2)

**"재고 관리 동시성 제어"**

```
내용:
1. 비관적 락 (Pessimistic Lock)
   - 개념 및 원리
   - SELECT FOR UPDATE
   - 현재 구현 상태
   - 장단점 분석

2. 낙관적 락 (Optimistic Lock)
   - @Version 기반 동시성 제어
   - 재시도 로직
   - 충돌 처리

3. Redis 기반 재고 관리
   - Lua 스크립트 원자적 연산
   - 배치 동기화
   - TTL 관리

4. 전략 선택 기준
   - 성능 vs 안전성
   - 구현 복잡도
   - 확장성

목표: 재고 차감을 안전하게 구현하는 방법 학습
읽기 시간: ~30분
```

### 📄 03_coupon_issuance.md (Step 3)

**"선착순 쿠폰 발급"**

```
내용:
1. Redis Set 기반 선착순
   - 원자적 SADD 연산
   - 재검증 로직
   - 중복 방지

2. Queue 기반 순차 처리
   - FIFO 큐 사용
   - 비동기 Worker 처리
   - 선착순 보장

3. Redisson 분산 락
   - 멀티 서버 지원
   - 분산 락 관리
   - 데드락 처리

4. 전략 선택
   - Phase별 구현 계획
   - 동기식 vs 비동기식
   - 확장성 고려

목표: 정확히 N개만 발급하는 시스템 구축
읽기 시간: ~30분
```

### 📄 04_payment_idempotency.md (Step 4)

**"결제 프로세스 동시성 & 멱등성"**

```
내용:
1. 멱등성(Idempotency) 개념
   - 수학적 정의
   - 금융 표준
   - 멱등성 키

2. 멱등성 키 구현
   - 유니크 제약
   - Redis 캐싱
   - 더블 체크

3. Saga 패턴
   - 분산 트랜잭션 조율
   - 보상 트랜잭션
   - Orchestrator 구현

4. 구현 전략
   - Level 1: 멱등성 키
   - Level 2: Saga 패턴
   - Level 3: 분산 락

목표: 중복 결제를 방지하고 부분 실패 처리
읽기 시간: ~30분
```

---

## 구현 로드맵

### Phase 1: 긴급 (1주일)

#### Step 1: 비관적 락 강화 (재고)

```kotlin
// 목표: 현재 비관적 락 개선
// 파일: InventoryService.kt
// - 타임아웃 설정
// - 데드락 감지
// - 명확한 에러 메시지

// 구현 체크리스트:
☐ findBySkuForUpdate() 에러 처리
☐ 타임아웃 설정 (5초)
☐ OptimisticLockException 처리
☐ 데드락 감지 및 재시도
☐ 단위 테스트 (동시성 테스트)
☐ 통합 테스트
```

#### Step 2: 멱등성 키 구현 (결제)

```kotlin
// 목표: 중복 결제 방지
// 파일: PaymentService.kt, PaymentJpaEntity.kt
// - 유니크 제약 추가
// - Redis 캐싱
// - 더블 체크

// 구현 체크리스트:
☐ PaymentJpaEntity에 idempotencyKey 필드 추가
☐ 데이터베이스 유니크 인덱스 생성
☐ Redis 캐싱 로직 추가
☐ ProcessPaymentWithIdempotency 메서드 구현
☐ 컨트롤러에서 Idempotency-Key 헤더 처리
☐ 통합 테스트 (재시도 시나리오)
```

### Phase 2: 권장 (2주일)

#### Step 3: Redis Set 기반 쿠폰 발급

```kotlin
// 목표: 선착순 쿠폰 정확성 보장
// 파일: CouponService.kt, RedisConfiguration.kt
// - Redis Set 원자적 연산
// - 재검증 로직

// 구현 체크리스트:
☐ RedisConfiguration 설정
☐ RedisCouponService 구현
☐ SADD + SCARD 원자적 연산
☐ 재검증 로직 (초과 발급 방지)
☐ TTL 관리
☐ 부하 테스트 (동시 100명)
```

#### Step 4: Saga 패턴 도입

```kotlin
// 목표: 부분 실패 대응
// 파일: PaymentSaga.kt, SagaStep.kt
// - 보상 트랜잭션
// - 단계별 처리

// 구현 체크리스트:
☐ SagaStep 인터페이스 정의
☐ DeductBalanceStep 구현
☐ DeductInventoryStep 구현
☐ UseCouponStep 구현
☐ TransmitDataStep 구현
☐ PaymentSaga Orchestrator 구현
☐ 보상 트랜잭션 로직
☐ 통합 테스트
```

### Phase 3: 선택 (1주일)

#### Step 5: 성능 최적화 & 부하 테스트

```kotlin
// 목표: 성능 측정 및 최적화
// 도구: K6, JMeter
// - TPS 측정
// - 응답시간 분석
// - 병목 지점 파악

// 구현 체크리스트:
☐ K6 부하 테스트 스크립트 작성
☐ 재고 차감 TPS 측정
☐ 쿠폰 발급 TPS 측정
☐ 결제 처리 TPS 측정
☐ P95 응답시간 측정
☐ 에러율 측정
☐ 병목 지점 분석
☐ 최적화 적용 (커넥션 풀, 캐시 등)
```

---

## 성공 기준

### 재고 관리 (Step 2)

```
✅ 기능적 요구사항:
- [ ] 동시에 100개 요청 → 100개 판매 (초과 안 됨)
- [ ] 동시에 101개 요청 → 100개 판매 (1개 실패)
- [ ] 음수 재고 절대 발생 안 함
- [ ] 실패 시 재고 정확히 복구

✅ 비기능적 요구사항:
- [ ] 응답시간 < 100ms (P95)
- [ ] TPS > 1000 req/sec
- [ ] 동시 사용자 1000명 지원
```

### 쿠폰 발급 (Step 3)

```
✅ 기능적 요구사항:
- [ ] 정확히 100명만 발급
- [ ] 중복 발급 절대 안 됨
- [ ] 발급 순서 보장
- [ ] 101번째 사용자는 "소진" 메시지

✅ 비기능적 요구사항:
- [ ] 응답시간 < 50ms (P95)
- [ ] TPS > 5000 req/sec (쿠폰 인기 높음)
- [ ] 동시 사용자 10,000명 지원
```

### 결제 처리 (Step 4)

```
✅ 기능적 요구사항:
- [ ] 중복 결제 절대 안 됨 (멱등성)
- [ ] 실패 시 자동 롤백 (Saga)
- [ ] 모든 거래 기록 (감시)
- [ ] 감사 로그 남김 (Audit)

✅ 비기능적 요구사항:
- [ ] 응답시간 < 500ms (P95)
- [ ] TPS > 100 req/sec (결제는 느려도 안전 우선)
- [ ] 가용성 > 99.9%
```

---

## 추천 읽기 순서

### 1️⃣ 먼저 읽기 (필수)

```
1. 본 문서 (00_summary.md) - 전체 개요 파악
   읽기 시간: 10분

2. Step 1 보고서 (01_concurrency_issue_identification.md)
   이유: 문제를 이해하지 못하면 해결책을 이해 불가
   읽기 시간: 20분

3. Step 2 보고서 (02_inventory_concurrency_control.md)
   이유: 재고는 가장 우선순위 높은 문제
   읽기 시간: 30분
```

### 2️⃣ 그 다음 읽기 (권장)

```
4. Step 3 보고서 (03_coupon_issuance.md)
   이유: 쿠폰은 선착순 보장이 핵심
   읽기 시간: 30분

5. Step 4 보고서 (04_payment_idempotency.md)
   이유: 결제는 금전이 걸린 가장 중요한 기능
   읽기 시간: 30분
```

### 3️⃣ 마지막 (심화)

```
6. 각 보고서의 구현 예시 코드 분석
   이유: 실제 구현 시 참고
   읽기 시간: 1-2시간

7. 테스트 코드 작성
   이유: 동시성 테스트는 반드시 필요
   읽기 시간: 2-3시간
```

---

## 주요 개념 용어집

| 용어 | 정의 | 예시 |
|------|------|------|
| **Race Condition** | 두 개 이상의 스레드가 공유 자원에 동시 접근하여 발생하는 오류 | 재고 -1, 쿠폰 초과 |
| **Atomic Operation** | 중단 없이 한 번에 완료되는 연산 | Redis SADD, SELECT FOR UPDATE |
| **Lock** | 자원에 대한 배타적 접근 권한 | 비관적 락, 분산 락 |
| **Idempotency** | 중복 요청해도 같은 결과 | 멱등성 키 |
| **Saga** | 분산 트랜잭션 관리 패턴 | 보상 트랜잭션 |
| **TTL** | Time To Live, 데이터 만료 시간 | 쿠폰 발급 24시간 |

---

## 다음 단계

### 구현 후 검증

```
1. 단위 테스트 (Unit Test)
   - 각 함수의 정확성 검증

2. 동시성 테스트 (Concurrency Test)
   - 동시 100명, 1000명 시나리오
   - 음수 재고, 중복 발급 확인

3. 통합 테스트 (Integration Test)
   - 전체 플로우 검증
   - 롤백/보상 트랜잭션 테스트

4. 부하 테스트 (Load Test)
   - K6/JMeter로 TPS, 응답시간 측정
   - 병목 지점 파악 및 최적화
```

### 모니터링

```
1. 실시간 모니터링
   - 음수 재고 감시 알림
   - 중복 쿠폰 발급 감시
   - 결제 실패율 모니터링

2. 성능 메트릭
   - TPS (초당 거래량)
   - 응답시간 분포 (P50, P95, P99)
   - 에러율

3. 비즈니스 메트릭
   - 월간 판매 손실
   - 고객 만족도
   - 환불 건수
```

---

## 참고 자료

### 표준 및 지침

- RFC 7231: HTTP Semantics and Content (멱등성)
- Microservices Patterns: Saga Pattern
- MySQL Documentation: InnoDB Locking

### 오픈소스 라이브러리

```gradle
// Redis
implementation("org.springframework.boot:spring-boot-starter-data-redis")
implementation("io.github.peterramsing:reslang:3.12.0")  // Redisson

// 테스트
testImplementation("io.github.oshai:kotlin-logging-jvm:${version}")
testImplementation("io.kotest:kotest-runner-junit5:${version}")
testImplementation("io.kotest.extensions:kotest-extensions-spring:${version}")
testImplementation("com.ninja-squad:springmockk:${version}")
```

---

## 결론

### 핵심 메시지

1. **식별**: 문제를 정확히 파악하자 (Step 1)
2. **분석**: 각 문제의 원인과 영향도를 분석하자 (Step 2-4)
3. **해결**: 적절한 기술을 선택하여 구현하자
4. **검증**: 철저한 테스트로 안정성을 확보하자
5. **모니터링**: 운영 중 지속적으로 감시하자

### 기대 효과

```
재고 관리:      월간 50만원 손실 → 0 (음수 재고 방지)
쿠폰 관리:      월간 50만원 손실 → 0 (정확한 발급)
결제 처리:      월간 500만원 손실 → 0 (중복 청구 방지)
────────────────────────────────────────────────
총 기대 효과:   월간 600만원 비용 절감
고객 신뢰도:    5% 향상
────────────────────────────────────────────────
```

---

## 작성일

- **작성**: 2024년 11월
- **최종 수정**: 2024년 11월

## 문의사항

각 단계별 상세 내용은 해당 보고서를 참고하시기 바랍니다.

---

**Happy Coding! 🚀**