# 의류 이커머스 API 명세서

## 📋 목차
- [인증](#인증)
- [공통 규칙](#공통-규칙)
- [API 엔드포인트](#api-엔드포인트)
    - [상품 관리](#상품-관리)
    - [장바구니](#장바구니)
    - [주문/결제](#주문결제)
    - [재고 관리](#재고-관리)
    - [배송](#배송)
    - [반품/교환](#반품교환)
    - [리뷰](#리뷰)
    - [사용자](#사용자)

---

### 기본 정보
- **Base URL**: `https://api.fashionstore.com/v1`
- **Protocol**: HTTPS only
- **Content-Type**: `application/json`
- **Character Encoding**: UTF-8

### 환경별 URL
| 환경 | URL                                   |
|------|---------------------------------------|
| Production | `https://api.fashionstore.com/v1`     |
| Staging | `https://api-staging.fashionstore.com/v1` |
| Development | `https://api-dev.fashionstore.com/v1` |

---

## 인증

### JWT Bearer Token
모든 인증이 필요한 API는 헤더에 JWT 토큰을 포함해야 합니다.
```http
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

### 토큰 갱신
- **Access Token**: 1시간 유효
- **Refresh Token**: 30일 유효
```http
POST /v1/auth/refresh
Content-Type: application/json

{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

---

## 공통 규칙

### 1. HTTP 메서드

| 메서드 | 용도 | Idempotent | Safe |
|--------|------|------------|------|
| GET | 조회 | ✅ | ✅ |
| POST | 생성 | ❌ | ❌ |
| PUT | 전체 수정 | ✅ | ❌ |
| PATCH | 부분 수정 | ✅ | ❌ |
| DELETE | 삭제 | ✅ | ❌ |

### 2. HTTP 상태 코드

| 코드 | 의미 | 사용 예시 |
|------|------|-----------|
| 200 | OK | 조회/수정/삭제 성공 |
| 201 | Created | 생성 성공 |
| 204 | No Content | 삭제 성공 (응답 본문 없음) |
| 400 | Bad Request | 잘못된 요청 데이터 |
| 401 | Unauthorized | 인증 필요 |
| 403 | Forbidden | 권한 없음 |
| 404 | Not Found | 리소스 없음 |
| 409 | Conflict | 재고 부족, 중복 데이터 |
| 500 | Internal Server Error | 서버 오류 |

### 3. 에러 응답 형식
```json
{
  "code": "VALIDATION_ERROR",
  "message": "요청 데이터가 올바르지 않습니다.",
  "details": {
    "field": "email",
    "issue": "이미 사용중인 이메일입니다."
  }
}
```

### 4. 페이지네이션

**Query Parameters:**
```
GET /v1/products?page=1&limit=20
```

**응답 형식:**
```json
{
  "data": [...],
  "pagination": {
    "page": 1,
    "limit": 20,
    "total": 150,
    "totalPages": 8
  }
}
```

### 5. 정렬 (Sorting)
```
GET /v1/products?sort=-price,+createdAt
```

- `+`: 오름차순 (ASC)
- `-`: 내림차순 (DESC)

### 6. 필터링 (Filtering)
```
GET /v1/products?category=jacket&color=black&minPrice=50000&maxPrice=100000
```

### 7. Rate Limiting

| 사용자 유형 | 제한 |
|-------------|------|
| 인증된 사용자 | 1000 req/hour |
| 비인증 사용자 | 100 req/hour |

**응답 헤더:**
```http
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 999
X-RateLimit-Reset: 1679875200
```

---

## API 엔드포인트

## 상품 관리

### 1.1 상품 목록 조회
```http
GET /v1/products
```

**Query Parameters:**

| 파라미터 | 타입 | 필수 | 설명 | 예시 |
|---------|------|------|------|------|
| category | string | X | 카테고리 필터 | `tops`, `bottoms` |
| brand | string | X | 브랜드 필터 | `LEVI'S` |
| color | string[] | X | 색상 필터 (다중) | `black`, `navy` |
| size | string[] | X | 사이즈 필터 (다중) | `S`, `M`, `L` |
| minPrice | integer | X | 최소 가격 | `50000` |
| maxPrice | integer | X | 최대 가격 | `100000` |
| inStock | boolean | X | 재고 있는 상품만 | `true` |
| sort | string | X | 정렬 | `price`, `-price`, `popularity` |
| page | integer | X | 페이지 번호 | `1` (default) |
| limit | integer | X | 페이지당 항목 수 | `20` (default, max: 100) |

**응답 예시 (200 OK):**
```json
{
  "data": [
    {
      "id": "prod_123",
      "name": "슬림핏 청바지",
      "brand": "LEVI'S",
      "category": "pants",
      "basePrice": 89000,
      "salePrice": 79000,
      "discountRate": 11,
      "images": [
        "https://cdn.fashionstore.com/prod_123_1.jpg"
      ],
      "variantCount": 12,
      "rating": 4.5,
      "reviewCount": 128,
      "tags": ["베스트", "신상품"]
    }
  ],
  "pagination": {
    "page": 1,
    "limit": 20,
    "total": 150,
    "totalPages": 8
  }
}
```

---

### 1.2 상품 상세 조회
```http
GET /v1/products/{productId}
```

**Path Parameters:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| productId | string | O | 상품 ID |

**응답 예시 (200 OK):**
```json
{
  "id": "prod_123",
  "name": "슬림핏 청바지",
  "brand": "LEVI'S",
  "category": "pants",
  "description": "클래식한 핏의 청바지입니다.",
  "basePrice": 89000,
  "salePrice": 79000,
  "discountRate": 11,
  "material": "면 98%, 스판덱스 2%",
  "careInstructions": [
    "단독 세탁",
    "찬물 세탁",
    "건조기 사용 금지"
  ],
  "images": [
    "https://cdn.fashionstore.com/prod_123_1.jpg",
    "https://cdn.fashionstore.com/prod_123_2.jpg"
  ],
  "variants": [
    {
      "id": "var_456",
      "sku": "LEVI-501-BLK-32-REG",
      "color": "black",
      "colorHex": "#000000",
      "size": "32",
      "length": "regular",
      "price": 79000,
      "stock": 15,
      "stockStatus": "IN_STOCK"
    }
  ],
  "sizeGuide": {
    "brand": "LEVI'S",
    "category": "pants",
    "measurements": [
      {
        "size": "32",
        "waist": 82,
        "hip": 98,
        "length": 108
      }
    ]
  },
  "rating": 4.5,
  "reviewCount": 128
}
```

**에러 응답:**
- `404 NOT_FOUND`: 상품을 찾을 수 없음

---

### 1.3 상품 변량 목록 조회
```http
GET /v1/products/{productId}/variants
```

**Query Parameters:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| color | string | X | 색상 필터 |
| size | string | X | 사이즈 필터 |
| inStock | boolean | X | 재고 있는 변량만 |

**응답 예시 (200 OK):**
```json
[
  {
    "id": "var_456",
    "sku": "LEVI-501-BLK-32-REG",
    "color": "black",
    "colorHex": "#000000",
    "size": "32",
    "length": "regular",
    "price": 79000,
    "originalPrice": 89000,
    "stock": 15,
    "stockStatus": "IN_STOCK"
  },
  {
    "id": "var_457",
    "sku": "LEVI-501-BLK-34-REG",
    "color": "black",
    "colorHex": "#000000",
    "size": "34",
    "length": "regular",
    "price": 79000,
    "originalPrice": 89000,
    "stock": 3,
    "stockStatus": "LOW_STOCK"
  }
]
```

---

### 1.4 SKU 코드로 상품 조회
```http
GET /v1/skus/{sku}
```

**Path Parameters:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| sku | string | O | SKU 코드 |

**예시:**
```http
GET /v1/skus/LEVI-501-BLK-32-REG
```

---

### 1.5 상품 검색
```http
GET /v1/products/search?q=청바지
```

**Query Parameters:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| q | string | O | 검색 키워드 |
| page | integer | X | 페이지 번호 |
| limit | integer | X | 페이지당 항목 수 |

**응답 예시:**
```json
{
  "data": [...],
  "pagination": {...},
  "suggestions": [
    "슬림핏 청바지",
    "스키니 청바지",
    "와이드 청바지"
  ]
}
```

---

## 장바구니

### 2.1 장바구니 조회
```http
GET /v1/cart
Authorization: Bearer {token}
```

**응답 예시 (200 OK):**
```json
{
  "items": [
    {
      "id": "cart_item_1",
      "product": {
        "id": "prod_123",
        "name": "슬림핏 청바지",
        "brand": "LEVI'S"
      },
      "variant": {
        "id": "var_456",
        "sku": "LEVI-501-BLK-32-REG",
        "color": "black",
        "size": "32"
      },
      "quantity": 2,
      "price": 79000,
      "subtotal": 158000
    }
  ],
  "summary": {
    "itemCount": 3,
    "subtotal": 267000,
    "estimatedShipping": 3000,
    "estimatedTotal": 270000
  }
}
```

---

### 2.2 장바구니에 상품 추가
```http
POST /v1/cart/items
Authorization: Bearer {token}
Content-Type: application/json

{
  "variantId": "var_456",
  "quantity": 2
}
```

**요청 Body:**

| 필드 | 타입 | 필수 | 설명 | 제약 |
|------|------|------|------|------|
| variantId | string | O | 변량 ID | - |
| quantity | integer | O | 수량 | 1-99 |

**응답 예시 (201 Created):**
```json
{
  "id": "cart_item_1",
  "product": {...},
  "variant": {...},
  "quantity": 2,
  "price": 79000,
  "subtotal": 158000,
  "addedAt": "2024-03-15T10:30:00Z"
}
```

**에러 응답:**
- `409 INSUFFICIENT_STOCK`: 재고 부족
```json
{
  "code": "INSUFFICIENT_STOCK",
  "message": "재고가 부족합니다. 현재 재고: 3개",
  "details": {
    "availableStock": 3
  }
}
```

---

### 2.3 장바구니 항목 수량 변경
```http
PATCH /v1/cart/items/{itemId}
Authorization: Bearer {token}
Content-Type: application/json

{
  "quantity": 3
}
```

---

### 2.4 장바구니 항목 삭제
```http
DELETE /v1/cart/items/{itemId}
Authorization: Bearer {token}
```

**응답: 204 No Content**

---

### 2.5 장바구니 전체 비우기
```http
DELETE /v1/cart
Authorization: Bearer {token}
```

**응답: 204 No Content**

---

## 주문/결제

### 3.1 주문 생성
```http
POST /v1/orders
Authorization: Bearer {token}
Content-Type: application/json
```

**요청 Body:**
```json
{
  "items": [
    {
      "variantId": "var_456",
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

**요청 필드:**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| items | array | O | 주문 상품 목록 |
| items[].variantId | string | O | 변량 ID |
| items[].quantity | integer | O | 수량 (1-99) |
| shippingAddress | object | O | 배송지 정보 |
| shippingMethod | string | O | 배송 방법 (`standard`, `express`, `dawn`) |
| couponCode | string | X | 쿠폰 코드 |
| pointsToUse | integer | X | 사용할 포인트 |
| agreeToTerms | boolean | O | 구매 약관 동의 |
| requestMessage | string | X | 배송 요청사항 (최대 200자) |

**응답 예시 (201 Created):**
```json
{
  "id": "ord_789",
  "orderNumber": "2024031500123",
  "status": "PENDING_PAYMENT",
  "reservationExpiry": "2024-03-15T10:45:00Z",
  "items": [
    {
      "id": "item_1",
      "product": {...},
      "variant": {...},
      "quantity": 2,
      "price": 79000,
      "subtotal": 158000
    }
  ],
  "payment": {
    "amount": 146000,
    "breakdown": {
      "subtotal": 158000,
      "discount": -10000,
      "pointsUsed": -5000,
      "shipping": 3000,
      "total": 146000
    }
  },
  "createdAt": "2024-03-15T10:30:00Z"
}
```

**배송비 계산 규칙 [P0]:**

배송비는 다음 순서대로 결정됩니다:

1. **VIP 회원 확인** (최우선)
    - VIP 회원: **무료배송** (모든 상품)

2. **기본 배송비**
    - 기본값: **3,000원**
    - 무료배송 기준: **주문 금액 30,000원 이상**

3. **지역 할증료**
    - 제주도: **+3,000원** 추가
    - 도서산간: **+3,000원** 추가
    - 배송 불가 지역: 선택 불가 (인터페이스에서 제거)

**배송비 계산 예시:**

| 사용자 | 주문액 | 배송지 | 배송비 | 비고 |
|--------|--------|--------|--------|------|
| 일반회원 | 50,000 | 서울 | 0원 | 30K 이상 무료 |
| 일반회원 | 25,000 | 서울 | 3,000원 | 기본배송비 |
| 일반회원 | 25,000 | 제주 | 6,000원 | 3K + 3K 할증 |
| VIP회원 | 10,000 | 제주 | 0원 | VIP 무료 |

**비즈니스 로직:**
1. 재고 예약 (15분 TTL)
2. 배송비 계산 (위의 규칙 적용)
3. 쿠폰/포인트 적용
4. 최종 금액 계산
5. 주문 생성 (PENDING_PAYMENT 상태)

**에러 응답:**
- `409 INSUFFICIENT_STOCK`: 재고 부족
- `400 INVALID_COUPON`: 유효하지 않은 쿠폰
- `400 UNDELIVERABLE_AREA`: 배송 불가 지역

---

### 3.2 주문 목록 조회
```http
GET /v1/orders?status=PAID&page=1&limit=20
Authorization: Bearer {token}
```

**Query Parameters:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| status | string | X | 주문 상태 필터 |
| startDate | date | X | 시작 날짜 (YYYY-MM-DD) |
| endDate | date | X | 종료 날짜 |
| page | integer | X | 페이지 번호 |
| limit | integer | X | 페이지당 항목 수 |

**주문 상태 (OrderStatus):**
- `PENDING_PAYMENT`: 결제 대기
- `PAID`: 결제 완료
- `PREPARING`: 상품 준비중
- `SHIPPED`: 배송 시작
- `DELIVERED`: 배송 완료
- `CANCELLED`: 취소
- `RETURN_REQUESTED`: 반품 요청
- `RETURN_COMPLETED`: 반품 완료
- `EXCHANGE_REQUESTED`: 교환 요청
- `EXCHANGE_COMPLETED`: 교환 완료

---

### 3.3 주문 상세 조회
```http
GET /v1/orders/{orderId}
Authorization: Bearer {token}
```

**응답 예시 (200 OK):**
```json
{
  "id": "ord_789",
  "orderNumber": "2024031500123",
  "status": "SHIPPED",
  "items": [...],
  "payment": {...},
  "shipping": {
    "address": {...},
    "method": "standard",
    "fee": 3000,
    "trackingNumber": "123456789012",
    "carrier": "CJ대한통운",
    "estimatedDelivery": "2024-03-18"
  },
  "timeline": [
    {
      "status": "PAID",
      "timestamp": "2024-03-15T10:35:00Z"
    },
    {
      "status": "PREPARING",
      "timestamp": "2024-03-15T11:00:00Z"
    },
    {
      "status": "SHIPPED",
      "timestamp": "2024-03-16T09:00:00Z"
    }
  ],
  "createdAt": "2024-03-15T10:30:00Z",
  "updatedAt": "2024-03-16T09:00:00Z"
}
```

---

### 3.4 주문 취소
```http
POST /v1/orders/{orderId}/cancel
Authorization: Bearer {token}
Content-Type: application/json

{
  "reason": "CHANGE_OF_MIND",
  "detailReason": "사이즈가 맞지 않을 것 같아요"
}
```

**취소 사유 (reason):**
- `CHANGE_OF_MIND`: 단순 변심
- `FOUND_BETTER_PRICE`: 더 저렴한 가격 발견
- `ACCIDENTAL_ORDER`: 실수로 주문
- `OTHER`: 기타

**취소 가능 조건:**
- 결제 대기: ✅ 즉시 취소
- 결제 완료: ✅ 환불 처리
- 배송 준비중: ✅ 가능
- 배송중: ❌ 불가 (반품 필요)
- 배송완료: ❌ 불가 (반품 필요)

**응답 예시 (200 OK):**
```json
{
  "message": "주문이 취소되었습니다.",
  "refundAmount": 146000,
  "refundMethod": "CARD",
  "estimatedRefundDate": "2024-03-20"
}
```

**에러 응답 (400 CANNOT_CANCEL):**
```json
{
  "code": "CANNOT_CANCEL",
  "message": "이미 배송이 시작되어 취소할 수 없습니다. 반품을 신청해주세요."
}
```

---

### 3.5 결제 요청

```http
POST /v1/payments
Authorization: Bearer {token}
Content-Type: application/json
Idempotency-Key: {uuid}

{
  "orderId": "ord_789",
  "method": "CARD",
  "amount": 146000,
  "cardInfo": {
    "pgToken": "tok_abc123def456"
  }
}
```

**요청 헤더:**

| 헤더 | 필수 | 설명 |
|------|------|------|
| `Authorization` | O | JWT 토큰 |
| `Idempotency-Key` | O | **[P0]** UUID 형식의 멱등성 키 |

**요청 Body:**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| orderId | string | O | 주문 ID |
| method | string | O | 결제 수단 |
| amount | integer | O | 결제 금액 (원) |
| cardInfo.pgToken | string | O | PG사 토큰 |

**결제 수단 (method):**
- `CARD`: 신용/체크카드
- `TRANSFER`: 실시간 계좌이체
- `VIRTUAL_ACCOUNT`: 가상계좌
- `KAKAO_PAY`: 카카오페이
- `NAVER_PAY`: 네이버페이

**⚠️ 중요: 카드번호 직접 전송 금지**
- PG사 토큰(`pgToken`)을 사용해야 합니다.
- 프론트엔드에서 PG사 SDK로 토큰 발급 → 백엔드로 전달

---

#### **[P0] Idempotency Key 처리 규칙**

**목적:** 중복 결제 방지 및 분산 트랜잭션 안정성 보장

**규칙:**
1. **클라이언트 책임:**
    - UUID v4 생성 (예: `550e8400-e29b-41d4-a716-446655440000`)
    - 동일한 결제에는 항상 같은 키 전송
    - 재시도 시에도 동일한 키 사용

2. **서버 처리:**
   ```
   결제 요청 수신
   ├─ Idempotency-Key 저장 (payments 테이블)
   ├─ 중복 확인:
   │  ├─ 기존 결제 있으면 → 기존 결과 반환 (200 OK)
   │  └─ 새로운 결제 → 결제 진행
   ├─ PG사 API 호출 (3회 재시도, Exponential Backoff)
   └─ 결과 저장 및 응답
   ```

3. **처리 흐름:**
   ```
   첫 번째 요청 (Idempotency-Key: ABC-123)
   └─ 결제 진행 → 결과 저장

   두 번째 요청 (Idempotency-Key: ABC-123)
   └─ DB에서 기존 결과 조회 → 즉시 반환 (네트워크 재전송도 안전)

   세 번째 요청 (Idempotency-Key: XYZ-789)
   └─ 새로운 결제로 처리 (다른 키이므로)
   ```

**데이터베이스 설계:**
```sql
CREATE TABLE payments (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    method VARCHAR(50) NOT NULL,
    amount BIGINT NOT NULL,
    status ENUM('PENDING', 'APPROVED', 'FAILED', 'REFUNDED') NOT NULL,
    transaction_id VARCHAR(255),
    pg_code VARCHAR(100),
    fail_reason TEXT,
    approved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    UNIQUE KEY (order_id, idempotency_key)  -- 중복 방지
);

CREATE INDEX idx_payments_idempotency ON payments(idempotency_key);
```

---

**응답 예시 (201 Created):**
```json
{
  "id": "pay_999",
  "orderId": "ord_789",
  "status": "APPROVED",
  "method": "CARD",
  "amount": 146000,
  "transactionId": "pg_txn_123456",
  "approvedAt": "2024-03-15T10:35:00Z"
}
```

**중복 요청 응답 (200 OK):**
```json
{
  "id": "pay_999",
  "orderId": "ord_789",
  "status": "APPROVED",
  "method": "CARD",
  "amount": 146000,
  "transactionId": "pg_txn_123456",
  "approvedAt": "2024-03-15T10:35:00Z",
  "message": "기존 결제 결과를 반환합니다. (Idempotent)"
}
```

**결제 실패 응답 (402 PAYMENT_FAILED):**
```json
{
  "code": "PAYMENT_FAILED",
  "message": "카드 한도가 부족합니다.",
  "details": {
    "pgCode": "INSUFFICIENT_FUNDS",
    "retryable": true,
    "suggestedAction": "다른 결제 수단을 선택하거나 잠시 후 재시도하세요"
  }
}
```

**실패 시 권장 처리:**
```
1. retryable = true인 경우:
   ├─ 새로운 Idempotency-Key 생성
   ├─ 재시도 (최대 3회)
   └─ 지연: 1초, 2초, 4초 (Exponential Backoff)

2. retryable = false인 경우:
   ├─ 사용자에게 실패 알림
   └─ 다른 결제 수단 제안
```

---

#### **[P0] 분산 트랜잭션 처리 시나리오 (Saga Pattern)**

**목적:** 결제 + 재고 차감의 원자성 보장

**구현 패턴: 보상 트랜잭션 (Compensating Transactions)**

**시나리오 1: 결제 성공 → 재고 차감 실패**

```
흐름:
1. POST /v1/payments → 승인 성공 ✅
2. POST /v1/inventory/deduct → DB 오류 ❌

문제: 돈은 나갔는데 상품 재고가 안 차감됨

해결:
- 재고 차감 실패 감지
- 결제 취소 요청 (PG사 API)
- 주문 상태: PAYMENT_COMPLETE → STOCK_DEDUCTION_FAILED
- 환불 진행 (3일 이내)
- CS 알림: 고객에게 "시스템 오류로 자동 환불 처리됨" 안내

구현:
```python
@transaction.atomic
def handle_payment(order_id, idempotency_key):
    try:
        # 1단계: 결제 처리
        payment = call_pg_api(order_id)  # 결제 승인
        payment.save()

        # 2단계: 재고 차감 (동시성 제어)
        deduct_inventory(order_id)  # 실패 가능

        # 성공
        order.status = 'PAID'
        order.save()
        return payment

    except InventoryError as e:
        # 보상 트랜잭션: 환불 처리
        payment.cancel(refund_reason="System error: Stock deduction failed")
        order.status = 'PAYMENT_FAILED_COMPENSATED'
        order.save()

        # CS 티켓 자동 생성
        create_cs_ticket(
            order_id=order_id,
            reason="결제 후 재고 차감 실패로 자동 환불 처리",
            priority="HIGH"
        )

        raise InventoryError(f"재고 차감 실패, 환불 처리됨: {e}")
```

---

**시나리오 2: 환불 처리 중 재고 복구 실패**

```
흐름:
1. 주문 취소 요청 (배송 준비 중)
2. 결제 취소 성공 ✅
3. 재고 복구 실패 ❌

문제: 돈은 돌려받는데 재고가 복구 안됨

해결:
- 재고 복구는 멱등성 보장 필수
- 예약 재고 → 물리적 재고로 즉시 이동
- 실패 시에도 다시 시도 가능 (중복 추가 방지)

구현:
```python
def restore_inventory(order_id, max_retries=3):
    '''재고 복구는 멱등성 보장해야 함'''
    order = Order.objects.get(id=order_id)

    for attempt in range(max_retries):
        try:
            with transaction.atomic():
                for item in order.items.all():
                    # 비관적 락으로 동시성 제어
                    inventory = Inventory.objects.select_for_update().get(
                        sku=item.sku
                    )

                    # 예약 재고에서 차감 + 물리 재고에 추가
                    inventory.reserved_stock -= item.quantity
                    inventory.physical_stock += item.quantity
                    inventory.available_stock = (
                        inventory.physical_stock
                        - inventory.reserved_stock
                        - inventory.safety_stock
                    )
                    inventory.save()

                return True  # 성공

        except DatabaseError as e:
            if attempt == max_retries - 1:
                # 최종 실패 → 배치 작업으로 나중에 재처리
                create_inventory_restore_job(
                    order_id=order_id,
                    retry_count=max_retries
                )
                return False

            # 지수 백오프로 재시도
            time.sleep(2 ** attempt)
```

---

**시나리오 3: 주문 생성 + 결제 실패 → 예약 재고 자동 해제**

```
흐름:
1. 주문 생성: 재고 예약 ✅ (TTL: 15분)
2. 결제 진행: 실패 ❌
3. 예약 재고 자동 해제 ✅ (15분 경과)

구현:
```python
# 1. 주문 생성 시 예약
order = Order.objects.create(...)
reservation = Reservation.objects.create(
    order_id=order.id,
    sku=item.sku,
    quantity=item.quantity,
    expires_at=now() + timedelta(minutes=15)  # 15분 후 자동 해제
)

# 2. 배치 작업: 5분마다 실행
@periodic_task(run_every=crontab(minute='*/5'))
def release_expired_reservations():
    '''만료된 예약 자동 해제'''
    expired = Reservation.objects.filter(
        expires_at__lt=now(),
        status='ACTIVE'
    )

    for reservation in expired:
        with transaction.atomic():
            inventory = Inventory.objects.select_for_update().get(
                sku=reservation.sku
            )
            inventory.reserved_stock -= reservation.quantity
            inventory.save()

            reservation.status = 'EXPIRED'
            reservation.save()
```

---

**데이터베이스 설계:**

```sql
-- 결제 테이블: Idempotency 보장
CREATE TABLE payments (
    ...
    UNIQUE KEY (order_id, idempotency_key)
);

-- 예약 재고 추적
CREATE TABLE reservations (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    sku VARCHAR(100) NOT NULL,
    quantity INT NOT NULL,
    status ENUM('ACTIVE', 'CONFIRMED', 'EXPIRED') NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,

    FOREIGN KEY (order_id) REFERENCES orders(id),
    INDEX idx_reservations_expires (expires_at)
);

-- 결제 실패 로그
CREATE TABLE payment_failures (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    reason VARCHAR(255) NOT NULL,
    pg_code VARCHAR(100),
    compensation_status ENUM('PENDING', 'COMPENSATED', 'FAILED') NOT NULL,
    created_at TIMESTAMP NOT NULL,

    INDEX idx_payment_failures_order (order_id)
);
```

---

**모니터링 및 알림:**

```yaml
결제 시스템 모니터링:
  1. Payment Success Rate
     - 목표: 95% 이상
     - 알람: 90% 이하 → PagerDuty 알림

  2. Compensation Transaction
     - 보상 트랜잭션 발생 건수 추적
     - 시간당 3건 이상 → 이상 알림

  3. Inventory Deduction Failure
     - 재고 차감 실패율
     - 목표: 0.1% 이하
```

---

### 3.6 결제 실패 시 Fallback 처리

**[P0] PG사 API 장애 대응 전략**

```http
POST /v1/payments/fallback
Authorization: Bearer {token}
Content-Type: application/json

{
  "orderId": "ord_789",
  "fallbackMethod": "VIRTUAL_ACCOUNT"
}
```

**상황: PG사 API가 응답하지 않을 때**

```
정상 결제 흐름:
POST /v1/payments
├─ PG사 API 호출 (신용카드 결제)
├─ 승인 대기 (3초)
├─ 승인 완료 → 재고 차감

PG사 장애 시:
POST /v1/payments
├─ PG사 API 호출 (Timeout: 3초)
├─ Circuit Breaker 작동
├─ Fallback 옵션 제시 → 고객에게 안내
│  ├─ 가상계좌 결제 (즉시 채번)
│  ├─ 계좌이체 (수동 입금)
│  └─ 결제 대기 (1시간 후 재시도)
└─ 고객이 선택한 방법으로 진행
```

**구현:**

```python
class PaymentCircuitBreaker:
    """PG사 API 장애 감지 및 대응"""

    def call_pg_api(self, order_id, method, amount):
        try:
            # 3초 타임아웃
            response = requests.post(
                'https://pg.example.com/payment',
                json={...},
                timeout=3
            )
            self.success()
            return response

        except (Timeout, ConnectionError) as e:
            self.failure()

            # Circuit Breaker 상태 확인
            if self.is_open():
                # 많은 실패 발생 → Fallback으로 전환
                return self.create_virtual_account(order_id, amount)

            raise PaymentError(
                code="PG_API_UNAVAILABLE",
                message="결제 서비스 일시 불가, 가상계좌로 진행됩니다",
                fallback_options=[
                    {
                        "method": "VIRTUAL_ACCOUNT",
                        "description": "즉시 계좌 발급",
                        "instruction": "10분 이내 입금하세요"
                    },
                    {
                        "method": "RETRY_LATER",
                        "description": "1시간 후 재시도",
                        "deadline": "2024-03-15T11:35:00Z"
                    }
                ]
            )

    def create_virtual_account(self, order_id, amount):
        """
        가상계좌 자동 발급
        - 10분 유효
        - 입금 시 자동 확인
        """
        account = VirtualAccount.objects.create(
            order_id=order_id,
            amount=amount,
            bank='국민은행',
            account_number=self.generate_account(),
            expires_at=now() + timedelta(minutes=10)
        )

        return {
            "method": "VIRTUAL_ACCOUNT",
            "account": {
                "bank": account.bank,
                "number": account.account_number,
                "holder": "Fashion Store",
                "amount": amount
            },
            "deadline": account.expires_at
        }
```

**응답 예시 (503 Service Unavailable):**

```json
{
  "code": "PG_API_UNAVAILABLE",
  "message": "결제 서비스 일시 불가합니다",
  "fallbackOptions": [
    {
      "method": "VIRTUAL_ACCOUNT",
      "description": "즉시 가상계좌 발급",
      "account": {
        "bank": "국민은행",
        "number": "123-456-789012",
        "holder": "Fashion Store",
        "amount": 146000
      },
      "deadline": "2024-03-15T10:50:00Z"
    },
    {
      "method": "RETRY_LATER",
      "description": "1시간 후 신용카드 재시도",
      "deadline": "2024-03-15T11:35:00Z"
    }
  ]
}
```

---

### 3.7 PG사 Webhook 처리 (결제 결과 수신)

**[P0] Webhook 보안 검증 및 멱등성 처리**

```http
POST /v1/webhooks/payments
Content-Type: application/json

{
  "eventId": "evt_123456",
  "eventType": "payment.completed",
  "timestamp": "2024-03-15T10:35:00Z",
  "data": {
    "orderId": "ord_789",
    "transactionId": "pg_txn_123456",
    "amount": 146000,
    "status": "completed"
  },
  "signature": "sha256=abcdef1234567890..."
}
```

**요청 헤더:**

| 헤더 | 필수 | 설명 |
|------|------|------|
| `Content-Type` | O | `application/json` |
| `X-Webhook-Signature` | O | **[P0]** HMAC-SHA256 서명 |
| `X-Webhook-Nonce` | O | **[P0]** Replay Attack 방지용 난수 |
| `X-Webhook-Timestamp` | O | 요청 생성 시간 (Unix timestamp) |

---

#### **[P0] Webhook 검증 3단계**

**1단계: 서명 검증 (HMAC-SHA256)**

```
문제: PG사로 위장한 위조 요청이 들어올 수 있음
해결: PG사와 공유된 Secret Key로 서명 검증

구현:
```python
import hmac
import hashlib

def verify_webhook_signature(request_body, signature, secret_key):
    """PG사 Webhook 서명 검증"""

    # 요청 본문으로 HMAC 생성
    expected_signature = hmac.new(
        key=secret_key.encode(),
        msg=request_body,
        digestmod=hashlib.sha256
    ).hexdigest()

    # 시간 기반 비교로 타이밍 공격 방지
    if not hmac.compare_digest(signature, expected_signature):
        return False, "Invalid signature"

    return True, None
```

**응답:**
```
- ✅ 서명 일치: 처리 진행
- ❌ 서명 불일치: 401 Unauthorized
```

---

**2단계: Replay Attack 방지 (Nonce)**

```
문제: 이미 처리한 Webhook을 다시 받으면?
      → 중복 환불, 중복 주문 생성 위험

해결: Nonce(일회용 난수) + Timestamp 검증

구현:
```python
from datetime import datetime, timedelta

def verify_webhook_nonce(nonce, timestamp, max_age_seconds=300):
    """Replay Attack 방지"""

    # 1. Timestamp 검증 (5분 이내)
    request_time = datetime.fromtimestamp(int(timestamp))
    current_time = datetime.utcnow()

    if (current_time - request_time).total_seconds() > max_age_seconds:
        return False, "Request too old (timestamp expired)"

    # 2. Nonce 중복 확인 (Redis)
    if redis.exists(f"webhook_nonce:{nonce}"):
        return False, "Duplicate nonce (already processed)"

    # 3. Nonce 등록 (5분 TTL)
    redis.setex(f"webhook_nonce:{nonce}", max_age_seconds, "1")

    return True, None
```

---

**3단계: Idempotency 보장 (Event ID 기반)**

```
문제: 네트워크 오류로 같은 Webhook이 2번 전송되면?
해결: Event ID를 저장해서 중복 처리 방지

구현:
```python
def handle_webhook(request):
    """PG사 Webhook 처리"""

    # 1. 서명 검증
    body = request.body
    signature = request.headers.get('X-Webhook-Signature')
    valid, error = verify_webhook_signature(
        body,
        signature,
        settings.PG_SECRET_KEY
    )
    if not valid:
        return JsonResponse({"error": error}, status=401)

    # 2. Nonce/Timestamp 검증
    nonce = request.headers.get('X-Webhook-Nonce')
    timestamp = request.headers.get('X-Webhook-Timestamp')
    valid, error = verify_webhook_nonce(nonce, timestamp)
    if not valid:
        return JsonResponse({"error": error}, status=400)

    # 3. Webhook 로그 저장 (중복 방지)
    event_id = request.json.get('eventId')

    try:
        webhook_log = WebhookLog.objects.create(
            event_id=event_id,
            event_type=request.json.get('eventType'),
            payload=request.json,
            status='PROCESSING'
        )
    except IntegrityError:
        # 이미 처리된 Event ID
        existing = WebhookLog.objects.get(event_id=event_id)
        if existing.status == 'COMPLETED':
            return JsonResponse(
                {
                    "message": "이미 처리된 이벤트입니다",
                    "eventId": event_id
                },
                status=200
            )

    # 4. 실제 처리 (비동기)
    try:
        process_payment_webhook.delay(event_id)
        webhook_log.status = 'QUEUED'
        webhook_log.save()

        return JsonResponse({"status": "accepted"}, status=202)

    except Exception as e:
        webhook_log.status = 'FAILED'
        webhook_log.error_message = str(e)
        webhook_log.save()

        # 재시도 큐에 등록
        retry_queue.push({
            'event_id': event_id,
            'retry_count': 0,
            'max_retries': 3
        })

        return JsonResponse(
            {"error": "Processing failed, will retry"},
            status=500
        )
```

---

#### **[P0] Webhook 처리 플로우**

```
PG사 → Webhook 전송
    │
    ├─ 서명 검증 (HMAC-SHA256)
    │  ├─ ✅ 통과 → 다음
    │  └─ ❌ 실패 → 401 Unauthorized
    │
    ├─ Nonce/Timestamp 검증
    │  ├─ ✅ 통과 → 다음
    │  └─ ❌ 실패 → 400 Bad Request
    │
    ├─ Event ID 중복 확인
    │  ├─ 이미 존재 & COMPLETED → 200 OK (무시)
    │  └─ 새로운 Event → 다음
    │
    ├─ 비동기 큐에 추가 (Celery/RabbitMQ)
    │  └─ 200 Accepted 즉시 반환 (3초 내 응답)
    │
    └─ 백그라운드 처리
        ├─ 주문 상태 업데이트
        ├─ 재고 차감
        ├─ 포인트 적립
        ├─ 배송 준비
        └─ WebhookLog 상태 = COMPLETED
```

---

#### **[P0] Webhook 데이터베이스 설계**

```sql
-- Webhook 로그 (중복 처리 방지)
CREATE TABLE webhook_logs (
    id UUID PRIMARY KEY,
    event_id VARCHAR(255) NOT NULL UNIQUE,  -- 이벤트 ID로 중복 방지
    event_type VARCHAR(100) NOT NULL,       -- payment.completed, etc.
    order_id UUID,                           -- 어떤 주문의 이벤트인지
    payload JSONB NOT NULL,                  -- 전체 Webhook 데이터
    status ENUM('PROCESSING', 'QUEUED', 'COMPLETED', 'FAILED') NOT NULL,
    error_message TEXT,
    retry_count INT DEFAULT 0,
    processed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    FOREIGN KEY (order_id) REFERENCES orders(id),
    INDEX idx_webhook_logs_event_id (event_id),
    INDEX idx_webhook_logs_status (status),
    INDEX idx_webhook_logs_order_id (order_id)
);

-- Webhook 재시도 큐
CREATE TABLE webhook_retry_queue (
    id UUID PRIMARY KEY,
    event_id VARCHAR(255) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    max_retries INT NOT NULL DEFAULT 3,
    next_retry_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    INDEX idx_retry_queue_next_retry (next_retry_at),
    FOREIGN KEY (event_id) REFERENCES webhook_logs(event_id)
);
```

---

#### **[P0] Webhook 재시도 정책**

```python
@periodic_task(run_every=crontab(minute='*/1'))
def retry_failed_webhooks():
    """실패한 Webhook 재처리 (1분마다 확인)"""

    # 다음 재시도 시간이 된 항목 조회
    retry_items = WebhookRetryQueue.objects.filter(
        next_retry_at__lte=now()
    )

    for item in retry_items:
        webhook_log = WebhookLog.objects.get(event_id=item.event_id)

        if item.retry_count >= item.max_retries:
            # 최대 재시도 횟수 초과
            webhook_log.status = 'FAILED'
            webhook_log.error_message = 'Max retry attempts exceeded'
            webhook_log.save()
            item.delete()

            # CS 알림
            send_cs_alert(
                type='WEBHOOK_MAX_RETRY_EXCEEDED',
                event_id=item.event_id,
                order_id=webhook_log.order_id
            )
            continue

        # 재시도 처리
        try:
            process_payment_webhook(item.event_id)
            webhook_log.status = 'COMPLETED'
            webhook_log.processed_at = now()
            webhook_log.save()
            item.delete()

        except Exception as e:
            # 재시도 예약
            item.retry_count += 1
            item.next_retry_at = now() + timedelta(seconds=60 * (2 ** item.retry_count))
            item.save()
```

---

#### **[P0] Webhook 모니터링 및 알림**

```yaml
Webhook 모니터링:
  1. 처리 성공률
     - 목표: 99.9% 이상
     - 알람: 99% 이하 → PagerDuty 즉시 알림

  2. 처리 지연시간
     - 목표: 평균 100ms 이내
     - 알람: 1초 이상 → 개선 필요

  3. 재시도 발생 빈도
     - 모니터: 시간당 재시도 건수 추적
     - 알람: 시간당 10건 이상 → 조사 필요

  4. 최대 재시도 초과
     - 심각도: High
     - 자동 CS 티켓 생성: "Webhook 처리 실패로 주문 상태 미반영"
     - 수동 처리 필요
```

---

## 재고 관리

### 4.1 실시간 재고 조회
```http
GET /v1/inventory/skus/{sku}
```

**응답 예시 (200 OK):**
```json
{
  "sku": "LEVI-501-BLK-32-REG",
  "available": 15,
  "reserved": 3,
  "physical": 20,
  "safetyStock": 2,
  "status": "IN_STOCK",
  "lastUpdated": "2024-03-15T10:30:00Z"
}
```

**재고 상태:**
- `available`: 가용 재고 (판매 가능)
- `reserved`: 예약 재고 (결제 대기중)
- `physical`: 물리적 재고 (창고 실재고)
- `safetyStock`: 안전 재고 (최소 유지 수량)

**재고 계산 공식:**
```
available = physical - reserved - safetyStock
```

---

### 4.2 재입고 알림 신청
```http
POST /v1/inventory/restock-notifications
Authorization: Bearer {token}
Content-Type: application/json

{
  "sku": "LEVI-501-BLK-32-REG",
  "notifyVia": ["EMAIL", "PUSH"]
}
```

**알림 수단:**
- `EMAIL`: 이메일
- `PUSH`: 앱 푸시 알림
- `SMS`: 문자 메시지

**응답 (201 Created):**
```json
{
  "message": "재입고 알림이 신청되었습니다.",
  "notificationId": "noti_123"
}
```

---

## 배송

### 5.1 배송 추적
```http
GET /v1/shipments/{shipmentId}/tracking
```

**응답 예시 (200 OK):**
```json
{
  "shipmentId": "ship_555",
  "orderId": "ord_789",
  "carrier": "CJ대한통운",
  "trackingNumber": "123456789012",
  "status": "IN_TRANSIT",
  "estimatedDelivery": "2024-03-18",
  "events": [
    {
      "status": "PICKED_UP",
      "location": "서울 강남구 물류센터",
      "timestamp": "2024-03-16T09:00:00Z",
      "description": "상품이 집하되었습니다"
    },
    {
      "status": "IN_TRANSIT",
      "location": "대전 분류센터",
      "timestamp": "2024-03-16T15:30:00Z",
      "description": "상품이 배송 중입니다"
    }
  ]
}
```

**배송 상태:**
- `PREPARING`: 배송 준비중
- `PICKED_UP`: 집하 완료
- `IN_TRANSIT`: 배송 중
- `OUT_FOR_DELIVERY`: 배송 출발
- `DELIVERED`: 배송 완료

---

## 반품/교환

### 6.1 반품 요청
```http
POST /v1/returns
Authorization: Bearer {token}
Content-Type: application/json
```

**요청 Body:**
```json
{
  "orderId": "ord_789",
  "items": [
    {
      "orderItemId": "item_1",
      "quantity": 1,
      "reason": "SIZE_ISSUE",
      "detailReason": "사이즈가 작아요",
      "images": ["https://..."]
    }
  ],
  "refundMethod": "ORIGINAL",
  "bankAccount": {
    "bank": "신한은행",
    "accountNumber": "110-123-456789",
    "holder": "홍길동"
  }
}
```

**반품 사유 (reason):**
- `SIZE_ISSUE`: 사이즈 문제
- `DEFECTIVE`: 불량
- `WRONG_ITEM`: 오배송
- `NOT_AS_DESCRIBED`: 상품 설명과 다름
- `CHANGE_OF_MIND`: 단순 변심

**환불 방법 (refundMethod):**
- `ORIGINAL`: 원결제수단
- `ACCOUNT`: 계좌 환불

**응답 예시 (201 Created):**
```json
{
  "id": "ret_111",
  "orderId": "ord_789",
  "status": "REQUESTED",
  "returnShipping": {
    "method": "CUSTOMER_ARRANGED",
    "fee": 6000,
    "feePaymentBy": "CUSTOMER"
  },
  "expectedRefund": 83000,
  "createdAt": "2024-03-20T10:00:00Z"
}
```

**반품 배송비:**
- 단순 변심: 고객 부담 (6,000원)
- 불량/오배송: 판매자 부담 (무료)

---

### 6.2 반품 상태 조회
```http
GET /v1/returns/{returnId}
Authorization: Bearer {token}
```

**반품 상태:**
- `REQUESTED`: 요청됨
- `APPROVED`: 승인됨
- `REJECTED`: 거부됨
- `SHIPPING`: 반품 배송중
- `RECEIVED`: 반품 상품 도착
- `INSPECTING`: 검수중
- `COMPLETED`: 완료

---

### 6.3 교환 요청
```http
POST /v1/exchanges
Authorization: Bearer {token}
Content-Type: application/json

{
  "orderId": "ord_789",
  "items": [
    {
      "orderItemId": "item_1",
      "fromVariantId": "var_456",
      "toVariantId": "var_457",
      "reason": "SIZE_ISSUE"
    }
  ]
}
```

**응답 예시 (201 Created):**
```json
{
  "id": "ex_222",
  "orderId": "ord_789",
  "status": "REQUESTED",
  "stockStatus": "AVAILABLE",
  "exchangeShipping": {
    "fee": 6000,
    "feePaymentBy": "CUSTOMER"
  },
  "createdAt": "2024-03-20T10:00:00Z"
}
```

**교환 재고 상태:**
- `AVAILABLE`: 재고 있음 → 교환 진행
- `OUT_OF_STOCK`: 재고 없음 → 환불 또는 대기

---

## 리뷰

### 7.1 상품 리뷰 목록
```http
GET /v1/products/{productId}/reviews?rating=5&hasPhoto=true&sort=recent&page=1&limit=20
```

**Query Parameters:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| rating | integer | X | 평점 필터 (1-5) |
| hasPhoto | boolean | X | 포토 리뷰만 |
| sort | string | X | 정렬 (`recent`, `helpful`) |
| page | integer | X | 페이지 번호 |
| limit | integer | X | 페이지당 항목 수 |

**응답 예시:**
```json
{
  "data": [
    {
      "id": "rev_1",
      "user": {
        "id": "user_123",
        "name": "홍*동",
        "tier": "VIP"
      },
      "product": {...},
      "variant": {...},
      "rating": 5,
      "title": "핏이 좋아요",
      "content": "사이즈 추천대로 주문했는데 핏이 딱 맞네요!",
      "images": ["https://..."],
      "sizeRating": "FITS_WELL",
      "helpfulCount": 24,
      "createdAt": "2024-03-10T15:00:00Z"
    }
  ],
  "pagination": {...},
  "summary": {
    "averageRating": 4.5,
    "totalReviews": 128,
    "ratingDistribution": {
      "5": 80,
      "4": 30,
      "3": 10,
      "2": 5,
      "1": 3
    }
  }
}
```

---

### 7.2 리뷰 작성
```http
POST /v1/reviews
Authorization: Bearer {token}
Content-Type: application/json

{
  "orderId": "ord_789",
  "orderItemId": "item_1",
  "rating": 5,
  "title": "핏이 좋아요",
  "content": "사이즈 추천대로 주문했는데 핏이 딱 맞네요!",
  "images": ["https://..."],
  "sizeRating": "FITS_WELL"
}
```

**사이즈 평가 (sizeRating):**
- `TOO_SMALL`: 작아요
- `FITS_WELL`: 딱 맞아요
- `TOO_LARGE`: 커요

**포인트 적립:**
- 일반 리뷰: 500P
- 포토 리뷰: 1,000P

**응답 (201 Created):**
```json
{
  "id": "rev_1",
  "rating": 5,
  "title": "핏이 좋아요",
  "content": "...",
  "pointsEarned": 1000,
  "createdAt": "2024-03-20T10:00:00Z"
}
```

---

## 사용자

### 8.1 회원가입
```http
POST /v1/users/register
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "********",
  "name": "홍길동",
  "phone": "010-1234-5678",
  "agreeToTerms": true,
  "agreeToMarketing": false
}
```

**비밀번호 요구사항:**
- 최소 8자
- 영문 + 숫자 조합
- 특수문자 포함 권장

**응답 (201 Created):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresIn": 3600,
  "user": {
    "id": "user_123",
    "email": "user@example.com",
    "name": "홍길동",
    "tier": "GENERAL"
  }
}
```

**에러 응답:**
- `409 EMAIL_ALREADY_EXISTS`: 이미 사용중인 이메일

---

### 8.2 로그인
```http
POST /v1/users/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "********"
}
```

**응답 (200 OK):**
- 회원가입과 동일한 형식

---

### 8.3 내 정보 조회
```http
GET /v1/users/me
Authorization: Bearer {token}
```

**응답 (200 OK):**
```json
{
  "id": "user_123",
  "email": "user@example.com",
  "name": "홍길동",
  "phone": "010-1234-5678",
  "tier": "VIP",
  "createdAt": "2024-01-01T00:00:00Z"
}
```

**회원 등급 (tier):**
- `GENERAL`: 일반 회원
- `VIP`: VIP 회원 (최근 6개월 구매 금액 100만원 이상)

**VIP 혜택:**
- 전 상품 무료배송
- 추가 포인트 적립 (2%)
- 우선 고객 지원

---

### 8.4 배송지 관리

**배송지 목록 조회:**
```http
GET /v1/users/me/addresses
Authorization: Bearer {token}
```

**배송지 추가:**
```http
POST /v1/users/me/addresses
Authorization: Bearer {token}
Content-Type: application/json

{
  "name": "홍길동",
  "phone": "010-1234-5678",
  "address": "서울특별시 강남구 테헤란로 123",
  "addressDetail": "456호",
  "zipCode": "06000",
  "isDefault": true
}
```

---

### 8.5 사이즈 프로필 관리

**사이즈 프로필 조회:**
```http
GET /v1/users/me/size-profile
Authorization: Bearer {token}
```

**사이즈 프로필 수정:**
```http
PATCH /v1/users/me/size-profile
Authorization: Bearer {token}
Content-Type: application/json

{
  "height": 175,
  "weight": 70,
  "topSize": "L",
  "bottomSize": "32",
  "shoeSize": 270,
  "preferredFit": "SLIM",
  "bodyType": "ATHLETIC"
}
```

**선호 핏 (preferredFit):**
- `TIGHT`: 타이트
- `SLIM`: 슬림
- `REGULAR`: 레귤러
- `LOOSE`: 루즈

**체형 (bodyType):**
- `SLIM`: 마른 체형
- `ATHLETIC`: 운동 체형
- `AVERAGE`: 보통 체형
- `MUSCULAR`: 근육질
- `HEAVY`: 통통한 체형

---

## 쿠폰/포인트

### 9.1 내 쿠폰 목록
```http
GET /v1/users/me/coupons?status=AVAILABLE
Authorization: Bearer {token}
```

**쿠폰 상태:**
- `AVAILABLE`: 사용 가능
- `USED`: 사용됨
- `EXPIRED`: 만료됨

---

### 9.2 쿠폰 적용 검증
```http
POST /v1/coupons/validate
Authorization: Bearer {token}
Content-Type: application/json

{
  "couponCode": "SUMMER2024",
  "orderAmount": 158000
}
```

**응답 (200 OK):**
```json
{
  "valid": true,
  "discount": 10000,
  "message": "10,000원 할인이 적용됩니다"
}
```

**응답 (400 INVALID_COUPON):**
```json
{
  "code": "INVALID_COUPON",
  "message": "최소 주문 금액(50,000원)을 충족하지 못했습니다.",
  "details": {
    "minOrderAmount": 50000,
    "currentAmount": 30000
  }
}
```

---

### 9.3 포인트 잔액 조회
```http
GET /v1/users/me/points/balance
Authorization: Bearer {token}
```

**응답 (200 OK):**
```json
{
  "balance": 15000,
  "expiringPoints": 3000,
  "expiryDate": "2024-12-31"
}
```

---

### 9.4 포인트 히스토리
```http
GET /v1/users/me/points/history?type=EARNED&page=1&limit=20
Authorization: Bearer {token}
```

**포인트 유형:**
- `EARNED`: 적립
- `USED`: 사용
- `EXPIRED`: 소멸

**응답 (200 OK):**
```json
{
  "data": [
    {
      "id": "pt_hist_1",
      "type": "EARNED",
      "amount": 890,
      "description": "주문 구매 확정 (ord_789)",
      "createdAt": "2024-03-20T10:00:00Z",
      "expiryDate": "2025-03-20"
    }
  ],
  "pagination": {...}
}
```

---

### 9.5 포인트 적립 [P0]

**포인트 자동 적립 (구매 확정 시):**

```http
POST /v1/users/me/points/earn
Authorization: Bearer {token}
Content-Type: application/json

{
  "orderId": "ord_789",
  "orderAmount": 89000,
  "earnType": "PURCHASE_CONFIRMATION"
}
```

**포인트 적립 규칙:**

| 적립 유형 | 적립률 | 조건 | 설명 |
|---------|-------|------|------|
| 구매 확정 | 1% | 배송 완료 후 자동 | 결제 금액의 1% 적립 |
| VIP 구매 | 2% | VIP 회원 | VIP 회원 결제 금액의 2% 적립 |
| 리뷰 작성 | 500P | 일반 리뷰 | 상품 리뷰 작성 시 500P 적립 |
| 포토 리뷰 | 1,000P | 포토 포함 | 사진과 함께 리뷰 작성 시 1,000P 적립 |

**응답 (200 OK):**
```json
{
  "pointsEarned": 890,
  "previousBalance": 15000,
  "newBalance": 15890,
  "expiryDate": "2025-03-20",
  "orderId": "ord_789",
  "earnedAt": "2024-03-20T10:00:00Z"
}
```

**응답 (400 INVALID_ORDER):**
```json
{
  "code": "INVALID_ORDER",
  "message": "주문이 존재하지 않거나 포인트 적립 불가능 상태입니다.",
  "details": {
    "orderId": "ord_789",
    "orderStatus": "PENDING_PAYMENT"
  }
}
```

**포인트 소멸 정책:**
- 최종 적립일로부터 1년 후 자동 소멸
- 소멸 예정 포인트는 별도 추적 (`expiringPoints`)
- 사용 시 소멸 예정 포인트부터 먼저 차감

---

### 9.6 포인트 사용 [P0]

**주문 생성 시 포인트 사용:**

```http
POST /v1/orders
Authorization: Bearer {token}
Content-Type: application/json

{
  "items": [...],
  "shippingAddress": {...},
  "shippingMethod": "standard",
  "couponCode": "SUMMER2024",
  "pointsToUse": 5000,
  "pointsDeductionType": "MANUAL"
}
```

**포인트 사용 규칙:**

| 항목 | 규칙 | 설명 |
|------|------|------|
| 최소 사용 단위 | 1,000P | 1,000P 단위로만 사용 가능 |
| 최대 사용 한도 | 주문금액 80% | 주문금액의 80%까지만 사용 |
| 적용 순서 | 쿠폰 할인 후 | 쿠폰 할인 금액을 제외한 금액에서 차감 |
| 소멸 포인트 우선 | 소멸 예정부터 사용 | 만료 예정 포인트를 먼저 차감 |
| 사용 타입 | MANUAL / AUTO | MANUAL: 사용자 요청, AUTO: 포인트 부족 자동 사용 |

**포인트 사용 검증:**

```http
POST /v1/points/validate-usage
Authorization: Bearer {token}
Content-Type: application/json

{
  "requestedPoints": 5000,
  "orderAmount": 89000
}
```

**응답 (200 OK - 검증 성공):**
```json
{
  "valid": true,
  "requestedPoints": 5000,
  "availablePoints": 15000,
  "expiringPoints": 3000,
  "pointsToUseFromExpiring": 3000,
  "pointsToUseFromRegular": 2000,
  "finalOrderAmount": 84000,
  "message": "포인트 5,000P를 사용할 수 있습니다 (소멸예정 3,000P + 일반 2,000P)"
}
```

**응답 (400 INSUFFICIENT_POINTS):**
```json
{
  "code": "INSUFFICIENT_POINTS",
  "message": "사용 가능한 포인트가 부족합니다.",
  "details": {
    "requestedPoints": 5000,
    "availablePoints": 3000,
    "shortfall": 2000
  }
}
```

**응답 (400 INVALID_POINTS_AMOUNT):**
```json
{
  "code": "INVALID_POINTS_AMOUNT",
  "message": "포인트는 1,000P 단위로 사용 가능합니다.",
  "details": {
    "requestedPoints": 5500,
    "remainder": 500
  }
}
```

**응답 (400 POINTS_EXCEED_LIMIT):**
```json
{
  "code": "POINTS_EXCEED_LIMIT",
  "message": "주문금액의 80%를 초과할 수 없습니다.",
  "details": {
    "orderAmount": 89000,
    "maxPointsAllowed": 71200,
    "requestedPoints": 80000
  }
}
```

**주문 취소 시 포인트 복구:**

```http
POST /v1/orders/{orderId}/cancel
Authorization: Bearer {token}
Content-Type: application/json

{
  "reason": "CUSTOMER_CHANGE_OF_MIND"
}
```

**포인트 복구 규칙:**
- 주문 취소 시 사용한 포인트는 즉시 복구
- 만료 예정 포인트 사용 후 취소 시, 원래 만료 예정일이 연장됨 (동일 기간)
- 이미 소멸된 포인트는 복구 불가능

**응답 (200 OK - 주문 취소 성공):**
```json
{
  "message": "주문이 취소되었습니다.",
  "refundAmount": 84000,
  "pointsRestored": 5000,
  "restoredAt": "2024-03-20T15:30:00Z",
  "estimatedRefundDate": "2024-03-25"
}
```

---

## 부록

### A. 에러 코드 목록

| 코드 | HTTP | 설명 |
|------|------|------|
| VALIDATION_ERROR | 400 | 요청 데이터 검증 실패 |
| UNAUTHORIZED | 401 | 인증 필요 |
| FORBIDDEN | 403 | 권한 없음 |
| NOT_FOUND | 404 | 리소스 없음 |
| INSUFFICIENT_STOCK | 409 | 재고 부족 |
| INVALID_COUPON | 400 | 유효하지 않은 쿠폰 |
| PAYMENT_FAILED | 402 | 결제 실패 |
| CANNOT_CANCEL | 400 | 취소 불가 |
| INTERNAL_ERROR | 500 | 서버 오류 |

### B. 배송비 정책

| 조건 | 배송비 |
|------|--------|
| 기본 배송비 | 3,000원 |
| 30,000원 이상 구매 | 무료 |
| VIP 회원 | 전 상품 무료 |
| 제주/도서산간 | +3,000원 |

### C. 포인트 정책

| 활동 | 적립 포인트 |
|------|------------|
| 구매 확정 | 결제 금액의 1% |
| 일반 리뷰 작성 | 500P |
| 포토 리뷰 작성 | 1,000P |

**포인트 소멸:**
- 최종 적립일로부터 1년

### D. 회원 등급 정책

| 등급 | 조건 | 혜택 |
|------|------|------|
| GENERAL | 기본 | - |
| VIP | 최근 6개월 구매 금액 100만원 이상 | 무료배송, 포인트 2% 적립 |

---

### 📌 **Critical: P0 이슈 3가지 (반드시 구현)**

본 API 명세서에서 **[P0]** 마크가 있는 항목들은 **분산 트랜잭션 안정성과 보안을 위해 반드시 구현**해야 합니다.

| 항목 | 섹션 | 내용 | 우선순위 |
|------|------|------|---------|
| **Idempotency Key** | 3.5 결제 요청 | 중복 결제 방지 및 멱등성 보장 | 🔴 P0 |
| **Saga Pattern** | 3.5 분산 트랜잭션 | 결제 + 재고 차감의 원자성 보장 | 🔴 P0 |
| **Webhook 보안** | 3.7 Webhook 처리 | PG사 Webhook 위조 방지 및 멱등성 | 🔴 P0 |

**이 3가지 없이는 프로덕션 배포 불가능합니다.**

**문서 버전:** v1.0.0  
**최종 수정일:** 2025-10-31  
**작성자:** Backend Team