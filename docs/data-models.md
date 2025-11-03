# 의류 이커머스 데이터 모델 설계

## 📋 목차
- [개요](#개요)
- [ERD 다이어그램](#erd-다이어그램)
- [엔티티 상세 설계](#엔티티-상세-설계)
- [인덱스 전략](#인덱스-전략)
- [비즈니스 규칙](#비즈니스-규칙)

---

## 개요

### 설계 원칙

1. **정규화**: 3NF까지 정규화, 필요 시 비정규화
2. **확장성**: 수평 확장 가능한 구조
3. **성능**: 적절한 인덱스와 파티셔닝
4. **감사**: createdAt, updatedAt 모든 테이블 포함
5. **소프트 삭제**: 중요 데이터는 deletedAt 사용

### 데이터베이스

- **주 데이터베이스**: PostgreSQL 15+
- **캐시**: Redis 7+
- **검색 엔진**: Elasticsearch 8+

### 명명 규칙

- **테이블명**: snake_case, 복수형 (예: `users`, `products`)
- **컬럼명**: snake_case (예: `created_at`, `user_id`)
- **Primary Key**: `id` (UUID 또는 BIGINT)
- **Foreign Key**: `{table_name}_id` (예: `user_id`, `product_id`)

---

## ERD 다이어그램

### 전체 ERD
```mermaid
erDiagram
    %% ==================== 사용자 관련 ====================
    USERS ||--o{ ADDRESSES : "has"
    USERS ||--o{ SIZE_PROFILES : "has"
    USERS ||--o{ CARTS : "has"
    USERS ||--o{ ORDERS : "places"
    USERS ||--o{ USER_COUPONS : "has"
    USERS ||--o{ POINT_HISTORIES : "has"
    USERS ||--o{ REVIEWS : "writes"
    USERS ||--o{ RESTOCK_NOTIFICATIONS : "subscribes"
    
    %% ==================== 상품 관련 ====================
    PRODUCTS ||--o{ PRODUCT_VARIANTS : "has"
    PRODUCT_VARIANTS ||--|| INVENTORY : "tracks"
    PRODUCTS ||--o{ REVIEWS : "receives"
    
    %% ==================== 장바구니 관련 ====================
    CARTS ||--o{ CART_ITEMS : "contains"
    CART_ITEMS }o--|| PRODUCT_VARIANTS : "references"
    
    %% ==================== 주문 관련 ====================
    ORDERS ||--o{ ORDER_ITEMS : "contains"
    ORDERS ||--|| PAYMENTS : "has"
    ORDERS ||--|| SHIPMENTS : "has"
    ORDERS ||--o{ RETURNS : "has"
    ORDERS ||--o{ EXCHANGES : "has"
    ORDER_ITEMS }o--|| PRODUCT_VARIANTS : "references"
    
    %% ==================== 쿠폰 관련 ====================
    COUPONS ||--o{ USER_COUPONS : "issued_to"
    
    %% ==================== 리뷰 관련 ====================
    REVIEWS }o--|| ORDERS : "belongs_to"
    REVIEWS }o--|| ORDER_ITEMS : "reviews_item"

    %% ==================== 결제 보안 관련 (P0) ====================
    ORDERS ||--o{ RESERVATIONS : "has_reservation"
    PAYMENTS ||--o{ PAYMENT_FAILURES : "has_failure"
    ORDERS ||--o{ WEBHOOK_LOGS : "has_webhook"
    WEBHOOK_LOGS ||--o{ WEBHOOK_RETRY_QUEUE : "has_retry"

    %% ==================== 테이블 정의 ====================
    
    USERS {
        uuid id PK "회원 ID | UUID | DEFAULT uuid_generate_v4()"
        varchar email "이메일 | VARCHAR(255) | NOT NULL | UNIQUE"
        varchar password_hash "비밀번호 해시 | VARCHAR(255) | NOT NULL"
        varchar name "회원 이름 | VARCHAR(100) | NOT NULL"
        varchar phone "전화번호 | VARCHAR(20) | NULL"
        enum tier "회원 등급 | ENUM(GENERAL,VIP) | NOT NULL | Default: GENERAL"
        datetime tier_updated_at "등급 변경일 | DATETIME | NULL"
        varchar social_token "소셜 로그인 토큰 | VARCHAR(255) | NULL"
        varchar refresh_token "리프레시 토큰 | VARCHAR(255) | NULL"
        boolean agree_to_terms "약관 동의 | BOOLEAN | NOT NULL"
        boolean agree_to_marketing "마케팅 동의 | BOOLEAN | NOT NULL | Default: false"
        datetime created_at "생성일 | DATETIME | NOT NULL | DEFAULT NOW()"
        datetime updated_at "수정일 | DATETIME | NOT NULL | DEFAULT NOW()"
        datetime deleted_at "삭제일 | DATETIME | NULL"
    }
    
    ADDRESSES {
        bigint addressId PK "주소 ID | AUTO_INCREMENT"
        bigint userId FK "회원 ID | NOT NULL"
        varchar addressName "주소명 | VARCHAR(100) | NOT NULL"
        varchar phone "수령인 전화번호 | VARCHAR(20) | NOT NULL"
        varchar streetAddress "도로명 주소 | VARCHAR(255) | NOT NULL"
        varchar detailAddress "상세 주소 | VARCHAR(255) | NOT NULL"
        varchar postalCode "우편번호 | VARCHAR(10) | NOT NULL"
        varchar city "도시 | VARCHAR(50) | NOT NULL"
        varchar province "도/도 | VARCHAR(50) | NOT NULL"
        boolean isDefault "기본 배송지 여부 | BOOLEAN | Default: false"
        datetime regDate "생성일 | DATETIME | NOT NULL | CURRENT_TIMESTAMP"
        datetime modDate "수정일 | DATETIME | NULL | ON UPDATE CURRENT_TIMESTAMP"
    }
    
    SIZE_PROFILES {
        bigint profileId PK "프로필 ID | AUTO_INCREMENT"
        bigint userId FK "회원 ID | NOT NULL"
        decimal height "키(cm) | DECIMAL(5,2) | NULL"
        decimal weight "체중(kg) | DECIMAL(6,2) | NULL"
        varchar topSize "상의 사이즈 | VARCHAR(10) | NULL"
        varchar bottomSize "하의 사이즈 | VARCHAR(10) | NULL"
        decimal shoeSize "신발 사이즈 | DECIMAL(5,1) | NULL"
        enum preferredFit "선호 핏 | ENUM(SLIM,REGULAR,RELAXED) | NULL"
        enum bodyType "체형 | ENUM(SLIM,NORMAL,CURVY,MUSCULAR) | NULL"
        datetime regDate "생성일 | DATETIME | NOT NULL | CURRENT_TIMESTAMP"
        datetime modDate "수정일 | DATETIME | NULL | ON UPDATE CURRENT_TIMESTAMP"
    }
    
    PRODUCTS {
        bigint productId PK "상품 ID | AUTO_INCREMENT"
        varchar productName "상품명 | VARCHAR(255) | NOT NULL"
        varchar brand "브랜드 | VARCHAR(100) | NOT NULL"
        enum category "카테고리 | ENUM(TOP,BOTTOM,DRESS,OUTERWEAR,ACCESSORY,FOOTWEAR) | NOT NULL"
        text description "상품 설명 | TEXT | NULL"
        text material "소재 | TEXT | NULL"
        json careInstructions "세탁 방법 | JSON | NULL"
        bigint basePrice "정가 | BIGINT | NOT NULL"
        bigint salePrice "판매가 | BIGINT | NOT NULL"
        int discountRate "할인율(%) | INT | Default: 0"
        json images "이미지 URL 배열 | JSON | NULL"
        json tags "태그 배열 | JSON | NULL"
        decimal rating "평점(0-5) | DECIMAL(3,2) | Default: 0"
        int reviewCount "리뷰 개수 | INT | Default: 0"
        boolean isActive "판매 여부 | BOOLEAN | Default: true"
        datetime regDate "생성일 | DATETIME | NOT NULL | CURRENT_TIMESTAMP"
        datetime modDate "수정일 | DATETIME | NULL | ON UPDATE CURRENT_TIMESTAMP"
        datetime delDate "삭제일 | DATETIME | NULL"
    }
    
    PRODUCT_VARIANTS {
        bigint variantId PK "변형 ID | AUTO_INCREMENT"
        bigint productId FK "상품 ID | NOT NULL"
        varchar sku "상품코드(SKU) | VARCHAR(100) | NOT NULL | UNIQUE"
        varchar color "색상 | VARCHAR(50) | NOT NULL"
        varchar colorHex "색상코드 | VARCHAR(7) | NULL"
        varchar size "사이즈 | VARCHAR(20) | NOT NULL"
        enum length "길이 | ENUM(SHORT,REGULAR,LONG) | NULL"
        bigint price "가격 | BIGINT | NOT NULL"
        bigint originalPrice "원가 | BIGINT | NOT NULL"
        json images "변형 이미지 | JSON | NULL"
        boolean isActive "활성화 여부 | BOOLEAN | Default: true"
        datetime regDate "생성일 | DATETIME | NOT NULL | CURRENT_TIMESTAMP"
        datetime modDate "수정일 | DATETIME | NULL | ON UPDATE CURRENT_TIMESTAMP"
        datetime delDate "삭제일 | DATETIME | NULL"
    }
    
    INVENTORY {
        bigint inventoryId PK "재고 ID | AUTO_INCREMENT"
        varchar sku "상품코드(SKU) | VARCHAR(100) | NOT NULL | UNIQUE"
        int physicalStock "실제 재고 | INT | NOT NULL | Default: 0"
        int reservedStock "예약 재고 | INT | NOT NULL | Default: 0"
        int availableStock "가용 재고 | INT | NOT NULL | Default: 0"
        int safetyStock "안전 재고 | INT | NOT NULL | Default: 10"
        enum status "상태 | ENUM(IN_STOCK,LOW_STOCK,OUT_OF_STOCK) | NOT NULL"
        int reorderLevel "재주문 수준 | INT | Default: 20"
        int reorderQuantity "재주문 수량 | INT | Default: 100"
        datetime lastUpdated "마지막 업데이트 | DATETIME | NOT NULL | ON UPDATE CURRENT_TIMESTAMP"
        datetime regDate "생성일 | DATETIME | NOT NULL | CURRENT_TIMESTAMP"
        datetime modDate "수정일 | DATETIME | NULL | ON UPDATE CURRENT_TIMESTAMP"
    }
    
    CARTS {
        bigint cartId PK "장바구니 ID | AUTO_INCREMENT"
        bigint userId FK "회원 ID | NOT NULL"
        bigint totalPrice "총액 | BIGINT | NOT NULL | Default: 0"
        int itemCount "상품 수량 | INT | NOT NULL | Default: 0"
        datetime regDate "생성일 | DATETIME | NOT NULL | CURRENT_TIMESTAMP"
        datetime modDate "수정일 | DATETIME | NULL | ON UPDATE CURRENT_TIMESTAMP"
    }
    
    CART_ITEMS {
        bigint cartItemId PK "장바구니 항목 ID | AUTO_INCREMENT"
        bigint cartId FK "장바구니 ID | NOT NULL"
        bigint variantId FK "상품 변형 ID | NOT NULL"
        int quantity "수량 | INT | NOT NULL | Default: 1"
        bigint unitPrice "단가 | BIGINT | NOT NULL"
        bigint subtotal "소계 | BIGINT | NOT NULL"
        datetime regDate "생성일 | DATETIME | NOT NULL | CURRENT_TIMESTAMP"
        datetime modDate "수정일 | DATETIME | NULL | ON UPDATE CURRENT_TIMESTAMP"
    }
    
    ORDERS {
        bigint orderId PK "주문 ID | AUTO_INCREMENT"
        varchar orderNumber "주문번호 | VARCHAR(50) | NOT NULL | UNIQUE"
        bigint userId FK "회원 ID | NOT NULL"
        enum status "상태 | ENUM(PENDING_PAYMENT,PAID,PREPARING,SHIPPED,DELIVERED,CANCELLED,RETURN_REQUESTED,RETURN_COMPLETED,EXCHANGE_REQUESTED,EXCHANGE_COMPLETED) | NOT NULL | Default: PENDING_PAYMENT"
        json shippingAddress "배송 주소 | JSON | NOT NULL"
        enum shippingMethod "배송방법 | ENUM(standard,express,dawn) | NOT NULL"
        bigint shippingFee "배송료 | BIGINT | NOT NULL | Default: 0"
        varchar couponCode "쿠폰 코드 | VARCHAR(50) | NULL"
        bigint pointsUsed "사용 포인트 | BIGINT | NOT NULL | Default: 0"
        bigint subtotal "소계 | BIGINT | NOT NULL"
        bigint discount "할인액 | BIGINT | NOT NULL | Default: 0"
        bigint totalAmount "총액 | BIGINT | NOT NULL"
        varchar requestMessage "배송 요청사항 | VARCHAR(255) | NULL"
        datetime reservationExpiry "예약 만료 | DATETIME | NULL"
        datetime created_at "생성일 | DATETIME | NOT NULL | DEFAULT NOW()"
        datetime updated_at "수정일 | DATETIME | NOT NULL | DEFAULT NOW()"
    }
    
    ORDER_ITEMS {
        bigint orderItemId PK "주문 항목 ID | AUTO_INCREMENT"
        bigint orderId FK "주문 ID | NOT NULL"
        bigint productId FK "상품 ID | NOT NULL"
        bigint variantId FK "상품 변형 ID | NOT NULL"
        int quantity "수량 | INT | NOT NULL"
        bigint unitPrice "단가 | BIGINT | NOT NULL"
        bigint subtotal "소계 | BIGINT | NOT NULL"
        json productSnapshot "상품 스냅샷 | JSON | NOT NULL"
        enum reviewStatus "리뷰 상태 | ENUM(PENDING,REVIEWED,REVIEWABLE) | Default: PENDING"
        datetime regDate "생성일 | DATETIME | NOT NULL | CURRENT_TIMESTAMP"
        datetime modDate "수정일 | DATETIME | NULL | ON UPDATE CURRENT_TIMESTAMP"
    }
    
    PAYMENTS {
        bigint paymentId PK "결제 ID | AUTO_INCREMENT"
        bigint orderId FK "주문 ID | NOT NULL"
        varchar idempotency_key "멱등성 키 | VARCHAR(255) | NOT NULL"
        enum method "결제방법 | ENUM(CARD,BANK_TRANSFER,PAYPAL,APPLE_PAY) | NOT NULL"
        enum status "상태 | ENUM(PENDING,APPROVED,DECLINED,REFUNDED,CANCELLED) | NOT NULL"
        bigint amount "금액 | BIGINT | NOT NULL"
        varchar transactionId "거래 ID | VARCHAR(100) | NULL"
        varchar pgCode "결제게이트웨이 코드 | VARCHAR(100) | NULL"
        text failReason "실패 사유 | TEXT | NULL"
        datetime approvedAt "승인일시 | DATETIME | NULL"
        datetime created_at "생성일 | DATETIME | NOT NULL | DEFAULT NOW()"
        datetime updated_at "수정일 | DATETIME | NOT NULL | DEFAULT NOW()"
    }
    
    SHIPMENTS {
        bigint shipmentId PK "배송 ID | AUTO_INCREMENT"
        bigint orderId FK "주문 ID | NOT NULL"
        varchar trackingNumber "추적번호 | VARCHAR(100) | NOT NULL"
        varchar carrier "배송사 | VARCHAR(50) | NOT NULL"
        enum status "상태 | ENUM(PREPARING,SHIPPED,IN_TRANSIT,DELIVERED,FAILED) | NOT NULL"
        date estimatedDelivery "예상 배송일 | DATE | NULL"
        json events "배송 이벤트 | JSON | NULL"
        datetime shippedAt "발송일시 | DATETIME | NULL"
        datetime regDate "생성일 | DATETIME | NOT NULL | CURRENT_TIMESTAMP"
        datetime modDate "수정일 | DATETIME | NULL | ON UPDATE CURRENT_TIMESTAMP"
    }
    
    RETURNS {
        bigint returnId PK "반품 ID | AUTO_INCREMENT"
        bigint orderId FK "주문 ID | NOT NULL"
        enum status "상태 | ENUM(REQUESTED,APPROVED,REJECTED,RETURNED,REFUNDED) | NOT NULL"
        json items "반품 상품 목록 | JSON | NOT NULL"
        enum reason "반품사유 | ENUM(DEFECTIVE,NOT_AS_DESCRIBED,WRONG_SIZE,CHANGED_MIND) | NOT NULL"
        text detailReason "상세 사유 | TEXT | NULL"
        json images "반품 사진 | JSON | NULL"
        enum refundMethod "환불방법 | ENUM(ORIGINAL,NEW_CARD,BANK_TRANSFER) | NOT NULL"
        json bankAccount "계좌 정보 | JSON | NULL"
        bigint returnShippingFee "반품 배송료 | BIGINT | NOT NULL | Default: 0"
        enum feePaymentBy "배송료 부담 | ENUM(CUSTOMER,SELLER) | NOT NULL"
        bigint expectedRefund "예상 환불액 | BIGINT | NOT NULL"
        json timeline "반품 타임라인 | JSON | NULL"
        datetime regDate "생성일 | DATETIME | NOT NULL | CURRENT_TIMESTAMP"
        datetime modDate "수정일 | DATETIME | NULL | ON UPDATE CURRENT_TIMESTAMP"
    }
    
    EXCHANGES {
        bigint exchangeId PK "교환 ID | AUTO_INCREMENT"
        bigint orderId FK "주문 ID | NOT NULL"
        enum status "상태 | ENUM(REQUESTED,APPROVED,REJECTED,SHIPPED,DELIVERED) | NOT NULL"
        json items "교환 상품 목록 | JSON | NOT NULL"
        enum stockStatus "재고상태 | ENUM(AVAILABLE,UNAVAILABLE,PARTIAL) | NOT NULL"
        bigint exchangeShippingFee "교환 배송료 | BIGINT | NOT NULL | Default: 0"
        enum feePaymentBy "배송료 부담 | ENUM(CUSTOMER,SELLER) | NOT NULL"
        json timeline "교환 타임라인 | JSON | NULL"
        datetime regDate "생성일 | DATETIME | NOT NULL | CURRENT_TIMESTAMP"
        datetime modDate "수정일 | DATETIME | NULL | ON UPDATE CURRENT_TIMESTAMP"
    }
    
    COUPONS {
        bigint couponId PK "쿠폰 ID | AUTO_INCREMENT"
        varchar code "쿠폰 코드 | VARCHAR(50) | NOT NULL | UNIQUE"
        varchar name "쿠폰명 | VARCHAR(255) | NOT NULL"
        enum type "타입 | ENUM(PERCENTAGE,FIXED_AMOUNT,FREE_SHIPPING) | NOT NULL"
        bigint discount "할인액/할인율 | BIGINT | NOT NULL"
        bigint minOrderAmount "최소 주문액 | BIGINT | Default: 0"
        bigint maxDiscountAmount "최대 할인액 | BIGINT | NULL"
        int maxIssueCount "최대 발급 수 | INT | NOT NULL"
        int issuedCount "발급된 수 | INT | Default: 0"
        int maxPerUser "사용자당 최대 사용 횟수 | INT | Default: 1"
        datetime validFrom "유효 시작일 | DATETIME | NOT NULL"
        datetime validUntil "유효 종료일 | DATETIME | NOT NULL"
        boolean isActive "활성화 여부 | BOOLEAN | Default: true"
        datetime regDate "생성일 | DATETIME | NOT NULL | CURRENT_TIMESTAMP"
        datetime modDate "수정일 | DATETIME | NULL | ON UPDATE CURRENT_TIMESTAMP"
    }
    
    USER_COUPONS {
        bigint userCouponId PK "사용자 쿠폰 ID | AUTO_INCREMENT"
        bigint userId FK "회원 ID | NOT NULL"
        bigint couponId FK "쿠폰 ID | NOT NULL"
        enum status "상태 | ENUM(AVAILABLE,USED,EXPIRED) | NOT NULL | Default: AVAILABLE"
        datetime usedAt "사용일시 | DATETIME | NULL"
        datetime regDate "생성일 | DATETIME | NOT NULL | CURRENT_TIMESTAMP"
        datetime modDate "수정일 | DATETIME | NULL | ON UPDATE CURRENT_TIMESTAMP"
    }
    
    POINT_HISTORIES {
        bigint pointHistoryId PK "포인트 이력 ID | AUTO_INCREMENT"
        bigint userId FK "회원 ID | NOT NULL"
        enum type "타입 | ENUM(EARNED,REDEEMED,EXPIRED,ADJUSTED) | NOT NULL"
        bigint amount "포인트 | BIGINT | NOT NULL"
        bigint balanceAfter "포인트 잔액 | BIGINT | NOT NULL"
        varchar description "설명 | VARCHAR(255) | NOT NULL"
        varchar referenceId "참조 ID(주문번호 등) | VARCHAR(100) | NULL"
        date expiryDate "만료일 | DATE | NULL"
        datetime regDate "생성일 | DATETIME | NOT NULL | CURRENT_TIMESTAMP"
    }
    
    REVIEWS {
        bigint reviewId PK "리뷰 ID | AUTO_INCREMENT"
        bigint userId FK "회원 ID | NOT NULL"
        bigint productId FK "상품 ID | NOT NULL"
        bigint orderId FK "주문 ID | NOT NULL"
        bigint orderItemId FK "주문 항목 ID | NOT NULL"
        int rating "평점(1-5) | INT | NOT NULL"
        varchar title "제목 | VARCHAR(255) | NOT NULL"
        text content "내용 | TEXT | NOT NULL"
        json images "리뷰 사진 | JSON | NULL"
        enum sizeRating "사이즈 평가 | ENUM(TOO_SMALL,FITS_WELL,TOO_LARGE) | NULL"
        int helpfulCount "도움이 됨 수 | INT | Default: 0"
        boolean isVerifiedPurchase "구매 확인 리뷰 여부 | BOOLEAN | Default: true"
        datetime regDate "생성일 | DATETIME | NOT NULL | CURRENT_TIMESTAMP"
        datetime modDate "수정일 | DATETIME | NULL | ON UPDATE CURRENT_TIMESTAMP"
        datetime delDate "삭제일 | DATETIME | NULL"
    }
    
    RESTOCK_NOTIFICATIONS {
        bigint notificationId PK "알림 ID | AUTO_INCREMENT"
        bigint userId FK "회원 ID | NOT NULL"
        varchar sku "상품코드(SKU) | VARCHAR(100) | NOT NULL"
        json notifyVia "알림 채널 | JSON | NOT NULL"
        enum status "상태 | ENUM(ACTIVE,NOTIFIED,CANCELLED) | NOT NULL | Default: ACTIVE"
        datetime notifiedAt "알림 발송일시 | DATETIME | NULL"
        datetime regDate "생성일 | DATETIME | NOT NULL | CURRENT_TIMESTAMP"
        datetime modDate "수정일 | DATETIME | NULL | ON UPDATE CURRENT_TIMESTAMP"
    }

    %% ==================== P0 CRITICAL 테이블 ====================
    RESERVATIONS {
        uuid id PK "예약 ID | UUID | DEFAULT uuid_generate_v4()"
        uuid order_id FK "주문 ID | UUID | NOT NULL"
        varchar sku "상품코드(SKU) | VARCHAR(100) | NOT NULL"
        int quantity "예약 수량 | INT | NOT NULL"
        enum status "상태 | ENUM(ACTIVE,CONFIRMED,EXPIRED,CANCELLED) | NOT NULL"
        datetime expires_at "만료 시간 | DATETIME | NOT NULL"
        datetime created_at "생성일 | DATETIME | NOT NULL | DEFAULT NOW()"
        datetime updated_at "수정일 | DATETIME | NOT NULL | DEFAULT NOW()"
    }

    PAYMENT_FAILURES {
        uuid id PK "실패 기록 ID | UUID | DEFAULT uuid_generate_v4()"
        uuid order_id FK "주문 ID | UUID | NOT NULL"
        uuid payment_id FK "결제 ID | UUID | NOT NULL"
        varchar reason "실패 사유 | VARCHAR(255) | NOT NULL"
        varchar pg_code "PG사 응답 코드 | VARCHAR(100) | NULL"
        enum compensation_status "보상 상태 | ENUM(PENDING,COMPENSATED,FAILED) | NOT NULL"
        text compensation_reason "보상 사유 | TEXT | NULL"
        datetime created_at "생성일 | DATETIME | NOT NULL | DEFAULT NOW()"
        datetime updated_at "수정일 | DATETIME | NOT NULL | DEFAULT NOW()"
    }

    WEBHOOK_LOGS {
        uuid id PK "웹훅 로그 ID | UUID | DEFAULT uuid_generate_v4()"
        varchar event_id "이벤트 ID | VARCHAR(255) | NOT NULL | UNIQUE"
        varchar event_type "이벤트 타입 | VARCHAR(100) | NOT NULL"
        uuid order_id FK "주문 ID | UUID | NULL"
        jsonb payload "전체 페이로드 | JSONB | NOT NULL"
        enum status "상태 | ENUM(PROCESSING,QUEUED,COMPLETED,FAILED) | NOT NULL"
        text error_message "에러 메시지 | TEXT | NULL"
        int retry_count "재시도 횟수 | INT | Default: 0"
        datetime processed_at "처리 완료일 | DATETIME | NULL"
        datetime created_at "생성일 | DATETIME | NOT NULL | DEFAULT NOW()"
        datetime updated_at "수정일 | DATETIME | NOT NULL | DEFAULT NOW()"
    }

    WEBHOOK_RETRY_QUEUE {
        uuid id PK "재시도 큐 ID | UUID | DEFAULT uuid_generate_v4()"
        varchar event_id FK "이벤트 ID | VARCHAR(255) | NOT NULL"
        int retry_count "현재 재시도 횟수 | INT | NOT NULL | Default: 0"
        int max_retries "최대 재시도 횟수 | INT | NOT NULL | Default: 3"
        datetime next_retry_at "다음 재시도 시간 | DATETIME | NOT NULL"
        datetime created_at "생성일 | DATETIME | NOT NULL | DEFAULT NOW()"
        datetime updated_at "수정일 | DATETIME | NOT NULL | DEFAULT NOW()"
    }
```

---

## 엔티티 상세 설계

### 1. users (사용자)

**설명**: 회원 정보를 저장합니다.

| 컬럼명 | 타입 | 제약 | 기본값 | 설명 |
|--------|------|------|--------|------|
| id | UUID | PK | uuid_generate_v4() | 사용자 ID |
| email | VARCHAR(255) | UNIQUE, NOT NULL | - | 이메일 (로그인 ID) |
| password_hash | VARCHAR(255) | NOT NULL | - | 비밀번호 해시 (bcrypt) |
| name | VARCHAR(100) | NOT NULL | - | 이름 |
| phone | VARCHAR(20) | NOT NULL | - | 전화번호 |
| tier | ENUM | NOT NULL | 'GENERAL' | 회원 등급 |
| tier_updated_at | TIMESTAMP | NULL | - | 등급 변경 일시 |
| agree_to_terms | BOOLEAN | NOT NULL | - | 이용약관 동의 |
| agree_to_marketing | BOOLEAN | NOT NULL | false | 마케팅 수신 동의 |
| created_at | TIMESTAMP | NOT NULL | NOW() | 생성 일시 |
| updated_at | TIMESTAMP | NOT NULL | NOW() | 수정 일시 |
| deleted_at | TIMESTAMP | NULL | - | 삭제 일시 (소프트 삭제) |

**ENUM 타입:**
- `tier`: `'GENERAL'`, `'VIP'`

**인덱스:**
```sql
CREATE UNIQUE INDEX idx_users_email ON users(email) WHERE deleted_at IS NULL;
CREATE INDEX idx_users_tier ON users(tier);
CREATE INDEX idx_users_created_at ON users(created_at);
```

**비즈니스 규칙:**
- 이메일 중복 불가 (소프트 삭제된 경우 제외)
- VIP 조건: 최근 6개월 구매 금액 100만원 이상
- 비밀번호: 최소 8자, bcrypt 해싱

---

### 2. addresses (배송지)

**설명**: 사용자의 배송지 정보를 저장합니다.

| 컬럼명 | 타입 | 제약 | 기본값 | 설명 |
|--------|------|------|--------|------|
| id | UUID | PK | uuid_generate_v4() | 배송지 ID |
| user_id | UUID | FK, NOT NULL | - | 사용자 ID |
| name | VARCHAR(100) | NOT NULL | - | 수령인 이름 |
| phone | VARCHAR(20) | NOT NULL | - | 전화번호 |
| address | VARCHAR(500) | NOT NULL | - | 기본 주소 |
| address_detail | VARCHAR(200) | NULL | - | 상세 주소 |
| zip_code | VARCHAR(10) | NOT NULL | - | 우편번호 |
| is_default | BOOLEAN | NOT NULL | false | 기본 배송지 여부 |
| created_at | TIMESTAMP | NOT NULL | NOW() | 생성 일시 |
| updated_at | TIMESTAMP | NOT NULL | NOW() | 수정 일시 |

**외래 키:**
```sql
FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
```

**인덱스:**
```sql
CREATE INDEX idx_addresses_user_id ON addresses(user_id);
CREATE INDEX idx_addresses_is_default ON addresses(user_id, is_default);
```

**비즈니스 규칙:**
- 사용자당 기본 배송지는 1개만 가능
- 새 기본 배송지 설정 시 기존 기본 배송지 해제

---

### 3. size_profiles (사이즈 프로필)

**설명**: 사용자의 신체 정보 및 사이즈 선호도를 저장합니다.

| 컬럼명 | 타입 | 제약 | 기본값 | 설명 |
|--------|------|------|--------|------|
| id | UUID | PK | uuid_generate_v4() | 프로필 ID |
| user_id | UUID | FK, UNIQUE, NOT NULL | - | 사용자 ID |
| height | DECIMAL(5,2) | NULL | - | 키 (cm) |
| weight | DECIMAL(5,2) | NULL | - | 몸무게 (kg) |
| top_size | VARCHAR(10) | NULL | - | 상의 사이즈 |
| bottom_size | VARCHAR(10) | NULL | - | 하의 사이즈 |
| shoe_size | DECIMAL(4,1) | NULL | - | 신발 사이즈 (mm) |
| preferred_fit | ENUM | NULL | - | 선호 핏 |
| body_type | ENUM | NULL | - | 체형 |
| created_at | TIMESTAMP | NOT NULL | NOW() | 생성 일시 |
| updated_at | TIMESTAMP | NOT NULL | NOW() | 수정 일시 |

**ENUM 타입:**
- `preferred_fit`: `'TIGHT'`, `'SLIM'`, `'REGULAR'`, `'LOOSE'`
- `body_type`: `'SLIM'`, `'ATHLETIC'`, `'AVERAGE'`, `'MUSCULAR'`, `'HEAVY'`

**외래 키:**
```sql
FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
```

**인덱스:**
```sql
CREATE UNIQUE INDEX idx_size_profiles_user_id ON size_profiles(user_id);
```

**비즈니스 규칙:**
- 사용자당 1개의 사이즈 프로필
- AI 사이즈 추천에 활용

---

### 4. products (상품)

**설명**: 상품의 기본 정보를 저장합니다.

| 컬럼명 | 타입 | 제약 | 기본값 | 설명 |
|--------|------|------|--------|------|
| id | UUID | PK | uuid_generate_v4() | 상품 ID |
| name | VARCHAR(255) | NOT NULL | - | 상품명 |
| brand | VARCHAR(100) | NOT NULL | - | 브랜드 |
| category | ENUM | NOT NULL | - | 카테고리 |
| description | TEXT | NULL | - | 상품 설명 |
| material | VARCHAR(255) | NULL | - | 소재 |
| care_instructions | JSONB | NULL | - | 세탁 방법 (배열) |
| base_price | BIGINT | NOT NULL | - | 정가 (원) |
| sale_price | BIGINT | NOT NULL | - | 판매가 (원) |
| discount_rate | INT | NOT NULL | 0 | 할인율 (%) |
| images | JSONB | NOT NULL | - | 이미지 URL 배열 |
| tags | JSONB | NULL | - | 태그 배열 |
| rating | DECIMAL(3,2) | NULL | 0.00 | 평균 평점 |
| review_count | INT | NOT NULL | 0 | 리뷰 수 |
| created_at | TIMESTAMP | NOT NULL | NOW() | 생성 일시 |
| updated_at | TIMESTAMP | NOT NULL | NOW() | 수정 일시 |
| deleted_at | TIMESTAMP | NULL | - | 삭제 일시 |

**ENUM 타입:**
- `category`: `'tops'`, `'bottoms'`, `'outerwear'`, `'dresses'`, `'shoes'`, `'accessories'`

**인덱스:**
```sql
CREATE INDEX idx_products_brand ON products(brand);
CREATE INDEX idx_products_category ON products(category);
CREATE INDEX idx_products_sale_price ON products(sale_price);
CREATE INDEX idx_products_rating ON products(rating DESC);
CREATE INDEX idx_products_created_at ON products(created_at DESC);
CREATE INDEX idx_products_deleted_at ON products(deleted_at) WHERE deleted_at IS NULL;
```

**비즈니스 규칙:**
- 할인율 = ((정가 - 판매가) / 정가) × 100
- 이미지 최소 1개 필수
- Elasticsearch에 동기화 (검색용)

---

### 5. product_variants (상품 변량/SKU)

**설명**: 상품의 색상, 사이즈 등 변량 정보를 저장합니다.

| 컬럼명 | 타입 | 제약 | 기본값 | 설명 |
|--------|------|------|--------|------|
| id | UUID | PK | uuid_generate_v4() | 변량 ID |
| product_id | UUID | FK, NOT NULL | - | 상품 ID |
| sku | VARCHAR(100) | UNIQUE, NOT NULL | - | SKU 코드 |
| color | VARCHAR(50) | NOT NULL | - | 색상명 |
| color_hex | VARCHAR(7) | NOT NULL | - | 색상 HEX 코드 |
| size | VARCHAR(10) | NOT NULL | - | 사이즈 |
| length | ENUM | NULL | 'regular' | 길이 |
| price | BIGINT | NOT NULL | - | 판매가 |
| original_price | BIGINT | NOT NULL | - | 정가 |
| images | JSONB | NULL | - | 변량별 이미지 |
| created_at | TIMESTAMP | NOT NULL | NOW() | 생성 일시 |
| updated_at | TIMESTAMP | NOT NULL | NOW() | 수정 일시 |
| deleted_at | TIMESTAMP | NULL | - | 삭제 일시 |

**ENUM 타입:**
- `length`: `'regular'`, `'short'`, `'long'`

**외래 키:**
```sql
FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
```

**인덱스:**
```sql
CREATE UNIQUE INDEX idx_variants_sku ON product_variants(sku) WHERE deleted_at IS NULL;
CREATE INDEX idx_variants_product_id ON product_variants(product_id);
CREATE INDEX idx_variants_color_size ON product_variants(color, size);
```

**비즈니스 규칙:**
- SKU 코드 형식: `{브랜드코드}-{상품코드}-{색상코드}-{사이즈}-{길이}`
    - 예: `LEVI-501-BLK-32-REG`
- 하나의 상품은 여러 변량을 가짐 (1:N)

---

### 6. inventory (재고)

**설명**: SKU별 재고 정보를 실시간으로 관리합니다.

| 컬럼명 | 타입 | 제약 | 기본값 | 설명 |
|--------|------|------|--------|------|
| id | UUID | PK | uuid_generate_v4() | 재고 ID |
| sku | VARCHAR(100) | UNIQUE, NOT NULL | - | SKU 코드 |
| physical_stock | INT | NOT NULL | 0 | 물리적 재고 |
| reserved_stock | INT | NOT NULL | 0 | 예약 재고 |
| available_stock | INT | NOT NULL | 0 | 가용 재고 |
| safety_stock | INT | NOT NULL | 0 | 안전 재고 |
| status | ENUM | NOT NULL | - | 재고 상태 |
| last_updated | TIMESTAMP | NOT NULL | NOW() | 최종 업데이트 |
| created_at | TIMESTAMP | NOT NULL | NOW() | 생성 일시 |
| updated_at | TIMESTAMP | NOT NULL | NOW() | 수정 일시 |

**ENUM 타입:**
- `status`: `'IN_STOCK'`, `'LOW_STOCK'`, `'OUT_OF_STOCK'`

**인덱스:**
```sql
CREATE UNIQUE INDEX idx_inventory_sku ON inventory(sku);
CREATE INDEX idx_inventory_status ON inventory(status);
CREATE INDEX idx_inventory_available ON inventory(available_stock);
```

**비즈니스 규칙:**
- **재고 계산**: `available_stock = physical_stock - reserved_stock - safety_stock`
- **재고 상태**:
    - `IN_STOCK`: available_stock > 5
    - `LOW_STOCK`: 1 <= available_stock <= 5
    - `OUT_OF_STOCK`: available_stock <= 0
- **동시성 제어**: Pessimistic Lock 또는 Redis 분산 락
- **재고 변경 로그**: 별도 `inventory_logs` 테이블에 기록

---

### 7. carts (장바구니)

**설명**: 사용자의 장바구니 정보를 저장합니다.

| 컬럼명 | 타입 | 제약 | 기본값 | 설명 |
|--------|------|------|--------|------|
| id | UUID | PK | uuid_generate_v4() | 장바구니 ID |
| user_id | UUID | FK, UNIQUE, NOT NULL | - | 사용자 ID |
| created_at | TIMESTAMP | NOT NULL | NOW() | 생성 일시 |
| updated_at | TIMESTAMP | NOT NULL | NOW() | 수정 일시 |

**외래 키:**
```sql
FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
```

**인덱스:**
```sql
CREATE UNIQUE INDEX idx_carts_user_id ON carts(user_id);
```

**비즈니스 규칙:**
- 사용자당 1개의 장바구니
- 회원가입 시 자동 생성

---

### 8. cart_items (장바구니 항목)

**설명**: 장바구니에 담긴 상품 정보를 저장합니다.

| 컬럼명 | 타입 | 제약 | 기본값 | 설명 |
|--------|------|------|--------|------|
| id | UUID | PK | uuid_generate_v4() | 항목 ID |
| cart_id | UUID | FK, NOT NULL | - | 장바구니 ID |
| variant_id | UUID | FK, NOT NULL | - | 변량 ID |
| quantity | INT | NOT NULL | 1 | 수량 |
| price | BIGINT | NOT NULL | - | 담은 시점 가격 |
| created_at | TIMESTAMP | NOT NULL | NOW() | 생성 일시 |
| updated_at | TIMESTAMP | NOT NULL | NOW() | 수정 일시 |

**외래 키:**
```sql
FOREIGN KEY (cart_id) REFERENCES carts(id) ON DELETE CASCADE
FOREIGN KEY (variant_id) REFERENCES product_variants(id) ON DELETE CASCADE
```

**인덱스:**
```sql
CREATE INDEX idx_cart_items_cart_id ON cart_items(cart_id);
CREATE INDEX idx_cart_items_variant_id ON cart_items(variant_id);
CREATE UNIQUE INDEX idx_cart_items_cart_variant ON cart_items(cart_id, variant_id);
```

**비즈니스 규칙:**
- 동일 상품 중복 추가 시 수량 증가
- 수량 제한: 1~99
- 가격은 담은 시점 기준 (나중에 변동 가능)

---

### 9. orders (주문)

**설명**: 주문 정보를 저장합니다.

| 컬럼명 | 타입 | 제약 | 기본값 | 설명 |
|--------|------|------|--------|------|
| id | UUID | PK | uuid_generate_v4() | 주문 ID |
| order_number | VARCHAR(50) | UNIQUE, NOT NULL | - | 주문번호 |
| user_id | UUID | FK, NOT NULL | - | 사용자 ID |
| status | ENUM | NOT NULL | 'PENDING_PAYMENT' | 주문 상태 |
| shipping_address | JSONB | NOT NULL | - | 배송지 정보 |
| shipping_method | ENUM | NOT NULL | - | 배송 방법 |
| shipping_fee | BIGINT | NOT NULL | - | 배송비 |
| coupon_code | VARCHAR(50) | NULL | - | 쿠폰 코드 |
| points_used | BIGINT | NOT NULL | 0 | 사용 포인트 |
| subtotal | BIGINT | NOT NULL | - | 상품 금액 |
| discount | BIGINT | NOT NULL | 0 | 할인 금액 |
| total_amount | BIGINT | NOT NULL | - | 최종 금액 |
| request_message | VARCHAR(200) | NULL | - | 배송 요청사항 |
| reservation_expiry | TIMESTAMP | NULL | - | 재고 예약 만료 시간 |
| created_at | TIMESTAMP | NOT NULL | NOW() | 생성 일시 |
| updated_at | TIMESTAMP | NOT NULL | NOW() | 수정 일시 |

**ENUM 타입:**
- `status`: `'PENDING_PAYMENT'`, `'PAID'`, `'PREPARING'`, `'SHIPPED'`, `'DELIVERED'`, `'CANCELLED'`, `'RETURN_REQUESTED'`, `'RETURN_COMPLETED'`, `'EXCHANGE_REQUESTED'`, `'EXCHANGE_COMPLETED'`
- `shipping_method`: `'standard'`, `'express'`, `'dawn'`

**외래 키:**
```sql
FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT
```

**인덱스:**
```sql
CREATE UNIQUE INDEX idx_orders_order_number ON orders(order_number);
CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_created_at ON orders(created_at DESC);
CREATE INDEX idx_orders_reservation_expiry ON orders(reservation_expiry) WHERE reservation_expiry IS NOT NULL;
```

**비즈니스 규칙:**
- 주문번호 형식: `YYYYMMDD{순번}` (예: 2024031500123)
- 재고 예약 TTL: 15분 (주문 생성 시점 + 15분)
- 최종 금액: `subtotal - discount - points_used + shipping_fee`
- 결제 완료 후 상태 변경: PENDING_PAYMENT → PAID

---

### 10. order_items (주문 항목)

**설명**: 주문에 포함된 상품 정보를 저장합니다.

| 컬럼명 | 타입 | 제약 | 기본값 | 설명 |
|--------|------|------|--------|------|
| id | UUID | PK | uuid_generate_v4() | 항목 ID |
| order_id | UUID | FK, NOT NULL | - | 주문 ID |
| product_id | UUID | FK, NOT NULL | - | 상품 ID |
| variant_id | UUID | FK, NOT NULL | - | 변량 ID |
| quantity | INT | NOT NULL | - | 수량 |
| price | BIGINT | NOT NULL | - | 주문 시점 가격 |
| subtotal | BIGINT | NOT NULL | - | 소계 |
| product_snapshot | JSONB | NOT NULL | - | 상품 정보 스냅샷 |
| created_at | TIMESTAMP | NOT NULL | NOW() | 생성 일시 |
| updated_at | TIMESTAMP | NOT NULL | NOW() | 수정 일시 |

**외래 키:**
```sql
FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT
FOREIGN KEY (variant_id) REFERENCES product_variants(id) ON DELETE RESTRICT
```

**인덱스:**
```sql
CREATE INDEX idx_order_items_order_id ON order_items(order_id);
CREATE INDEX idx_order_items_product_id ON order_items(product_id);
CREATE INDEX idx_order_items_variant_id ON order_items(variant_id);
```

**비즈니스 규칙:**
- 소계: `price × quantity`
- **스냅샷**: 주문 시점 상품 정보 저장 (이후 상품 수정에 영향 없음)
    - 상품명, 브랜드, 색상, 사이즈, 이미지 등

---

### 11. payments (결제)

**설명**: 결제 정보를 저장합니다.

| 컬럼명 | 타입 | 제약 | 기본값 | 설명 |
|--------|------|------|--------|------|
| id | UUID | PK | uuid_generate_v4() | 결제 ID |
| order_id | UUID | FK, UNIQUE, NOT NULL | - | 주문 ID |
| idempotency_key | VARCHAR(255) | NOT NULL | - | 멱등성 키 (중복 결제 방지) |
| method | ENUM | NOT NULL | - | 결제 수단 |
| status | ENUM | NOT NULL | 'PENDING' | 결제 상태 |
| amount | BIGINT | NOT NULL | - | 결제 금액 |
| transaction_id | VARCHAR(255) | NULL | - | PG사 거래 ID |
| pg_code | VARCHAR(50) | NULL | - | PG사 응답 코드 |
| fail_reason | TEXT | NULL | - | 실패 사유 |
| approved_at | TIMESTAMP | NULL | - | 승인 일시 |
| created_at | TIMESTAMP | NOT NULL | NOW() | 생성 일시 |
| updated_at | TIMESTAMP | NOT NULL | NOW() | 수정 일시 |

**ENUM 타입:**
- `method`: `'CARD'`, `'TRANSFER'`, `'VIRTUAL_ACCOUNT'`, `'KAKAO_PAY'`, `'NAVER_PAY'`
- `status`: `'PENDING'`, `'APPROVED'`, `'FAILED'`, `'CANCELLED'`, `'REFUNDED'`

**외래 키:**
```sql
FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT
```

**인덱스:**
```sql
CREATE UNIQUE INDEX idx_payments_order_id ON payments(order_id);
CREATE UNIQUE INDEX idx_payments_idempotency ON payments(idempotency_key);
CREATE INDEX idx_payments_status ON payments(status);
CREATE INDEX idx_payments_transaction_id ON payments(transaction_id);
CREATE INDEX idx_payments_approved_at ON payments(approved_at);
```

**비즈니스 규칙:**
- 주문당 1개의 결제
- **멱등성 키**: 클라이언트가 요청 시 제공 (UUID 또는 특정 형식)
- **중복 결제 방지**: idempotency_key + order_id UNIQUE 제약으로 동일 결제 중복 방지
- 카드번호 저장 금지 (PG사 토큰 사용)
- 승인 성공 시 재고 실차감
- 동일 idempotency_key로 재요청 시 캐시된 결과 반환 (멱등성 보장)

---

### 12. shipments (배송)

**설명**: 배송 정보를 저장합니다.

| 컬럼명 | 타입 | 제약 | 기본값 | 설명 |
|--------|------|------|--------|------|
| id | UUID | PK | uuid_generate_v4() | 배송 ID |
| order_id | UUID | FK, UNIQUE, NOT NULL | - | 주문 ID |
| tracking_number | VARCHAR(50) | NULL | - | 송장번호 |
| carrier | VARCHAR(50) | NULL | - | 택배사 |
| status | ENUM | NOT NULL | 'PREPARING' | 배송 상태 |
| estimated_delivery | DATE | NULL | - | 예상 배송일 |
| events | JSONB | NULL | - | 배송 이벤트 타임라인 |
| created_at | TIMESTAMP | NOT NULL | NOW() | 생성 일시 |
| updated_at | TIMESTAMP | NOT NULL | NOW() | 수정 일시 |

**ENUM 타입:**
- `status`: `'PREPARING'`, `'PICKED_UP'`, `'IN_TRANSIT'`, `'OUT_FOR_DELIVERY'`, `'DELIVERED'`

**외래 키:**
```sql
FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT
```

**인덱스:**
```sql
CREATE UNIQUE INDEX idx_shipments_order_id ON shipments(order_id);
CREATE INDEX idx_shipments_tracking_number ON shipments(tracking_number);
CREATE INDEX idx_shipments_status ON shipments(status);
```

**비즈니스 규칙:**
- 주문당 1개의 배송
- 택배사 API 연동으로 실시간 추적

---

### 13. returns (반품)

**설명**: 반품 정보를 저장합니다.

| 컬럼명 | 타입 | 제약 | 기본값 | 설명 |
|--------|------|------|--------|------|
| id | UUID | PK | uuid_generate_v4() | 반품 ID |
| order_id | UUID | FK, NOT NULL | - | 주문 ID |
| status | ENUM | NOT NULL | 'REQUESTED' | 반품 상태 |
| items | JSONB | NOT NULL | - | 반품 항목 정보 |
| reason | ENUM | NOT NULL | - | 반품 사유 |
| detail_reason | TEXT | NULL | - | 상세 사유 |
| images | JSONB | NULL | - | 불량 사진 |
| refund_method | ENUM | NOT NULL | - | 환불 방법 |
| bank_account | JSONB | NULL | - | 환불 계좌 정보 |
| return_shipping_fee | BIGINT | NOT NULL | - | 반품 배송비 |
| fee_payment_by | ENUM | NOT NULL | - | 배송비 부담 주체 |
| expected_refund | BIGINT | NOT NULL | - | 예상 환불 금액 |
| timeline | JSONB | NULL | - | 반품 진행 타임라인 |
| created_at | TIMESTAMP | NOT NULL | NOW() | 생성 일시 |
| updated_at | TIMESTAMP | NOT NULL | NOW() | 수정 일시 |

**ENUM 타입:**
- `status`: `'REQUESTED'`, `'APPROVED'`, `'REJECTED'`, `'SHIPPING'`, `'RECEIVED'`, `'INSPECTING'`, `'COMPLETED'`
- `reason`: `'SIZE_ISSUE'`, `'DEFECTIVE'`, `'WRONG_ITEM'`, `'NOT_AS_DESCRIBED'`, `'CHANGE_OF_MIND'`
- `refund_method`: `'ORIGINAL'`, `'ACCOUNT'`
- `fee_payment_by`: `'CUSTOMER'`, `'SELLER'`

**외래 키:**
```sql
FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT
```

**인덱스:**
```sql
CREATE INDEX idx_returns_order_id ON returns(order_id);
CREATE INDEX idx_returns_status ON returns(status);
CREATE INDEX idx_returns_created_at ON returns(created_at DESC);
```

**비즈니스 규칙:**
- 반품 가능 기간: 배송 완료 후 7일
- 단순 변심: 고객 부담 6,000원
- 불량/오배송: 판매자 부담

---

### 14. exchanges (교환)

**설명**: 교환 정보를 저장합니다.

| 컬럼명 | 타입 | 제약 | 기본값 | 설명 |
|--------|------|------|--------|------|
| id | UUID | PK | uuid_generate_v4() | 교환 ID |
| order_id | UUID | FK, NOT NULL | - | 주문 ID |
| status | ENUM | NOT NULL | 'REQUESTED' | 교환 상태 |
| items | JSONB | NOT NULL | - | 교환 항목 정보 |
| stock_status | ENUM | NOT NULL | - | 교환 상품 재고 상태 |
| exchange_shipping_fee | BIGINT | NOT NULL | - | 교환 배송비 |
| fee_payment_by | ENUM | NOT NULL | - | 배송비 부담 주체 |
| timeline | JSONB | NULL | - | 교환 진행 타임라인 |
| created_at | TIMESTAMP | NOT NULL | NOW() | 생성 일시 |
| updated_at | TIMESTAMP | NOT NULL | NOW() | 수정 일시 |

**ENUM 타입:**
- `status`: `'REQUESTED'`, `'APPROVED'`, `'REJECTED'`, `'SHIPPING_OLD'`, `'OLD_RECEIVED'`, `'INSPECTING'`, `'SHIPPING_NEW'`, `'COMPLETED'`
- `stock_status`: `'AVAILABLE'`, `'OUT_OF_STOCK'`
- `fee_payment_by`: `'CUSTOMER'`, `'SELLER'`

**외래 키:**
```sql
FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT
```

**인덱스:**
```sql
CREATE INDEX idx_exchanges_order_id ON exchanges(order_id);
CREATE INDEX idx_exchanges_status ON exchanges(status);
CREATE INDEX idx_exchanges_created_at ON exchanges(created_at DESC);
```

**비즈니스 규칙:**
- 교환 상품 재고 부족 시: 환불 또는 재입고 대기

---

### 15. coupons (쿠폰)

**설명**: 쿠폰 마스터 정보를 저장합니다.

| 컬럼명 | 타입 | 제약 | 기본값 | 설명 |
|--------|------|------|--------|------|
| id | UUID | PK | uuid_generate_v4() | 쿠폰 ID |
| code | VARCHAR(50) | UNIQUE, NOT NULL | - | 쿠폰 코드 |
| name | VARCHAR(200) | NOT NULL | - | 쿠폰명 |
| type | ENUM | NOT NULL | - | 쿠폰 유형 |
| discount | BIGINT | NOT NULL | - | 할인 금액/할인율 |
| min_order_amount | BIGINT | NOT NULL | 0 | 최소 주문 금액 |
| max_discount_amount | BIGINT | NULL | - | 최대 할인 금액 |
| max_issue_count | INT | NULL | - | 최대 발급 수량 |
| issued_count | INT | NOT NULL | 0 | 발급된 수량 |
| valid_from | TIMESTAMP | NOT NULL | - | 유효 시작일 |
| valid_until | TIMESTAMP | NOT NULL | - | 유효 종료일 |
| created_at | TIMESTAMP | NOT NULL | NOW() | 생성 일시 |
| updated_at | TIMESTAMP | NOT NULL | NOW() | 수정 일시 |

**ENUM 타입:**
- `type`: `'FIXED_AMOUNT'`, `'PERCENTAGE'`, `'FREE_SHIPPING'`, `'BUY_N_GET_1'`

**인덱스:**
```sql
CREATE UNIQUE INDEX idx_coupons_code ON coupons(code);
CREATE INDEX idx_coupons_valid_period ON coupons(valid_from, valid_until);
```

**비즈니스 규칙:**
- 선착순 쿠폰: `max_issue_count` 설정 필요
- 쿠폰 발급 시 동시성 제어 (Redis 분산 락)

---

### 16. user_coupons (사용자 쿠폰)

**설명**: 사용자에게 발급된 쿠폰을 저장합니다.

| 컬럼명 | 타입 | 제약 | 기본값 | 설명 |
|--------|------|------|--------|------|
| id | UUID | PK | uuid_generate_v4() | 사용자 쿠폰 ID |
| user_id | UUID | FK, NOT NULL | - | 사용자 ID |
| coupon_id | UUID | FK, NOT NULL | - | 쿠폰 ID |
| status | ENUM | NOT NULL | 'AVAILABLE' | 쿠폰 상태 |
| used_at | TIMESTAMP | NULL | - | 사용 일시 |
| created_at | TIMESTAMP | NOT NULL | NOW() | 생성 일시 |
| updated_at | TIMESTAMP | NOT NULL | NOW() | 수정 일시 |

**ENUM 타입:**
- `status`: `'AVAILABLE'`, `'USED'`, `'EXPIRED'`

**외래 키:**
```sql
FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
FOREIGN KEY (coupon_id) REFERENCES coupons(id) ON DELETE CASCADE
```

**인덱스:**
```sql
CREATE INDEX idx_user_coupons_user_id ON user_coupons(user_id);
CREATE INDEX idx_user_coupons_status ON user_coupons(user_id, status);
CREATE UNIQUE INDEX idx_user_coupons_user_coupon ON user_coupons(user_id, coupon_id);
```

**비즈니스 규칙:**
- 사용자는 동일 쿠폰을 1번만 발급받을 수 있음
- 만료 시 배치 작업으로 상태 변경 (AVAILABLE → EXPIRED)

---

### 17. point_histories (포인트 히스토리)

**설명**: 포인트 적립/사용 내역을 저장합니다.

| 컬럼명 | 타입 | 제약 | 기본값 | 설명 |
|--------|------|------|--------|------|
| id | UUID | PK | uuid_generate_v4() | 히스토리 ID |
| user_id | UUID | FK, NOT NULL | - | 사용자 ID |
| type | ENUM | NOT NULL | - | 포인트 유형 |
| amount | BIGINT | NOT NULL | - | 포인트 (+ 적립, - 사용) |
| balance_after | BIGINT | NOT NULL | - | 거래 후 잔액 |
| description | VARCHAR(255) | NOT NULL | - | 설명 |
| reference_id | VARCHAR(100) | NULL | - | 참조 ID (주문 ID 등) |
| expiry_date | DATE | NULL | - | 소멸 예정일 |
| created_at | TIMESTAMP | NOT NULL | NOW() | 생성 일시 |

**ENUM 타입:**
- `type`: `'EARNED'`, `'USED'`, `'EXPIRED'`

**외래 키:**
```sql
FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
```

**인덱스:**
```sql
CREATE INDEX idx_point_histories_user_id ON point_histories(user_id);
CREATE INDEX idx_point_histories_type ON point_histories(user_id, type);
CREATE INDEX idx_point_histories_created_at ON point_histories(created_at DESC);
CREATE INDEX idx_point_histories_expiry ON point_histories(expiry_date) WHERE expiry_date IS NOT NULL;
```

**비즈니스 규칙:**
- 포인트 적립 시점: 구매 확정 (배송 완료 후 7일 또는 수동 확정)
- 포인트 소멸: 최종 적립일로부터 1년
- 잔액 계산: 이전 거래 잔액 + 현재 거래 금액

---

### 18. reviews (리뷰)

**설명**: 상품 리뷰를 저장합니다.

| 컬럼명 | 타입 | 제약 | 기본값 | 설명 |
|--------|------|------|--------|------|
| id | UUID | PK | uuid_generate_v4() | 리뷰 ID |
| user_id | UUID | FK, NOT NULL | - | 사용자 ID |
| product_id | UUID | FK, NOT NULL | - | 상품 ID |
| order_id | UUID | FK, NOT NULL | - | 주문 ID |
| order_item_id | UUID | FK, UNIQUE, NOT NULL | - | 주문 항목 ID |
| rating | INT | NOT NULL | - | 평점 (1-5) |
| title | VARCHAR(100) | NULL | - | 제목 |
| content | TEXT | NOT NULL | - | 내용 |
| images | JSONB | NULL | - | 리뷰 사진 |
| size_rating | ENUM | NULL | - | 사이즈 평가 |
| helpful_count | INT | NOT NULL | 0 | 도움됨 수 |
| created_at | TIMESTAMP | NOT NULL | NOW() | 생성 일시 |
| updated_at | TIMESTAMP | NOT NULL | NOW() | 수정 일시 |
| deleted_at | TIMESTAMP | NULL | - | 삭제 일시 |

**ENUM 타입:**
- `size_rating`: `'TOO_SMALL'`, `'FITS_WELL'`, `'TOO_LARGE'`

**외래 키:**
```sql
FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT
FOREIGN KEY (order_item_id) REFERENCES order_items(id) ON DELETE RESTRICT
```

**인덱스:**
```sql
CREATE INDEX idx_reviews_product_id ON reviews(product_id);
CREATE INDEX idx_reviews_user_id ON reviews(user_id);
CREATE INDEX idx_reviews_rating ON reviews(rating);
CREATE INDEX idx_reviews_created_at ON reviews(created_at DESC);
CREATE INDEX idx_reviews_helpful ON reviews(helpful_count DESC);
CREATE UNIQUE INDEX idx_reviews_order_item ON reviews(order_item_id) WHERE deleted_at IS NULL;
```

**비즈니스 규칙:**
- 구매 확정 후 작성 가능
- 주문 항목당 1개의 리뷰
- 포인트 적립: 일반 리뷰 500P, 포토 리뷰 1,000P
- 리뷰 작성 시 상품 평점/리뷰 수 업데이트

---

### 19. restock_notifications (재입고 알림)

**설명**: 재입고 알림 신청 정보를 저장합니다.

| 컬럼명 | 타입 | 제약 | 기본값 | 설명 |
|--------|------|------|--------|------|
| id | UUID | PK | uuid_generate_v4() | 알림 ID |
| user_id | UUID | FK, NOT NULL | - | 사용자 ID |
| sku | VARCHAR(100) | NOT NULL | - | SKU 코드 |
| notify_via | JSONB | NOT NULL | - | 알림 수단 |
| status | ENUM | NOT NULL | 'PENDING' | 알림 상태 |
| notified_at | TIMESTAMP | NULL | - | 알림 발송 일시 |
| created_at | TIMESTAMP | NOT NULL | NOW() | 생성 일시 |
| updated_at | TIMESTAMP | NOT NULL | NOW() | 수정 일시 |

**ENUM 타입:**
- `status`: `'PENDING'`, `'NOTIFIED'`, `'CANCELLED'`

**외래 키:**
```sql
FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
```

**인덱스:**
```sql
CREATE INDEX idx_restock_notifications_user_id ON restock_notifications(user_id);
CREATE INDEX idx_restock_notifications_sku ON restock_notifications(sku);
CREATE INDEX idx_restock_notifications_status ON restock_notifications(status);
CREATE UNIQUE INDEX idx_restock_notifications_user_sku ON restock_notifications(user_id, sku) WHERE status = 'PENDING';
```

**비즈니스 규칙:**
- 품절 상품에 대해 알림 신청
- 재입고 시 선착순으로 알림 발송
- 알림 수단: EMAIL, PUSH, SMS

---

### 20. reservations (재고 예약) [P0 CRITICAL]

**설명**: Saga Pattern 구현을 위한 재고 예약 추적 정보를 저장합니다.

| 컬럼명 | 타입 | 제약 | 기본값 | 설명 |
|--------|------|------|--------|------|
| id | UUID | PK | uuid_generate_v4() | 예약 ID |
| order_id | UUID | FK, NOT NULL | - | 주문 ID |
| sku | VARCHAR(100) | NOT NULL | - | SKU 코드 |
| quantity | INT | NOT NULL | - | 예약 수량 |
| status | ENUM | NOT NULL | - | 상태 |
| expires_at | TIMESTAMP | NOT NULL | - | 만료 시간 (15분 후) |
| created_at | TIMESTAMP | NOT NULL | NOW() | 생성 일시 |
| updated_at | TIMESTAMP | NOT NULL | NOW() | 수정 일시 |

**ENUM 타입:**
- `status`: `'ACTIVE'`, `'CONFIRMED'`, `'EXPIRED'`, `'CANCELLED'`

**외래 키:**
```sql
FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
```

**인덱스:**
```sql
CREATE INDEX idx_reservations_expires ON reservations(expires_at);
CREATE INDEX idx_reservations_sku ON reservations(sku);
CREATE INDEX idx_reservations_status ON reservations(status);
```

**비즈니스 규칙:**
- 주문 생성 시 예약 생성 (15분 TTL)
- 결제 승인 시 상태 변경: ACTIVE → CONFIRMED
- 15분 경과 또는 결제 실패 시: ACTIVE → EXPIRED
- 주문 취소 시: 상태 변경 → CANCELLED

---

### 21. payment_failures (결제 실패 로그) [P0 CRITICAL]

**설명**: 결제 실패 이력 및 보상 트랜잭션 추적 정보를 저장합니다.

| 컬럼명 | 타입 | 제약 | 기본값 | 설명 |
|--------|------|------|--------|------|
| id | UUID | PK | uuid_generate_v4() | 실패 기록 ID |
| order_id | UUID | FK, NOT NULL | - | 주문 ID |
| payment_id | UUID | FK, NOT NULL | - | 결제 ID |
| reason | VARCHAR(255) | NOT NULL | - | 실패 사유 |
| pg_code | VARCHAR(100) | NULL | - | PG사 응답 코드 |
| compensation_status | ENUM | NOT NULL | - | 보상 상태 |
| compensation_reason | TEXT | NULL | - | 보상 사유 |
| created_at | TIMESTAMP | NOT NULL | NOW() | 생성 일시 |
| updated_at | TIMESTAMP | NOT NULL | NOW() | 수정 일시 |

**ENUM 타입:**
- `compensation_status`: `'PENDING'`, `'COMPENSATED'`, `'FAILED'`

**외래 키:**
```sql
FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
FOREIGN KEY (payment_id) REFERENCES payments(id) ON DELETE CASCADE
```

**인덱스:**
```sql
CREATE INDEX idx_payment_failures_order ON payment_failures(order_id);
CREATE INDEX idx_payment_failures_status ON payment_failures(compensation_status);
```

**비즈니스 규칙:**
- 결제 실패 시 자동 기록
- 자동 환불 처리 (compensat ion_status = COMPENSATED)
- 재고 복구 및 CS 티켓 자동 생성

---

### 22. webhook_logs (웹훅 로그) [P0 CRITICAL]

**설명**: PG사의 웹훅 이벤트를 기록하고 중복 처리를 방지합니다.

| 컬럼명 | 타입 | 제약 | 기본값 | 설명 |
|--------|------|------|--------|------|
| id | UUID | PK | uuid_generate_v4() | 웹훅 로그 ID |
| event_id | VARCHAR(255) | UNIQUE, NOT NULL | - | 이벤트 ID (PG사 제공) |
| event_type | VARCHAR(100) | NOT NULL | - | 이벤트 타입 |
| order_id | UUID | FK, NULL | - | 주문 ID |
| payload | JSONB | NOT NULL | - | 전체 웹훅 페이로드 |
| status | ENUM | NOT NULL | - | 처리 상태 |
| error_message | TEXT | NULL | - | 에러 메시지 |
| retry_count | INT | NOT NULL | 0 | 재시도 횟수 |
| processed_at | TIMESTAMP | NULL | - | 처리 완료 일시 |
| created_at | TIMESTAMP | NOT NULL | NOW() | 생성 일시 |
| updated_at | TIMESTAMP | NOT NULL | NOW() | 수정 일시 |

**ENUM 타입:**
- `status`: `'PROCESSING'`, `'QUEUED'`, `'COMPLETED'`, `'FAILED'`

**외래 키:**
```sql
FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE SET NULL
```

**인덱스:**
```sql
CREATE UNIQUE INDEX idx_webhook_logs_event_id ON webhook_logs(event_id);
CREATE INDEX idx_webhook_logs_status ON webhook_logs(status);
CREATE INDEX idx_webhook_logs_order_id ON webhook_logs(order_id);
CREATE INDEX idx_webhook_logs_created_at ON webhook_logs(created_at DESC);
```

**비즈니스 규칙:**
- Event ID UNIQUE 제약으로 중복 처리 방지
- HMAC-SHA256 서명 검증
- 처리 실패 시 재시도 큐에 자동 추가
- Nonce + Timestamp 검증

---

### 23. webhook_retry_queue (웹훅 재시도 큐) [P0 CRITICAL]

**설명**: 실패한 웹훅 처리를 지수 백오프로 재시도합니다.

| 컬럼명 | 타입 | 제약 | 기본값 | 설명 |
|--------|------|------|--------|------|
| id | UUID | PK | uuid_generate_v4() | 재시도 큐 ID |
| event_id | VARCHAR(255) | FK, NOT NULL | - | 이벤트 ID |
| retry_count | INT | NOT NULL | 0 | 현재 재시도 횟수 |
| max_retries | INT | NOT NULL | 3 | 최대 재시도 횟수 |
| next_retry_at | TIMESTAMP | NOT NULL | - | 다음 재시도 시간 |
| created_at | TIMESTAMP | NOT NULL | NOW() | 생성 일시 |
| updated_at | TIMESTAMP | NOT NULL | NOW() | 수정 일시 |

**외래 키:**
```sql
FOREIGN KEY (event_id) REFERENCES webhook_logs(event_id) ON DELETE CASCADE
```

**인덱스:**
```sql
CREATE INDEX idx_retry_queue_next_retry ON webhook_retry_queue(next_retry_at);
CREATE INDEX idx_retry_queue_event ON webhook_retry_queue(event_id);
```

**비즈니스 규칙:**
- 지수 백오프: 1분 → 5분 → 30분 → 1시간 (max 3회)
- 배치 작업으로 만료된 항목 자동 처리
- 최대 재시도 초과 시 관리자 알림

---

## 인덱스 전략

### 1. 조회 성능 최적화

**자주 조회되는 컬럼:**
- `users.email`: 로그인 시 사용
- `products.category`, `products.brand`: 필터링
- `product_variants.sku`: 재고 조회
- `orders.user_id`: 사용자별 주문 목록
- `orders.status`: 상태별 주문 조회

### 2. 복합 인덱스
```sql
-- 상품 검색 최적화
CREATE INDEX idx_products_category_price ON products(category, sale_price DESC);

-- 주문 목록 조회 최적화
CREATE INDEX idx_orders_user_status_date ON orders(user_id, status, created_at DESC);

-- 재고 알림 조회 최적화
CREATE INDEX idx_restock_sku_status ON restock_notifications(sku, status) WHERE status = 'PENDING';
```

### 3. Partial Index
```sql
-- 삭제되지 않은 데이터만
CREATE INDEX idx_products_active ON products(category, sale_price) WHERE deleted_at IS NULL;

-- 예약 만료 임박 주문
CREATE INDEX idx_orders_expiring ON orders(reservation_expiry) 
WHERE status = 'PENDING_PAYMENT' AND reservation_expiry IS NOT NULL;
```

---

## 비즈니스 규칙

### 1. 재고 관리

**재고 차감 시점:**
```
1. 주문 생성 → reserved_stock += quantity
2. 결제 승인 → physical_stock -= quantity, reserved_stock -= quantity
3. 결제 실패 → reserved_stock -= quantity (복구)
4. 주문 취소 → physical_stock += quantity (복구)
```

**재고 예약 TTL:**
- 15분 경과 시 자동 해제
- Redis에서 관리: `reservation:{orderId}`

**동시성 제어:**
```sql
-- 비관적 락
SELECT * FROM inventory WHERE sku = ? FOR UPDATE;

-- 낙관적 락 (version 컬럼 추가)
UPDATE inventory 
SET available_stock = available_stock - ?, version = version + 1 
WHERE sku = ? AND version = ?;
```

---

### 2. 주문 상태 전이
```
PENDING_PAYMENT (결제 대기)
    ↓ (결제 승인)
PAID (결제 완료)
    ↓ (출고 시작)
PREPARING (상품 준비중)
    ↓ (배송 시작)
SHIPPED (배송중)
    ↓ (배송 완료)
DELIVERED (배송 완료)
    ↓ (구매 확정 or 7일 경과)
[구매 확정] → 포인트 적립

특수 케이스:
- PENDING_PAYMENT → CANCELLED (결제 실패/취소)
- PAID → CANCELLED (발송 전 취소)
- DELIVERED → RETURN_REQUESTED (반품 신청)
- DELIVERED → EXCHANGE_REQUESTED (교환 신청)
```

---

### 3. 쿠폰 정책

**쿠폰 중복 사용:**
- 1주문에 1개 쿠폰만 사용 가능

**쿠폰 복구:**
```
- 주문 취소 → 쿠폰 USED → AVAILABLE (재사용 가능)
- 부분 취소 → 쿠폰 복구
- 전체 반품 → 쿠폰 복구
```

**선착순 쿠폰 동시성:**
```sql
-- Redis Lua Script
local current = redis.call('GET', 'coupon:' .. coupon_id)
if tonumber(current) < tonumber(max_count) then
    redis.call('INCR', 'coupon:' .. coupon_id)
    return 1
else
    return 0
end
```

---

### 4. 포인트 정책

**적립 기준:**
- 구매 금액의 1% (VIP: 2%)
- 구매 확정 시점에 적립

**포인트 소멸:**
- 최종 적립일로부터 1년
- 매일 배치 작업으로 소멸 처리

**포인트 잔액 계산:**
```sql
SELECT SUM(amount) as balance
FROM point_histories
WHERE user_id = ?
  AND (expiry_date IS NULL OR expiry_date >= CURRENT_DATE);
```

---

### 5. 정산

**정산 주기:**
- 주 1회 (매주 월요일)

**정산 금액:**
```
판매가 - PG 수수료(3%) - 플랫폼 수수료(10%)
```

**취소/반품 처리:**
- 다음 정산에서 차감

---

## 추가 고려사항

### 1. 파티셔닝

**시간 기반 파티셔닝:**
```sql
-- orders 테이블 월별 파티션
CREATE TABLE orders_2024_03 PARTITION OF orders
FOR VALUES FROM ('2024-03-01') TO ('2024-04-01');

-- point_histories 연도별 파티션
CREATE TABLE point_histories_2024 PARTITION OF point_histories
FOR VALUES FROM ('2024-01-01') TO ('2025-01-01');
```

### 2. 아카이빙

**오래된 데이터 아카이빙:**
- 2년 이상 주문 → `orders_archive` 테이블로 이동
- 소멸된 포인트 → `point_histories_archive`

### 3. 감사 로그

**중요 작업 로깅:**
- 재고 변경 → `inventory_logs`
- 쿠폰 사용 → `coupon_usage_logs`
- 가격 변경 → `price_change_logs`

—

**문서 버전:** v1.0.0  
**최종 수정일:** 2025-10-31  
**작성자:** Backend Team