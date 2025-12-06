# STEP11 & STEP12 구현 요약

**작성일:** 2025-11-29
**브랜치:** feature/homework_step12

---

## 📋 과제 체크리스트

### STEP11: 분산 락 적용 ✅

- [x] Redis 기반 분산락 직접 구현 (`RedissonDistributedLockService`)
- [x] 결제 멱등성에 분산락 적용 (`PaymentService.processPayment`)
- [x] **트랜잭션과 락 순서 보장** ← **중요 개선사항**
- [x] Test Container로 Redis/MySQL 통합 테스트 구성
- [x] 동시성 테스트 작성 및 검증

### STEP12: Redis 캐싱 적용 ✅

- [x] Redis 캐시 서비스 구현 (`RedisCacheService`)
- [x] Cache-Aside 패턴 적용
  - [x] **InventoryService.getInventory** (재고 조회)
  - [x] **ProductUseCase.getProducts** (상품 목록 조회) ← **신규 추가**
  - [x] **ProductUseCase.getTopProducts** (인기 상품 조회) ← **신규 추가**
- [x] 적절한 TTL 설정 및 캐시 키 전략 수립
- [x] 캐시 무효화 전략 적용 (쓰기 작업 후)
- [x] 성능 개선 보고서 작성 (`docs/step12/STEP12_CACHING_IMPLEMENTATION.md`)

---

## 🔧 핵심 개선 사항

### 1. 트랜잭션/락 순서 문제 해결 (STEP11)

**위치:** `PaymentService.kt:50`

#### ❌ 기존 코드 (잘못된 구현)
```kotlin
@Transactional  // ← 트랜잭션이 먼저 시작
fun processPayment(...): PaymentJpaEntity {
    val lockAcquired = distributedLockService.tryLock(...)  // ← 트랜잭션 내부에서 락 획득
    // ...
}
```

**문제점:**
- 트랜잭션 시작 → 락 획득 → 처리 → 락 해제 → 트랜잭션 커밋
- 락 해제 후 트랜잭션이 커밋되지 않아 다른 스레드가 이전 데이터를 조회

#### ✅ 수정된 코드 (올바른 구현)
```kotlin
// @Transactional 제거 - 트랜잭션 외부에서 락 획득
fun processPayment(...): PaymentJpaEntity {
    val lockAcquired = distributedLockService.tryLock(...)  // 1. 락 획득
    try {
        val existingPayment = queryExistingPayment(...)      // 2. 별도 트랜잭션
        val savedPayment = createPaymentInNewTransaction(...) // 3. 별도 트랜잭션
        return savedPayment
    } finally {
        distributedLockService.unlock(lockKey)               // 4. 트랜잭션 커밋 후 락 해제
    }
}
```

**올바른 순서:**
1. 분산 락 획득 (트랜잭션 외부)
2. 트랜잭션 시작 → 데이터 처리 → 트랜잭션 커밋
3. 분산 락 해제

**효과:**
- 데이터 정합성 보장
- 다른 스레드가 항상 최신 커밋된 데이터 조회 가능

---

### 2. Redis 캐싱 추가 구현 (STEP12)

#### 재고 조회 캐싱 (기존)
- **위치:** `InventoryService.kt:160`
- **메서드:** `getInventory(sku: String)`
- **TTL:** 60초
- **캐시 키:** `inventory:{sku}`

#### 상품 목록 조회 캐싱 (신규)
- **위치:** `ProductUseCase.kt:44`
- **메서드:** `getProducts(category: String?, sort: String)`
- **TTL:** 60초
- **캐시 키:** `products:{category}:{sort}`
- **특징:** 카테고리별, 정렬 방식별로 개별 캐시

#### 인기 상품 조회 캐싱 (신규)
- **위치:** `ProductUseCase.kt:122`
- **메서드:** `getTopProducts(limit: Int)`
- **TTL:** 300초 (5분)
- **캐시 키:** `products:top:limit:{limit}`
- **특징:** 인기도 점수 계산 결과 캐싱으로 CPU 부하 감소

---

## 📊 성능 개선 효과

### 예상 개선 수치

| 메트릭 | 캐싱 전 | 캐싱 후 | 개선도 |
|--------|--------|--------|--------|
| 상품 목록 조회 응답시간 | 150ms | 20ms | **87% ↓** |
| 인기 상품 조회 응답시간 | 200ms | 30ms | **85% ↓** |
| 재고 조회 응답시간 | 80ms | 15ms | **81% ↓** |
| DB 쿼리 수 | 1000/min | 200/min | **80% ↓** |

### 캐시 히트율 목표
- **재고 조회:** 70-80% (빈번한 조회)
- **상품 목록:** 60-70% (카테고리별 분산)
- **인기 상품:** 85-95% (높은 재사용성)

---

## 🧪 테스트 환경

### TestContainer 구성
```yaml
# application-test.yml에서 자동 구성
- MySQL 8.0 (TestContainer)
- Redis 7-alpine (TestContainer)
```

### GitHub Actions CI/CD
- **워크플로우:** `.github/workflows/ci.yml:72`
- **통합 테스트:** TestContainer 자동 프로비저닝
- **환경 변수 제거:** 하드코딩된 datasource URL 제거 (TestContainer가 자동 처리)

---

## 🚀 배포 가이드

### 로컬 실행
```bash
# 1. Redis 및 MySQL 시작
docker-compose up mysql redis

# 2. 애플리케이션 빌드 및 실행
./gradlew clean build
./gradlew bootRun

# 3. 캐시 상태 확인
redis-cli
> KEYS *
> GET products:all:newest
> TTL products:top:limit:5
```

### 테스트 실행
```bash
# 단위 테스트
./gradlew test

# 통합 테스트 (TestContainer 사용)
./gradlew testIntegration

# 전체 테스트
./gradlew clean build
```

---

## 📁 주요 변경 파일

### STEP11: 분산 락
- ✅ `PaymentService.kt` - 트랜잭션/락 순서 수정
- ✅ `RedissonDistributedLockService.kt` - 분산락 구현
- ✅ `.github/workflows/ci.yml` - CI 환경 개선

### STEP12: 캐싱
- ✅ `ProductUseCase.kt` - 상품 조회 캐싱 추가
- ✅ `InventoryService.kt` - 재고 조회 캐싱 (기존)
- ✅ `RedisCacheService.kt` - Redis 캐시 서비스
- ✅ `docs/step12/STEP12_CACHING_IMPLEMENTATION.md` - 성능 보고서

---

## 🎯 과제 평가 기준 달성도

### STEP11 체크리스트
- [x] 적절한 곳에 분산락이 사용되었는가? → **PaymentService 멱등성 처리**
- [x] 트랜잭션 순서와 락 순서가 보장되었는가? → **수정 완료 (main 포인트)**

### STEP12 체크리스트
- [x] 적절하게 Key 적용이 되었는가?
  - `inventory:{sku}`
  - `products:{category}:{sort}`
  - `products:top:limit:{limit}`
- [x] 캐시 필요한 부분 분석 → **3개 API 캐싱 적용**
- [x] Redis 기반의 캐시 적용 → **Cache-Aside 패턴**
- [x] 성능 개선 등을 포함한 보고서 제출 → **docs/step12/STEP12_CACHING_IMPLEMENTATION.md**

### 통합 테스트
- [x] Infrastructure 레이어를 포함하는 통합 테스트가 작성되었는가?
- [x] 핵심 기능에 대한 흐름이 테스트에서 검증되었는가?
- [x] 동시성을 검증할 수 있는 테스트코드로 작성되었는가?
- [x] Test Container가 적용되었는가?

---

## 🔍 리뷰어 확인 포인트

### 1. 트랜잭션/락 순서 개선 확인
- **파일:** `PaymentService.kt:50`
- **확인 사항:** `@Transactional` 어노테이션 제거, 락 획득 후 별도 트랜잭션 사용

### 2. 캐싱 적용 범위 확인
- **InventoryService:** `getInventory()` - TTL 60초
- **ProductUseCase:** `getProducts()` - TTL 60초
- **ProductUseCase:** `getTopProducts()` - TTL 300초

### 3. 캐시 키 전략
- 재고: `inventory:{sku}`
- 상품 목록: `products:{category}:{sort}`
- 인기 상품: `products:top:limit:{limit}`

---

## 💡 향후 개선 과제

1. **캐시 스탬피드 방지**
   - Null 캐싱 (부재 데이터도 캐싱)
   - 분산 락 기반 캐시 갱신

2. **모니터링 강화**
   - Cache Hit Ratio 메트릭
   - Redis 메모리 사용량 추적

3. **추가 캐싱 대상**
   - `ProductService.getProductById()` - 상품 단건 조회
   - `CouponService.getUserCoupons()` - 사용자 쿠폰 목록

---

**작성자:** Claude Code
**브랜치:** feature/homework_step12
**상태:** 구현 완료 ✅
