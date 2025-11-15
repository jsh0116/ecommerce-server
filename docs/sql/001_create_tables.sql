-- ============================================
-- HHPLUS E-Commerce Database Schema
-- MySQL 8.0+ / MariaDB 10.5+
-- ============================================
-- 이 스크립트는 MySQL Workbench에서 직접 실행 가능합니다.
-- 실행 순서: 001_create_tables.sql → 002_create_indexes.sql → 003_create_triggers.sql

USE hhplus_ecommerce;

-- ============================================
-- 1. USERS (회원)
-- ============================================
CREATE TABLE IF NOT EXISTS users (
    id CHAR(36) PRIMARY KEY COMMENT '회원 ID (UUID)',
    email VARCHAR(255) NOT NULL UNIQUE COMMENT '이메일',
    password_hash VARCHAR(255) NOT NULL COMMENT '비밀번호 해시 (bcrypt)',
    name VARCHAR(100) NOT NULL COMMENT '회원 이름',
    phone VARCHAR(20) NULL COMMENT '전화번호',
    balance BIGINT NOT NULL DEFAULT 0 COMMENT '잔액 (포인트)',
    tier ENUM('GENERAL', 'VIP') NOT NULL DEFAULT 'GENERAL' COMMENT '회원 등급',
    tier_updated_at TIMESTAMP NULL COMMENT '등급 변경 일시',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    deleted_at TIMESTAMP NULL COMMENT '삭제 일시 (소프트 삭제)',

    INDEX idx_email (email),
    INDEX idx_tier (tier),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='회원 정보';

-- ============================================
-- 2. ADDRESSES (배송지)
-- ============================================
CREATE TABLE IF NOT EXISTS addresses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '배송지 ID',
    user_id CHAR(36) NOT NULL COMMENT '회원 ID',
    address_name VARCHAR(100) NOT NULL COMMENT '주소명',
    phone VARCHAR(20) NOT NULL COMMENT '수령인 전화번호',
    street_address VARCHAR(255) NOT NULL COMMENT '도로명 주소',
    detail_address VARCHAR(255) NOT NULL COMMENT '상세 주소',
    postal_code VARCHAR(10) NOT NULL COMMENT '우편번호',
    city VARCHAR(50) NOT NULL COMMENT '도시',
    province VARCHAR(50) NOT NULL COMMENT '도/도',
    is_default BOOLEAN NOT NULL DEFAULT FALSE COMMENT '기본 배송지 여부',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',

    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_default (user_id, is_default)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='배송지 정보';

-- ============================================
-- 3. SIZE_PROFILES (사이즈 프로필)
-- ============================================
CREATE TABLE IF NOT EXISTS size_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '프로필 ID',
    user_id CHAR(36) NOT NULL UNIQUE COMMENT '회원 ID',
    height DECIMAL(5,2) NULL COMMENT '키(cm)',
    weight DECIMAL(6,2) NULL COMMENT '체중(kg)',
    top_size VARCHAR(10) NULL COMMENT '상의 사이즈',
    bottom_size VARCHAR(10) NULL COMMENT '하의 사이즈',
    shoe_size DECIMAL(5,1) NULL COMMENT '신발 사이즈',
    preferred_fit ENUM('SLIM', 'REGULAR', 'RELAXED') NULL COMMENT '선호 핏',
    body_type ENUM('SLIM', 'NORMAL', 'CURVY', 'MUSCULAR') NULL COMMENT '체형',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',

    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='사이즈 프로필';

-- ============================================
-- 4. PRODUCTS (상품)
-- ============================================
CREATE TABLE IF NOT EXISTS products (
    id CHAR(36) PRIMARY KEY COMMENT '상품 ID (UUID)',
    name VARCHAR(255) NOT NULL COMMENT '상품명',
    brand VARCHAR(100) NOT NULL COMMENT '브랜드',
    category ENUM('TOP', 'BOTTOM', 'DRESS', 'OUTERWEAR', 'ACCESSORY', 'FOOTWEAR') NOT NULL COMMENT '카테고리',
    description TEXT NULL COMMENT '상품 설명',
    material VARCHAR(255) NULL COMMENT '소재',
    care_instructions JSON NULL COMMENT '세탁 방법',
    base_price BIGINT NOT NULL COMMENT '정가 (원)',
    sale_price BIGINT NOT NULL COMMENT '판매가 (원)',
    discount_rate INT NOT NULL DEFAULT 0 COMMENT '할인율 (%)',
    images JSON NOT NULL COMMENT '이미지 URL 배열',
    tags JSON NULL COMMENT '태그 배열',
    rating DECIMAL(3,2) NULL DEFAULT 0 COMMENT '평균 평점 (0-5)',
    review_count INT NOT NULL DEFAULT 0 COMMENT '리뷰 수',
    is_active BOOLEAN NOT NULL DEFAULT TRUE COMMENT '판매 여부',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    deleted_at TIMESTAMP NULL COMMENT '삭제 일시',

    INDEX idx_brand (brand),
    INDEX idx_category (category),
    INDEX idx_sale_price (sale_price),
    INDEX idx_rating (rating DESC),
    INDEX idx_created_at (created_at DESC),
    INDEX idx_deleted (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='상품 정보';

-- ============================================
-- 5. PRODUCT_VARIANTS (상품 변량/SKU)
-- ============================================
CREATE TABLE IF NOT EXISTS product_variants (
    id CHAR(36) PRIMARY KEY COMMENT '변량 ID (UUID)',
    product_id CHAR(36) NOT NULL COMMENT '상품 ID',
    sku VARCHAR(100) NOT NULL UNIQUE COMMENT 'SKU 코드',
    color VARCHAR(50) NOT NULL COMMENT '색상',
    color_hex VARCHAR(7) NULL COMMENT '색상 HEX 코드',
    size VARCHAR(20) NOT NULL COMMENT '사이즈',
    length ENUM('SHORT', 'REGULAR', 'LONG') NULL DEFAULT 'REGULAR' COMMENT '길이',
    price BIGINT NOT NULL COMMENT '판매가',
    original_price BIGINT NOT NULL COMMENT '원가',
    images VARCHAR(2000) NULL COMMENT '변량 이미지 URL (쉼표 구분)',
    is_active BOOLEAN NOT NULL DEFAULT TRUE COMMENT '활성화 여부',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    deleted_at TIMESTAMP NULL COMMENT '삭제 일시',

    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    UNIQUE INDEX idx_sku (sku),
    INDEX idx_product_id (product_id),
    INDEX idx_color_size (color, size)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='상품 변량 (색상, 사이즈)';

-- ============================================
-- 6. INVENTORY (재고)
-- ============================================
CREATE TABLE IF NOT EXISTS inventory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '재고 ID',
    sku VARCHAR(100) NOT NULL UNIQUE COMMENT 'SKU 코드',
    physical_stock INT NOT NULL DEFAULT 0 COMMENT '실제 재고',
    reserved_stock INT NOT NULL DEFAULT 0 COMMENT '예약 재고',
    available_stock INT NOT NULL DEFAULT 0 COMMENT '가용 재고',
    safety_stock INT NOT NULL DEFAULT 0 COMMENT '안전 재고',
    status ENUM('IN_STOCK', 'LOW_STOCK', 'OUT_OF_STOCK') NOT NULL DEFAULT 'IN_STOCK' COMMENT '재고 상태',
    reorder_level INT NOT NULL DEFAULT 20 COMMENT '재주문 수준',
    reorder_quantity INT NOT NULL DEFAULT 100 COMMENT '재주문 수량',
    last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '마지막 업데이트',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',

    UNIQUE INDEX idx_sku (sku),
    INDEX idx_status (status),
    INDEX idx_available (available_stock)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='재고 관리';

-- ============================================
-- 7. CARTS (장바구니)
-- ============================================
CREATE TABLE IF NOT EXISTS carts (
    id CHAR(36) PRIMARY KEY COMMENT '장바구니 ID (UUID)',
    user_id CHAR(36) NOT NULL UNIQUE COMMENT '회원 ID',
    total_price BIGINT NOT NULL DEFAULT 0 COMMENT '총액',
    item_count INT NOT NULL DEFAULT 0 COMMENT '상품 수량',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',

    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='장바구니';

-- ============================================
-- 8. CART_ITEMS (장바구니 항목)
-- ============================================
CREATE TABLE IF NOT EXISTS cart_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '장바구니 항목 ID',
    cart_id CHAR(36) NOT NULL COMMENT '장바구니 ID',
    variant_id CHAR(36) NOT NULL COMMENT '상품 변량 ID',
    quantity INT NOT NULL DEFAULT 1 COMMENT '수량',
    unit_price BIGINT NOT NULL COMMENT '단가',
    subtotal BIGINT NOT NULL COMMENT '소계',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',

    FOREIGN KEY (cart_id) REFERENCES carts(id) ON DELETE CASCADE,
    FOREIGN KEY (variant_id) REFERENCES product_variants(id) ON DELETE CASCADE,
    INDEX idx_cart_id (cart_id),
    INDEX idx_variant_id (variant_id),
    UNIQUE INDEX idx_cart_variant (cart_id, variant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='장바구니 항목';

-- ============================================
-- 9. ORDERS (주문)
-- ============================================
CREATE TABLE IF NOT EXISTS orders (
    id CHAR(36) PRIMARY KEY COMMENT '주문 ID (UUID)',
    order_number VARCHAR(50) NOT NULL UNIQUE COMMENT '주문번호',
    user_id CHAR(36) NOT NULL COMMENT '회원 ID',
    status ENUM(
        'PENDING', 'PAID', 'PREPARING', 'SHIPPED', 'DELIVERED',
        'CANCELLED', 'RETURN_REQUESTED', 'RETURN_COMPLETED',
        'EXCHANGE_REQUESTED', 'EXCHANGE_COMPLETED'
    ) NOT NULL DEFAULT 'PENDING' COMMENT '주문 상태',
    shipping_address JSON NOT NULL COMMENT '배송지 정보',
    shipping_method ENUM('standard', 'express', 'dawn') NOT NULL COMMENT '배송방법',
    shipping_fee BIGINT NOT NULL DEFAULT 0 COMMENT '배송료',
    coupon_code VARCHAR(50) NULL COMMENT '쿠폰 코드',
    points_used BIGINT NOT NULL DEFAULT 0 COMMENT '사용 포인트',
    subtotal BIGINT NOT NULL COMMENT '소계',
    discount BIGINT NOT NULL DEFAULT 0 COMMENT '할인액',
    total_amount BIGINT NOT NULL COMMENT '총액',
    request_message VARCHAR(255) NULL COMMENT '배송 요청사항',
    reservation_expiry TIMESTAMP NULL COMMENT '재고 예약 만료',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',

    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    UNIQUE INDEX idx_order_number (order_number),
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at DESC),
    INDEX idx_reservation_expiry (reservation_expiry)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='주문';

-- ============================================
-- 10. ORDER_ITEMS (주문 항목)
-- ============================================
CREATE TABLE IF NOT EXISTS order_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '주문 항목 ID',
    order_id CHAR(36) NOT NULL COMMENT '주문 ID',
    product_id CHAR(36) NOT NULL COMMENT '상품 ID',
    variant_id CHAR(36) NOT NULL COMMENT '상품 변량 ID',
    quantity INT NOT NULL COMMENT '수량',
    unit_price BIGINT NOT NULL COMMENT '단가',
    subtotal BIGINT NOT NULL COMMENT '소계',
    product_snapshot JSON NOT NULL COMMENT '상품 정보 스냅샷',
    review_status ENUM('PENDING', 'REVIEWED', 'REVIEWABLE') DEFAULT 'PENDING' COMMENT '리뷰 상태',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',

    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT,
    FOREIGN KEY (variant_id) REFERENCES product_variants(id) ON DELETE RESTRICT,
    INDEX idx_order_id (order_id),
    INDEX idx_product_id (product_id),
    INDEX idx_variant_id (variant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='주문 항목';

-- ============================================
-- 11. PAYMENTS (결제)
-- ============================================
CREATE TABLE IF NOT EXISTS payments (
    id CHAR(36) PRIMARY KEY COMMENT '결제 ID (UUID)',
    order_id CHAR(36) NOT NULL UNIQUE COMMENT '주문 ID',
    idempotency_key VARCHAR(255) NOT NULL UNIQUE COMMENT '멱등성 키',
    method ENUM('CARD', 'BANK_TRANSFER', 'PAYPAL', 'APPLE_PAY', 'KAKAO_PAY', 'NAVER_PAY') NOT NULL COMMENT '결제수단',
    status ENUM('PENDING', 'APPROVED', 'DECLINED', 'REFUNDED', 'CANCELLED') NOT NULL DEFAULT 'PENDING' COMMENT '결제 상태',
    amount BIGINT NOT NULL COMMENT '결제 금액',
    transaction_id VARCHAR(100) NULL COMMENT 'PG사 거래 ID',
    pg_code VARCHAR(100) NULL COMMENT 'PG사 응답 코드',
    fail_reason TEXT NULL COMMENT '실패 사유',
    approved_at TIMESTAMP NULL COMMENT '승인 일시',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',

    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT,
    UNIQUE INDEX idx_order_id (order_id),
    UNIQUE INDEX idx_idempotency (idempotency_key),
    INDEX idx_status (status),
    INDEX idx_transaction_id (transaction_id),
    INDEX idx_approved_at (approved_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='결제';

-- ============================================
-- 12. SHIPMENTS (배송)
-- ============================================
CREATE TABLE IF NOT EXISTS shipments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '배송 ID',
    order_id CHAR(36) NOT NULL UNIQUE COMMENT '주문 ID',
    tracking_number VARCHAR(100) NULL COMMENT '추적번호',
    carrier VARCHAR(50) NULL COMMENT '배송사',
    status ENUM('PREPARING', 'SHIPPED', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED', 'FAILED') NOT NULL DEFAULT 'PREPARING' COMMENT '배송 상태',
    estimated_delivery DATE NULL COMMENT '예상 배송일',
    events JSON NULL COMMENT '배송 이벤트 타임라인',
    shipped_at TIMESTAMP NULL COMMENT '발송 일시',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',

    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT,
    UNIQUE INDEX idx_order_id (order_id),
    INDEX idx_tracking_number (tracking_number),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='배송';

-- ============================================
-- 13. COUPONS (쿠폰)
-- ============================================
CREATE TABLE IF NOT EXISTS coupons (
    id CHAR(36) PRIMARY KEY COMMENT '쿠폰 ID (UUID)',
    code VARCHAR(50) NOT NULL UNIQUE COMMENT '쿠폰 코드',
    name VARCHAR(255) NOT NULL COMMENT '쿠폰명',
    type ENUM('PERCENTAGE', 'FIXED_AMOUNT', 'FREE_SHIPPING', 'BUY_N_GET_1') NOT NULL COMMENT '쿠폰 유형',
    discount BIGINT NOT NULL COMMENT '할인 금액/할인율',
    min_order_amount BIGINT NOT NULL DEFAULT 0 COMMENT '최소 주문액',
    max_discount_amount BIGINT NULL COMMENT '최대 할인액',
    total_quantity INT NOT NULL COMMENT '최대 발급 수',
    issued_quantity INT NOT NULL DEFAULT 0 COMMENT '발급된 수',
    max_per_user INT NOT NULL DEFAULT 1 COMMENT '사용자당 최대 사용 횟수',
    valid_from TIMESTAMP NOT NULL COMMENT '유효 시작일',
    valid_until TIMESTAMP NOT NULL COMMENT '유효 종료일',
    is_active BOOLEAN NOT NULL DEFAULT TRUE COMMENT '활성화 여부',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',

    UNIQUE INDEX idx_code (code),
    INDEX idx_valid_period (valid_from, valid_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='쿠폰';

-- ============================================
-- 14. USER_COUPONS (사용자 쿠폰)
-- ============================================
CREATE TABLE IF NOT EXISTS user_coupons (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '사용자 쿠폰 ID',
    user_id CHAR(36) NOT NULL COMMENT '회원 ID',
    coupon_id CHAR(36) NOT NULL COMMENT '쿠폰 ID',
    status ENUM('AVAILABLE', 'USED', 'EXPIRED') NOT NULL DEFAULT 'AVAILABLE' COMMENT '쿠폰 상태',
    used_at TIMESTAMP NULL COMMENT '사용 일시',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',

    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (coupon_id) REFERENCES coupons(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_status (user_id, status),
    UNIQUE INDEX idx_user_coupon (user_id, coupon_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='사용자 쿠폰';

-- ============================================
-- 15. POINT_HISTORIES (포인트 이력)
-- ============================================
CREATE TABLE IF NOT EXISTS point_histories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '포인트 이력 ID',
    user_id CHAR(36) NOT NULL COMMENT '회원 ID',
    type ENUM('EARNED', 'USED', 'EXPIRED', 'ADJUSTED') NOT NULL COMMENT '포인트 유형',
    amount BIGINT NOT NULL COMMENT '포인트',
    balance_after BIGINT NOT NULL COMMENT '포인트 잔액',
    description VARCHAR(255) NOT NULL COMMENT '설명',
    reference_id VARCHAR(100) NULL COMMENT '참조 ID (주문 ID 등)',
    expiry_date DATE NULL COMMENT '소멸 예정일',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',

    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_type (user_id, type),
    INDEX idx_created_at (created_at DESC),
    INDEX idx_expiry (expiry_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='포인트 이력';

-- ============================================
-- 16. REVIEWS (리뷰)
-- ============================================
CREATE TABLE IF NOT EXISTS reviews (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '리뷰 ID',
    user_id CHAR(36) NOT NULL COMMENT '회원 ID',
    product_id CHAR(36) NOT NULL COMMENT '상품 ID',
    order_id CHAR(36) NOT NULL COMMENT '주문 ID',
    order_item_id BIGINT NOT NULL UNIQUE COMMENT '주문 항목 ID',
    rating INT NOT NULL COMMENT '평점 (1-5)',
    title VARCHAR(255) NULL COMMENT '제목',
    content TEXT NOT NULL COMMENT '내용',
    images JSON NULL COMMENT '리뷰 사진',
    size_rating ENUM('TOO_SMALL', 'FITS_WELL', 'TOO_LARGE') NULL COMMENT '사이즈 평가',
    helpful_count INT NOT NULL DEFAULT 0 COMMENT '도움이 됨 수',
    is_verified_purchase BOOLEAN NOT NULL DEFAULT TRUE COMMENT '구매 확인 리뷰 여부',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    deleted_at TIMESTAMP NULL COMMENT '삭제 일시',

    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT,
    FOREIGN KEY (order_item_id) REFERENCES order_items(id) ON DELETE RESTRICT,
    INDEX idx_product_id (product_id),
    INDEX idx_user_id (user_id),
    INDEX idx_rating (rating),
    INDEX idx_created_at (created_at DESC),
    INDEX idx_helpful (helpful_count DESC),
    UNIQUE INDEX idx_order_item (order_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='리뷰';

-- ============================================
-- 17. RESTOCK_NOTIFICATIONS (재입고 알림)
-- ============================================
CREATE TABLE IF NOT EXISTS restock_notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '알림 ID',
    user_id CHAR(36) NOT NULL COMMENT '회원 ID',
    sku VARCHAR(100) NOT NULL COMMENT 'SKU 코드',
    notify_via JSON NOT NULL COMMENT '알림 채널',
    status ENUM('PENDING', 'NOTIFIED', 'CANCELLED') NOT NULL DEFAULT 'PENDING' COMMENT '알림 상태',
    notified_at TIMESTAMP NULL COMMENT '알림 발송 일시',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',

    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_sku (sku),
    INDEX idx_status (status),
    UNIQUE INDEX idx_user_sku (user_id, sku)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='재입고 알림';

-- ============================================
-- 18. RESERVATIONS (재고 예약) [P0 CRITICAL]
-- ============================================
CREATE TABLE IF NOT EXISTS reservations (
    id CHAR(36) PRIMARY KEY COMMENT '예약 ID (UUID)',
    order_id CHAR(36) NOT NULL COMMENT '주문 ID',
    sku VARCHAR(100) NOT NULL COMMENT 'SKU 코드',
    quantity INT NOT NULL COMMENT '예약 수량',
    status ENUM('ACTIVE', 'CONFIRMED', 'EXPIRED', 'CANCELLED') NOT NULL DEFAULT 'ACTIVE' COMMENT '상태',
    expires_at TIMESTAMP NOT NULL COMMENT '만료 시간 (15분 후)',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',

    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    INDEX idx_expires (expires_at),
    INDEX idx_sku (sku),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='재고 예약 (Saga Pattern)';

-- ============================================
-- 19. PAYMENT_FAILURES (결제 실패 로그) [P0 CRITICAL]
-- ============================================
CREATE TABLE IF NOT EXISTS payment_failures (
    id CHAR(36) PRIMARY KEY COMMENT '실패 기록 ID (UUID)',
    order_id CHAR(36) NOT NULL COMMENT '주문 ID',
    payment_id CHAR(36) NOT NULL COMMENT '결제 ID',
    reason VARCHAR(255) NOT NULL COMMENT '실패 사유',
    pg_code VARCHAR(100) NULL COMMENT 'PG사 응답 코드',
    compensation_status ENUM('PENDING', 'COMPENSATED', 'FAILED') NOT NULL DEFAULT 'PENDING' COMMENT '보상 상태',
    compensation_reason TEXT NULL COMMENT '보상 사유',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',

    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    FOREIGN KEY (payment_id) REFERENCES payments(id) ON DELETE CASCADE,
    INDEX idx_order (order_id),
    INDEX idx_status (compensation_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='결제 실패 로그';

-- ============================================
-- 20. WEBHOOK_LOGS (웹훅 로그) [P0 CRITICAL]
-- ============================================
CREATE TABLE IF NOT EXISTS webhook_logs (
    id CHAR(36) PRIMARY KEY COMMENT '웹훅 로그 ID (UUID)',
    event_id VARCHAR(255) NOT NULL UNIQUE COMMENT '이벤트 ID (PG사 제공)',
    event_type VARCHAR(100) NOT NULL COMMENT '이벤트 타입',
    order_id CHAR(36) NULL COMMENT '주문 ID',
    payload JSON NOT NULL COMMENT '전체 웹훅 페이로드',
    status ENUM('PROCESSING', 'QUEUED', 'COMPLETED', 'FAILED') NOT NULL DEFAULT 'PROCESSING' COMMENT '처리 상태',
    error_message TEXT NULL COMMENT '에러 메시지',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '재시도 횟수',
    processed_at TIMESTAMP NULL COMMENT '처리 완료 일시',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',

    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE SET NULL,
    UNIQUE INDEX idx_event_id (event_id),
    INDEX idx_status (status),
    INDEX idx_order_id (order_id),
    INDEX idx_created_at (created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='웹훅 로그';

-- ============================================
-- 21. WEBHOOK_RETRY_QUEUE (웹훅 재시도 큐) [P0 CRITICAL]
-- ============================================
CREATE TABLE IF NOT EXISTS webhook_retry_queue (
    id CHAR(36) PRIMARY KEY COMMENT '재시도 큐 ID (UUID)',
    event_id VARCHAR(255) NOT NULL COMMENT '이벤트 ID',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '현재 재시도 횟수',
    max_retries INT NOT NULL DEFAULT 3 COMMENT '최대 재시도 횟수',
    next_retry_at TIMESTAMP NOT NULL COMMENT '다음 재시도 시간',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',

    FOREIGN KEY (event_id) REFERENCES webhook_logs(event_id) ON DELETE CASCADE,
    INDEX idx_next_retry (next_retry_at),
    INDEX idx_event (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='웹훅 재시도 큐';

-- ============================================
-- 22. RETURNS (반품)
-- ============================================
CREATE TABLE IF NOT EXISTS returns (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '반품 ID',
    order_id CHAR(36) NOT NULL COMMENT '주문 ID',
    status ENUM('REQUESTED', 'APPROVED', 'REJECTED', 'RETURNED', 'REFUNDED') NOT NULL DEFAULT 'REQUESTED' COMMENT '반품 상태',
    items JSON NOT NULL COMMENT '반품 상품 목록',
    reason ENUM('DEFECTIVE', 'NOT_AS_DESCRIBED', 'WRONG_SIZE', 'CHANGED_MIND') NOT NULL COMMENT '반품사유',
    detail_reason TEXT NULL COMMENT '상세 사유',
    images JSON NULL COMMENT '반품 사진',
    refund_method ENUM('ORIGINAL', 'NEW_CARD', 'BANK_TRANSFER') NOT NULL COMMENT '환불방법',
    bank_account JSON NULL COMMENT '계좌 정보',
    return_shipping_fee BIGINT NOT NULL DEFAULT 0 COMMENT '반품 배송료',
    fee_payment_by ENUM('CUSTOMER', 'SELLER') NOT NULL COMMENT '배송료 부담',
    expected_refund BIGINT NOT NULL COMMENT '예상 환불액',
    timeline JSON NULL COMMENT '반품 타임라인',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',

    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT,
    INDEX idx_order_id (order_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='반품';

-- ============================================
-- 23. EXCHANGES (교환)
-- ============================================
CREATE TABLE IF NOT EXISTS exchanges (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '교환 ID',
    order_id CHAR(36) NOT NULL COMMENT '주문 ID',
    status ENUM('REQUESTED', 'APPROVED', 'REJECTED', 'SHIPPING_OLD', 'OLD_RECEIVED', 'INSPECTING', 'SHIPPING_NEW', 'COMPLETED') NOT NULL DEFAULT 'REQUESTED' COMMENT '교환 상태',
    items JSON NOT NULL COMMENT '교환 상품 목록',
    stock_status ENUM('AVAILABLE', 'UNAVAILABLE', 'PARTIAL') NOT NULL DEFAULT 'AVAILABLE' COMMENT '교환 상품 재고 상태',
    exchange_shipping_fee BIGINT NOT NULL DEFAULT 0 COMMENT '교환 배송료',
    fee_payment_by ENUM('CUSTOMER', 'SELLER') NOT NULL COMMENT '배송료 부담',
    timeline JSON NULL COMMENT '교환 타임라인',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',

    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT,
    INDEX idx_order_id (order_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='교환';

-- ============================================
-- 완료 메시지
-- ============================================
-- 모든 테이블이 정상적으로 생성되었습니다.
-- 다음 단계: 002_create_indexes.sql 실행 → 003_create_triggers.sql 실행
