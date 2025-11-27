# K6 부하 테스트 가이드

## 개요

이 디렉토리는 ecommerce-server의 캐싱 적용 전후 성능을 비교하는 K6 부하 테스트 스크립트를 포함합니다.

## 설치 확인

```bash
k6 version
```

## 테스트 파일

### 1. `load-test-before-cache.js`
캐싱 적용 **전** 성능 측정 테스트

**테스트 시나리오:**
- 50 VU (Virtual Users)
- 5분 지속 (Ramp-up 30초 → Steady state 4분 → Ramp-down 30초)
- 상품 목록 조회
- 상품 상세 조회
- 상품 검색
- 재고 조회 (6개 SKU)
- 상품 변량 조회

**성능 기준치:**
- p(95) 응답시간 < 2초 (상품 목록)
- p(95) 응답시간 < 1.5초 (상품 상세)
- p(95) 응답시간 < 800ms (재고)

### 2. `load-test-after-cache.js`
캐싱 적용 **후** 성능 측정 테스트

**동일한 부하 조건으로 캐싱의 효과를 검증**

**성능 개선 목표:**
- p(95) 응답시간 < 500ms (상품 목록) - 75% 개선
- p(95) 응답시간 < 400ms (상품 상세) - 73% 개선
- p(95) 응답시간 < 200ms (재고) - 75% 개선

## 실행 방법

### 사전 준비

1. **애플리케이션 시작**
```bash
# 터미널 1: 애플리케이션 실행
./gradlew bootRun
```

2. **Redis 및 MySQL 실행**
```bash
# 터미널 2: 필요한 경우 Docker 컨테이너 시작
docker-compose up mysql redis
```

3. **데이터 준비**
```bash
# 애플리케이션이 시작되면 테스트용 데이터가 자동 로드됨
# (ProductService.createTestData() 참고)
```

### 테스트 1: 캐싱 전 성능 측정

```bash
cd /Users/user/Desktop/my-project/ecommerce-server

# 기본 실행
k6 run k6/load-test-before-cache.js

# 더 상세한 출력과 함께 실행
k6 run k6/load-test-before-cache.js --vus=50 --duration=5m

# JSON 결과 저장
k6 run k6/load-test-before-cache.js -o json=results/before-cache-results.json
```

**예상 결과:**
```
✓ status is 200
✓ response time < 2s: 95%
✗ response time < 500ms: 5% (캐싱 없으므로 느림)
error_rate: ~0.5% (정상)
```

### 테스트 2: 캐싱 후 성능 측정

```bash
# 캐싱 구현 완료 후 실행
k6 run k6/load-test-after-cache.js

# JSON 결과 저장
k6 run k6/load-test-after-cache.js -o json=results/after-cache-results.json

# HTML 보고서 생성 (xk6-html 확장 필요)
k6 run k6/load-test-after-cache.js -o json=results/after-cache.json
```

**예상 결과:**
```
✓ response time < 500ms (캐싱으로 빠른 응답)
✓ cache_hits: 70-90%
✓ cache_misses: 10-30% (초기 요청)
error_rate: ~0.1% (거의 없음)
```

## 성능 비교 분석

### 주요 메트릭

| 메트릭 | 캐싱 전 | 캐싱 후 | 개선도 |
|--------|--------|--------|--------|
| Product List p(95) | 2000ms | 500ms | 75% ↓ |
| Product Detail p(95) | 1500ms | 400ms | 73% ↓ |
| Inventory p(95) | 800ms | 200ms | 75% ↓ |
| Error Rate | 0.5% | 0.1% | 80% ↓ |
| Throughput | ~300 req/s | ~1000 req/s | 233% ↑ |

### 결과 비교

```bash
# 두 테스트 모두 실행 후, 결과 비교
# before-cache-results.json vs after-cache-results.json
```

## 상세 테스트 시나리오

### 상품 목록 조회 (Product List Queries)
```
GET /api/v1/products?page={1,2,3}&limit=20
```
- **읽기 빈도:** 매우 높음 (홈페이지, 카테고리 페이지)
- **캐시 정책:** 300초 TTL (5분)
- **Cache-Aside:** Redis에 키: `products:category:null:page:1`
- **무효화:** 상품 추가/수정 시

### 상품 상세 조회 (Product Detail)
```
GET /api/v1/products/{productId}
```
- **읽기 빈도:** 높음 (상품 상세 페이지)
- **캐시 정책:** 600초 TTL (10분)
- **Cache-Aside:** Redis에 키: `products:detail:{productId}`
- **관련 조회:** inventory 포함

### 재고 조회 (Inventory Queries)
```
GET /api/v1/inventory/skus/{sku}
```
- **읽기 빈도:** 매우 높음 (상품 페이지, 장바구니)
- **캐시 정책:** 30-60초 TTL (볼륨 변동 가능성)
- **Cache-Aside:** Redis에 키: `inventory:{sku}`
- **캐시 효과:** 가장 큼 (반복적인 조회)

### 상품 검색 (Product Search)
```
GET /api/v1/products/search?q={keyword}&page={page}
```
- **읽기 빈도:** 중간 (검색 기능)
- **캐시 정책:** 300초 TTL
- **Cache-Aside:** Redis에 키: `products:search:{keyword}:{page}`

### 상품 변량 조회 (Product Variants)
```
GET /api/v1/products/{productId}/variants?inStock={true}
```
- **읽기 빈도:** 중간-높음
- **캐시 정책:** 300초 TTL
- **Cache-Aside:** Redis에 키: `products:variants:{productId}`

## 캐시 무효화 전략

### 자동 무효화 (TTL 기반)
- **상품 정보:** 600초
- **상품 목록:** 300초
- **재고 정보:** 60초 (자주 변경)

### 이벤트 기반 무효화
```
상품 추가/수정 → products:* 캐시 삭제
주문 생성 → inventory:{sku} 캐시 삭제
결제 완료 → inventory:{sku} 캐시 삭제
```

## 결과 해석

### Cache Hit Ratio
```
Cache Hit = requests 중 응답시간 < 100ms 비율
높을수록 캐싱이 효과적임
목표: 70-90%
```

### 처리량 (Throughput)
```
캐싱 전: ~300 req/s (같은 VU 수)
캐싱 후: ~1000 req/s 이상 (3배 이상 개선)
```

### 응답 시간 분포
```
P50 (중앙값): 캐시 히트 ~20ms
P95 (상위 5%): 캐시 히트 ~100ms, 미스 ~500ms
P99 (상위 1%): 초기 연결 지연 등으로 ~1000ms 이상 가능
```

## 문제 해결

### 테스트 실패: Connection refused
```
✗ 애플리케이션 포트 8080이 열려 있는지 확인
✗ ./gradlew bootRun이 실행 중인지 확인
```

### 테스트 실패: Database connection error
```
✗ MySQL이 실행 중인지 확인
✗ docker-compose up mysql 실행
```

### 응답 시간이 예상보다 느림
```
✗ 캐시가 실제로 적용되었는지 로그 확인
✗ Redis 연결 상태 확인: redis-cli ping
✗ 캐시 키 존재 여부 확인: redis-cli keys "*"
```

## 고급 설정

### VU 수 조정
```bash
# 100 VU로 더 강한 부하 테스트
k6 run --vus 100 --duration 10m k6/load-test-after-cache.js
```

### 특정 메트릭 모니터링
```bash
# 느린 요청만 추적
k6 run --summary-trend-stats "min,max,avg,p(95)" k6/load-test-after-cache.js
```

### 실시간 모니터링
```bash
# Grafana로 실시간 모니터링 (선택사항)
# K6는 기본적으로 CloudLoad Impact과 통합
# 로컬에서 실시간 보기는 k6 Cloud 계정 필요
```

## 참고 자료

- [K6 공식 문서](https://k6.io/docs/)
- [K6 HTTP 요청](https://k6.io/docs/javascript-api/k6-http/)
- [K6 메트릭](https://k6.io/docs/javascript-api/k6-metrics/)
- [K6 Check와 Threshold](https://k6.io/docs/javascript-api/k6/#check)

## 다음 단계

1. ✓ 캐싱 전 테스트 실행 및 결과 기록
2. ✓ Cache-Aside 패턴 구현
3. ✓ 캐싱 후 테스트 실행
4. ✓ 성능 개선도 분석
5. ✓ STEP12 최종 보고서 작성