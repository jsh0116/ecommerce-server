# 의류 이커머스 Mock 서버 가이드

## 📌 개요

이 Mock 서버는 설계된 의류 이커머스 플랫폼의 API를 시뮬레이션하며, **상품 관리**, **재고 관리**, **주문 생성**, **쿠폰 시스템**을 포함합니다.

---

## 🚀 시작하기

### 1. 서버 실행

```bash
./gradlew bootRun
```

또는 JAR 파일로 실행:

```bash
./gradlew bootJar
java -jar build/libs/hhplus-week2-0.0.1-SNAPSHOT.jar
```

### 2. Swagger UI 접근

서버 실행 후 아래 주소로 접근:

```
http://localhost:8080/swagger-ui.html
```

---

## 📚 API 엔드포인트

### 상품 API (`/api/v1/products`)

#### 1️⃣ 상품 목록 조회
```http
GET /api/v1/products?page=1&limit=20&category=pants&brand=LEVI'S
```

**파라미터:**
- `page`: 페이지 번호 (기본값: 1)
- `limit`: 페이지당 항목 수 (기본값: 20)
- `category`: 카테고리 필터 (선택)
- `brand`: 브랜드 필터 (선택)
- `minPrice`: 최소 가격 필터 (선택)
- `maxPrice`: 최대 가격 필터 (선택)

**응답 예시:**
```json
{
  "data": [
    {
      "id": "prod_001",
      "name": "슬림핏 청바지",
      "brand": "LEVI'S",
      "category": "pants",
      "basePrice": 89000,
      "salePrice": 79000,
      "discountRate": 11,
      "images": ["https://cdn.fashionstore.com/prod_001_1.jpg"],
      "variantCount": 2,
      "rating": 4.5,
      "reviewCount": 128,
      "tags": ["베스트셀러"]
    }
  ],
  "pagination": {
    "page": 1,
    "limit": 20,
    "total": 2,
    "totalPages": 1
  }
}
```

#### 2️⃣ 상품 상세 조회
```http
GET /api/v1/products/{productId}
```

**예시:**
```bash
curl http://localhost:8080/api/v1/products/prod_001
```

#### 3️⃣ 상품 변량(SKU) 조회
```http
GET /api/v1/products/{productId}/variants?color=black&size=32
```

#### 4️⃣ 상품 검색
```http
GET /api/v1/products/search?q=청바지&page=1&limit=20
```

---

### 재고 API (`/api/v1/inventory`)

#### 1️⃣ 재고 조회
```http
GET /api/v1/inventory/skus/{sku}
```

**예시:**
```bash
curl http://localhost:8080/api/v1/inventory/skus/LEVI-501-BLK-32-REG
```

**응답:**
```json
{
  "sku": "LEVI-501-BLK-32-REG",
  "available": 15,
  "reserved": 0,
  "physical": 20,
  "safetyStock": 5,
  "status": "IN_STOCK",
  "lastUpdated": "2025-10-31T12:00:00Z"
}
```

#### 2️⃣ 재고 예약 (주문 생성 시)
```http
POST /api/v1/inventory/reserve
Content-Type: application/json

{
  "sku": "LEVI-501-BLK-32-REG",
  "quantity": 2
}
```

**응답:**
```json
{
  "reservationId": "550e8400-e29b-41d4-a716-446655440000",
  "sku": "LEVI-501-BLK-32-REG",
  "quantity": 2,
  "expiresAt": "2025-10-31T12:15:00Z",
  "success": true
}
```

**실패 시:**
```json
{
  "code": "INSUFFICIENT_STOCK",
  "message": "재고가 부족합니다.",
  "details": {
    "sku": "LEVI-501-BLK-32-REG",
    "requestedQuantity": 2
  }
}
```

#### 3️⃣ 재고 차감 (결제 승인 후)
```http
POST /api/v1/inventory/deduct
Content-Type: application/json

{
  "sku": "LEVI-501-BLK-32-REG",
  "quantity": 2
}
```

#### 4️⃣ 예약 취소 (결제 실패 시)
```http
POST /api/v1/inventory/cancel-reservation
Content-Type: application/json

{
  "sku": "LEVI-501-BLK-32-REG",
  "quantity": 2
}
```

---

### 쿠폰 API (`/api/v1/coupons`)

#### 1️⃣ 쿠폰 검증
```http
POST /api/v1/coupons/validate
Content-Type: application/json

{
  "couponCode": "SUMMER2024",
  "orderAmount": 158000
}
```

**응답 (유효한 쿠폰):**
```json
{
  "valid": true,
  "coupon": {
    "id": "coupon_001",
    "code": "SUMMER2024",
    "name": "여름 세일 10,000원 할인",
    "type": "FIXED_AMOUNT",
    "discount": 10000,
    "minOrderAmount": 50000,
    "maxDiscountAmount": null,
    "validFrom": "2025-10-31T00:00:00Z",
    "validUntil": "2025-12-31T23:59:59Z"
  },
  "discount": 10000,
  "message": "10000원 할인이 적용됩니다"
}
```

**응답 (유효하지 않은 쿠폰):**
```json
{
  "valid": false,
  "coupon": null,
  "discount": 0,
  "message": "최소 주문 금액(50000원)을 충족하지 못했습니다.",
  "details": {
    "minOrderAmount": 50000,
    "currentAmount": 30000
  }
}
```

#### 2️⃣ 쿠폰 목록 조회
```http
GET /api/v1/coupons?page=1&limit=20
```

**가용 쿠폰:**
- `SUMMER2024`: 10,000원 정액 할인 (최소 50,000원)
- `WELCOME20`: 20% 할인 (최대 50,000원)
- `FREESHIP`: 배송비 무료 (최소 10,000원)

---

### 주문 API (`/api/v1/orders`)

#### 1️⃣ 주문 생성
```http
POST /api/v1/orders
Content-Type: application/json
Authorization: Bearer {JWT_TOKEN}

{
  "items": [
    {
      "variantId": "var_001",
      "quantity": 2
    }
  ],
  "shippingAddress": {
    "name": "홍길동",
    "phone": "010-1234-5678",
    "address": "서울특별시 강남구 테헤란로 123",
    "addressDetail": "456호",
    "zipCode": "06000"
  },
  "shippingMethod": "standard",
  "couponCode": "SUMMER2024",
  "pointsToUse": 5000,
  "agreeToTerms": true,
  "requestMessage": "부재 시 경비실에 맡겨주세요"
}
```

**응답:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "orderNumber": "2025103100001",
  "status": "PENDING_PAYMENT",
  "reservationExpiry": "2025-10-31T12:15:00Z",
  "items": [
    {
      "id": "item_1",
      "productName": "슬림핏 청바지",
      "variant": {
        "id": "var_001",
        "sku": "LEVI-501-BLK-32-REG",
        "color": "black",
        "size": "32"
      },
      "quantity": 2,
      "price": 79000,
      "subtotal": 158000
    }
  ],
  "payment": {
    "amount": 146000,
    "breakdown": {
      "subtotal": 158000,
      "discount": 10000,
      "pointsUsed": 5000,
      "shipping": 3000,
      "total": 146000
    }
  },
  "createdAt": "2025-10-31T12:00:00Z"
}
```

**재고 부족 시:**
```json
{
  "code": "INSUFFICIENT_STOCK",
  "message": "재고가 부족합니다.",
  "details": {
    "sku": "LEVI-501-BLK-32-REG",
    "requestedQuantity": 2
  }
}
```

#### 2️⃣ 주문 조회
```http
GET /api/v1/orders/{orderId}
Authorization: Bearer {JWT_TOKEN}
```

#### 3️⃣ 주문 취소
```http
POST /api/v1/orders/{orderId}/cancel
Content-Type: application/json
Authorization: Bearer {JWT_TOKEN}

{
  "reason": "CHANGE_OF_MIND",
  "detailReason": "사이즈가 맞지 않을 것 같아요"
}
```

**응답:**
```json
{
  "message": "주문이 취소되었습니다.",
  "refundAmount": 146000,
  "estimatedRefundDate": "2025-11-10"
}
```

**실패 응답 (배송 시작 후):**
```json
{
  "code": "CANNOT_CANCEL",
  "message": "이미 배송이 시작되어 취소할 수 없습니다. 반품을 신청해주세요."
}
```

---

## 📦 Mock 데이터

### 상품 데이터

| 상품 ID | 상품명 | 브랜드 | 카테고리 | 기본가 | 판매가 |
|---------|--------|--------|----------|--------|--------|
| prod_001 | 슬림핏 청바지 | LEVI'S | pants | 89,000원 | 79,000원 |
| prod_002 | 에어 맥스 270 | NIKE | shoes | 189,000원 | 149,000원 |

### 상품 변량 데이터

| 변량 ID | SKU | 색상 | 사이즈 | 재고 | 상태 |
|---------|-----|------|--------|------|------|
| var_001 | LEVI-501-BLK-32-REG | black | 32 | 15 | IN_STOCK |
| var_002 | LEVI-501-BLK-34-REG | black | 34 | 3 | LOW_STOCK |
| var_003 | NIKE-270-WHT-270-REG | white | 270 | 8 | IN_STOCK |

### 쿠폰 데이터

| 쿠폰 코드 | 이름 | 유형 | 할인액/율 | 최소 주문액 |
|----------|------|------|----------|-----------|
| SUMMER2024 | 여름 세일 | 정액 | 10,000원 | 50,000원 |
| WELCOME20 | 신규 회원 | 정률 | 20% | - |
| FREESHIP | 배송비 무료 | 배송비 | 3,000원 | 10,000원 |

---

## 🔍 주요 비즈니스 로직

### 재고 관리
- **재고 상태**: `IN_STOCK` (5개 초과), `LOW_STOCK` (1-5개), `OUT_OF_STOCK` (0개)
- **재고 차감**: 결제 승인 시점에 실행
- **재고 예약**: 주문 생성 시 15분 TTL(Time To Live)로 예약
- **안전 재고**: 최소 보유 수량 (차감 불가능)

### 주문 상태 흐름
```
PENDING_PAYMENT (결제 대기)
    ↓
PAID (결제 완료)
    ↓
PREPARING (상품 준비중)
    ↓
SHIPPED (배송중)
    ↓
DELIVERED (배송 완료)

또는

PENDING_PAYMENT → CANCELLED (취소)
PAID → CANCELLED (취소)
PREPARING → CANCELLED (취소)
```

### 쿠폰 검증
- **유효성 확인**: 쿠폰 코드, 활성 여부, 유효 기간, 최소 주문액 검증
- **할인 계산**:
    - 정액: 쿠폰 할인액 적용
    - 정률: (주문금액 × 할인율) % 최대할인액
    - 배송비: 배송비 상황에 따라 적용

### 최종 금액 계산
```
최종 금액 = 상품 금액 - 쿠폰 할인 - 사용 포인트 + 배송비
```

### 배송비 계산
- **30,000원 이상**: 무료
- **일반 배송**: 3,000원
- **빠른 배송**: 4,000원
- **새벽 배송**: 5,000원

---

## 🧪 테스트 예제 (cURL)

### 1. 상품 목록 조회
```bash
curl -X GET "http://localhost:8080/api/v1/products?page=1&limit=20&category=pants" \
  -H "accept: application/json"
```

### 2. 상품 상세 조회
```bash
curl -X GET "http://localhost:8080/api/v1/products/prod_001" \
  -H "accept: application/json"
```

### 3. 재고 조회
```bash
curl -X GET "http://localhost:8080/api/v1/inventory/skus/LEVI-501-BLK-32-REG" \
  -H "accept: application/json"
```

### 4. 쿠폰 검증
```bash
curl -X POST "http://localhost:8080/api/v1/coupons/validate" \
  -H "Content-Type: application/json" \
  -d '{
    "couponCode": "SUMMER2024",
    "orderAmount": 158000
  }'
```

### 5. 주문 생성
```bash
curl -X POST "http://localhost:8080/api/v1/orders" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -d '{
    "items": [
      {
        "variantId": "var_001",
        "quantity": 2
      }
    ],
    "shippingAddress": {
      "name": "홍길동",
      "phone": "010-1234-5678",
      "address": "서울특별시 강남구 테헤란로 123",
      "addressDetail": "456호",
      "zipCode": "06000"
    },
    "shippingMethod": "standard",
    "couponCode": "SUMMER2024",
    "pointsToUse": 5000,
    "agreeToTerms": true
  }'
```

### 6. 주문 조회
```bash
curl -X GET "http://localhost:8080/api/v1/orders/550e8400-e29b-41d4-a716-446655440000" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -H "accept: application/json"
```

### 7. 주문 취소
```bash
curl -X POST "http://localhost:8080/api/v1/orders/550e8400-e29b-41d4-a716-446655440000/cancel" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -d '{
    "reason": "CHANGE_OF_MIND",
    "detailReason": "사이즈가 맞지 않을 것 같아요"
  }'
```

---

## 📊 응답 상태 코드

| 상태 코드 | 의미 | 예시 |
|----------|------|------|
| 200 | OK | 조회/수정 성공 |
| 201 | Created | 주문 생성 성공 |
| 400 | Bad Request | 잘못된 요청 또는 유효하지 않은 쿠폰 |
| 404 | Not Found | 상품/주문을 찾을 수 없음 |
| 409 | Conflict | 재고 부족 또는 주문 취소 불가 |

---

## 📝 체크리스트

- [x] 상품 API 구현 (목록, 상세, 변량 조회, 검색)
- [x] 재고 API 구현 (조회, 예약, 차감, 취소)
- [x] 쿠폰 API 구현 (검증, 목록 조회)
- [x] 주문 API 구현 (생성, 조회, 취소)
- [x] Mock 데이터 초기화
- [x] Swagger UI 통합
- [x] 비즈니스 로직 구현
    - [x] 재고 차감 및 예약
    - [x] 쿠폰 검증
    - [x] 최종 금액 계산
    - [x] 배송비 계산

---

## 🔗 관련 문서

- [API 명세서](./docs/api-specification.md)
- [데이터 모델 설계](./docs/data-models.md)
- [요구사항 명세서](./docs/requirements.md)

---

**Mock 서버 버전:** 1.0.0
**마지막 업데이트:** 2025-10-31



# 📚 Swagger API 명세 배포 - 완벽 가이드

## 🎯 개요

이 프로젝트는 **Springdoc-OpenAPI**를 통해 자동으로 Swagger UI를 제공합니다.
API 코드가 변경되면 문서도 **자동으로 동기화**됩니다.

---

## 🚀 시작하기 (3가지 방법)

### 방법 1: 로컬 실행 (가장 간단)

```bash
./gradlew bootRun
```

**접근:**
```
http://localhost:8080/swagger-ui.html
```

---

### 방법 2: Docker 실행

```bash
# 이미지 빌드
./gradlew clean build -x test
docker build -t hhplus-ecommerce:latest .

# 컨테이너 실행
docker run -p 8080:8080 hhplus-ecommerce:latest
```

**또는 Docker Compose 사용:**

```bash
docker-compose up --build
```

**접근:**
```
http://localhost:8080/swagger-ui.html
```

---

### 방법 3: 클라우드 배포 (Google Cloud Run)

```bash
# 1. 빌드
./gradlew clean build -x test

# 2. 이미지 빌드 및 푸시
gcloud auth configure-docker
docker build -t gcr.io/YOUR_PROJECT_ID/hhplus:latest .
docker push gcr.io/YOUR_PROJECT_ID/hhplus:latest

# 3. Cloud Run 배포
gcloud run deploy hhplus-ecommerce \
  --image gcr.io/YOUR_PROJECT_ID/hhplus:latest \
  --platform managed \
  --region asia-northeast1 \
  --port 8080 \
  --memory 512Mi
```

**접근:**
```
https://hhplus-ecommerce-{hash}.run.app/swagger-ui.html
```

---

## 📖 문서 구조

```

프로젝트 루트/
├── Dockerfile ....................... Docker 이미지 정의
├── docker-compose.yml ............... Docker Compose 설정
├── .dockerignore .................... Docker 제외 파일
├── docs/
│   ├── swagger/
│     ├── README_SWAGGER.md .................. 이 파일 (개요)
│     ├── QUICK_START.md .................... 5분 시작 가이드
│     ├── SWAGGER_DEPLOYMENT.md ............ 전체 배포 옵션 설명
│     └── DEPLOYMENT_CHECKLIST.md .......... 배포 확인 목록
│   ├── api-specification.md ......... API 명세서 (P0 이슈 포함)
│   ├── swagger.yaml ................. OpenAPI 정의 (자동 생성)
│   ├── requirements.md .............. 요구사항 명세서
│   ├── user-stories.md .............. 사용자 스토리
│   ├── data-models.md ............... 데이터 모델
│   ├── flow-chart.md ................ 플로우 차트
│   └── self-check-report.md ......... 자체 검증 보고서
│
└── src/main/
    ├── kotlin/com/hhplus/ecommerce/config/
    │   └── OpenApiConfig.kt ......... Swagger 설정
    │
    └── resources/
        ├── application.yml .......... Spring Boot 설정
        └── swagger.yaml ............ OpenAPI 정의 복사본
```

---

## 🛠️ 설정 파일 설명

### 1. build.gradle.kts

```kotlin
// Swagger UI & OpenAPI
implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.0.2")
```

**역할:** Springdoc-OpenAPI 라이브러리 제공

---

### 2. application.yml

```yaml
springdoc:
  swagger-ui:
    enabled: true
    path: /swagger-ui.html
  api-docs:
    path: /v3/api-docs
```

**역할:** Swagger UI 경로 설정

---

### 3. OpenApiConfig.kt

```kotlin
@Configuration
class OpenApiConfig {
    @Bean
    fun openAPI(): OpenAPI { ... }
}
```

**역할:** OpenAPI 메타데이터 정의 (제목, 설명, 연락처 등)

---

## 📊 API 접근 방법

### Swagger UI
```
GET http://localhost:8080/swagger-ui.html
```

### OpenAPI JSON
```
GET http://localhost:8080/v3/api-docs
```

### OpenAPI YAML
```
GET http://localhost:8080/v3/api-docs.yaml
```

---

## 🔍 Swagger UI 사용법

### 1. API 탐색
- 좌측: 카테고리별 API 그룹화
- 우측: 상세 정보 표시

### 2. API 테스트 (Try It Out)

```
1. 엔드포인트 클릭
2. "Try it out" 버튼 클릭
3. 파라미터 입력
4. "Execute" 버튼 클릭
5. 응답 확인
```

### 3. JWT 인증

```
1. 우측 상단의 "Authorize" 버튼 클릭
2. "Bearer {token}" 입력
3. "Authorize" 클릭
4. 이후 모든 요청에 자동 적용
```

---

## 🔄 API 문서 동기화

### 자동 동기화 (권장)

```
API 코드 수정 → Spring Boot 재시작 → Swagger UI 자동 업데이트
```

**적용되는 항목:**
- ✅ @RestController, @GetMapping 등 어노테이션
- ✅ @RequestParam, @PathVariable 파라미터
- ✅ @RequestBody, @ResponseBody 스키마
- ✅ 메서드 주석 (Javadoc/KDoc)

**수동 갱신:**
```bash
./gradlew bootRun  # 재시작으로 수동 갱신
```

---

## 🐳 Docker 배포 상세

### Docker 이미지 크기
```bash
docker images | grep hhplus
# REPOSITORY     TAG     SIZE
# hhplus...      latest  ~300MB
```

### 컨테이너 리소스 사용

```bash
docker stats hhplus-ecommerce-api
# CONTAINER CPU   MEM
# hhplus...   0.1% 200MB
```

### 로그 확인

```bash
docker logs hhplus-ecommerce-api
docker logs -f hhplus-ecommerce-api  # 실시간 로그
```

---

## ☁️ 클라우드 배포 비교

| 플랫폼 | 비용 | 설정 | 추천 상황 |
|--------|------|------|----------|
| **Google Cloud Run** | 무료~$20/월 | ⭐⭐ | 일반 프로젝트 |
| **AWS ECS** | $50/월+ | ⭐⭐⭐ | 엔터프라이즈 |
| **Heroku** | 무료~$50/월 | ⭐ | 개발/테스트 |
| **로컬 Docker** | 무료 | ⭐ | 개발 환경 |

---

## 🔒 보안 주의사항

### 1. 민감한 정보 숨김

```kotlin
@Hidden  // Swagger UI에서 숨김
fun internalApi() { }
```

### 2. HTTPS 강제

```yaml
server:
  ssl:
    enabled: true
    key-store: classpath:keystore.p12
```

### 3. 인증 필수

```kotlin
// Swagger UI 접근 시 인증 필요
@Configuration
class SwaggerSecurityConfig {
    // ... 설정
}
```

---

## 📈 모니터링 및 로깅

### CloudWatch (AWS)

```bash
# 로그 확인
aws logs tail /ecs/hhplus-ecommerce --follow
```

### Cloud Logging (GCP)

```bash
# 로그 확인
gcloud logging read "resource.type=cloud_run_revision" --limit 50
```

### 로컬 로그

```bash
./gradlew bootRun 2>&1 | grep -i swagger
```

---

## 🚨 문제 해결

### Q1: Swagger UI 404 에러

**원인:** 라이브러리 누락 또는 설정 오류

```bash
# 1. 의존성 확인
./gradlew dependencies | grep springdoc

# 2. 재빌드
./gradlew clean build

# 3. 재시작
./gradlew bootRun
```

### Q2: OpenAPI JSON이 비어 있음

**원인:** API 엔드포인트 없음

```bash
# 1. 컨트롤러 확인
find src -name "*Controller.kt"

# 2. @RestController 어노테이션 확인
grep -r "@RestController" src/
```

### Q3: Docker 빌드 실패

**원인:** JAR 파일 없음

```bash
# 1. 빌드
./gradlew clean build -x test

# 2. JAR 확인
ls -la build/libs/

# 3. Dockerfile에서 JAR 경로 확인
cat Dockerfile | grep -i "copy"
```

---

## 📚 추가 참고 자료

- **Springdoc 공식 문서:** https://springdoc.org/
- **OpenAPI 명세:** https://spec.openapis.org/
- **Swagger UI:** https://swagger.io/tools/swagger-ui/
- **Google Cloud Run:** https://cloud.google.com/run/docs

---

## 🎯 다음 단계

### 즉시 실행
1. `./gradlew bootRun`
2. http://localhost:8080/swagger-ui.html 접근
3. API 테스트

### 배포
1. `QUICK_START.md` 참고
2. Docker 또는 클라우드 선택
3. 배포 실행

### CI/CD
1. `.github/workflows/deploy.yml` 생성
2. GitHub Actions 구성
3. 자동 배포 설정

### 모니터링
1. CloudWatch/Cloud Logging 설정
2. 알람 구성
3. 대시보드 생성

---

## 📞 지원

**문제 발생 시:**
1. DEPLOYMENT_CHECKLIST.md 확인
2. 로그 확인 (`./gradlew bootRun`)
3. Docker 로그 확인 (`docker logs`)

**배포 가이드:**
- `QUICK_START.md` (빠른 시작)
- `SWAGGER_DEPLOYMENT.md` (상세 가이드)

---

## ✅ 체크리스트

- [ ] Spring Boot 실행 성공
- [ ] Swagger UI 접근 가능
- [ ] API 엔드포인트 표시됨
- [ ] "Try it out" 작동
- [ ] JWT 인증 작동
- [ ] Docker 이미지 빌드 성공
- [ ] 클라우드 배포 완료

---

**마지막 수정:** 2024-03-15
**버전:** 1.0.0
**작성자:** Backend Team

🎉 **준비 완료! Swagger API 명세 배포를 시작하세요!**
