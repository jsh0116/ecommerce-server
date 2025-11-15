-- ============================================
-- STEP08: 데이터베이스 성능 최적화 - 추가 인덱스 생성
-- MySQL 8.0+ / MariaDB 10.5+
-- ============================================
-- 실행 순서: 001_create_tables.sql 이후에 실행
-- 목적: 현재 쿼리 패턴 분석을 통한 성능 최적화
--
-- 최적화 전략:
-- 1. N+1 쿼리 문제 해결 (Fetch Join + 복합 인덱스)
-- 2. WHERE 절 필터링 개선 (복합 인덱스)
-- 3. ORDER BY 성능 개선 (정렬 커버링 인덱스)
-- 4. GROUP BY 쿼리 최적화 (집계 함수 인덱스)
-- 5. 배치 작업 최적화 (복합 인덱스로 스캔 범위 축소)
-- ============================================

USE hhplus_ecommerce;

-- ============================================
-- [P1] Priority 1 - CRITICAL: 즉시 추가 필요
-- ============================================

-- 1. Products 테이블: 브랜드+카테고리 복합 인덱스
-- 문제: 현재 brand와 category 인덱스가 별도로 존재하여 브라우징 쿼리 비효율
-- 최적화:
--   - ProductController.getProducts(brand, category) 조회 시
--   - WHERE brand = ? AND category = ? 조건 단일 인덱스 스캔
--   - 커버링 인덱스: 브랜드+카테고리+활성화 상태
-- 쿼리 예: SELECT * FROM products WHERE brand = 'Nike' AND category = 'TOP' AND is_active = 1
ALTER TABLE products
ADD INDEX idx_brand_category_active (brand, category, is_active) COMMENT '브랜드+카테고리+활성화 복합 인덱스 (브라우징 최적화)';

-- 2. Orders 테이블: 사용자+결제상태+결제날짜 복합 인덱스
-- 문제: 사용자 주문 조회 시 상태 필터링 후 정렬 비효율
-- 최적화:
--   - UserOrders 조회: WHERE user_id = ? AND status IN ('PAID', 'SHIPPED')
--   - 정렬: ORDER BY paid_at DESC
--   - 커버링 인덱스: 사용자+상태+결제날짜
-- 쿼리 예: SELECT * FROM orders WHERE user_id = 1 AND status = 'PAID' ORDER BY paid_at DESC LIMIT 20
ALTER TABLE orders
ADD INDEX idx_user_status_paid (user_id, status, paid_at DESC) COMMENT '사용자+상태+결제날짜 복합 인덱스 (사용자 주문 조회 최적화)';

-- 3. Reservations 테이블: SKU+상태+만료시간 복합 인덱스
-- 문제: 배치 작업으로 만료된 예약 찾기 시 풀 테이블 스캔
-- 최적화:
--   - 배치 작업: SELECT * FROM reservations WHERE status = 'ACTIVE' AND expires_at <= NOW()
--   - 한 번에 스캔 범위 축소
-- 쿼리 예: SELECT * FROM reservations WHERE status = 'ACTIVE' AND expires_at <= CURRENT_TIMESTAMP
ALTER TABLE reservations
ADD INDEX idx_status_expires (status, expires_at) COMMENT '상태+만료시간 복합 인덱스 (배치 조회 최적화)';

-- ============================================
-- [P2] Priority 2 - HIGH: 1개월 내 추가 권장
-- ============================================

-- 4. Products 테이블: 삭제 상태 필터링 최적화
-- 문제: 소프트 삭제 필터링이 모든 조회에 포함되어야 함
-- 최적화:
--   - WHERE deleted_at IS NULL 조건이 자주 사용됨
--   - 기존 deleted_at 인덱스는 범위 검색에만 유용
--   - 활성화 상태와 함께 사용되는 복합 인덱스 필요
-- 쿼리 예: SELECT * FROM products WHERE deleted_at IS NULL AND is_active = 1 ORDER BY created_at DESC
ALTER TABLE products
ADD INDEX idx_active_deleted (is_active, deleted_at) COMMENT '활성화+삭제상태 복합 인덱스 (소프트 삭제 필터링 최적화)';

-- 5. Reviews 테이블: 상품+최신 정렬 복합 인덱스
-- 문제: 상품의 리뷰 목록 조회 시 생성 날짜 정렬 필요
-- 최적화:
--   - ProductController.getProductReviews(productId)
--   - WHERE product_id = ? ORDER BY created_at DESC
--   - 커버링 인덱스로 별도 정렬 작업 제거
-- 쿼리 예: SELECT * FROM reviews WHERE product_id = 1 AND deleted_at IS NULL ORDER BY created_at DESC
ALTER TABLE reviews
ADD INDEX idx_product_created (product_id, created_at DESC) COMMENT '상품+생성날짜 복합 인덱스 (상품 리뷰 조회 최적화)';

-- 6. User_Coupons 테이블: 사용자+상태+만료시간 복합 인덱스
-- 문제: 유효한 쿠폰 조회 시 상태와 기간 모두 필터링
-- 최적화:
--   - SELECT * FROM user_coupons WHERE user_id = ? AND status = 'AVAILABLE'
--   - 쿠폰 만료 배치: WHERE status = 'AVAILABLE' AND expires_at <= NOW()
// 쿼리 예: SELECT * FROM user_coupons WHERE user_id = 1 AND status = 'AVAILABLE'
ALTER TABLE user_coupons
ADD INDEX idx_user_status_used (user_id, status, used_at DESC) COMMENT '사용자+상태+사용날짜 복합 인덱스 (쿠폰 조회 최적화)';

-- 7. Order_Items 테이블: 상품+주문별 주문 항목 조회 최적화
-- 문제: 주문 항목 조회 시 상품 ID와 주문 ID 모두 사용
-- 최적화:
--   - SELECT * FROM order_items WHERE order_id = ?
--   - 또는 상품별 모든 주문 항목: SELECT * FROM order_items WHERE product_id = ?
--   - 두 경우 모두 개별 인덱스로 충분하지만, 조회 성능 향상
// 쿼리 예: SELECT * FROM order_items WHERE order_id = 'uuid' AND review_status IN ('PENDING', 'REVIEWABLE')
ALTER TABLE order_items
ADD INDEX idx_order_product (order_id, product_id) COMMENT '주문+상품 복합 인덱스 (주문 항목 조회 최적화)';

-- 8. Inventory 테이블: SKU+상태 복합 인덱스
-- 문제: 재고 상태별 조회 시 SKU 기준으로 추가 필터링 필요
// 최적화:
--   - SELECT * FROM inventory WHERE sku = ? AND status IN ('IN_STOCK', 'LOW_STOCK')
--   - 상태별 대시보드: SELECT COUNT(*) FROM inventory WHERE status = 'OUT_OF_STOCK' GROUP BY sku
// 쿼리 예: SELECT * FROM inventory WHERE status = 'IN_STOCK' ORDER BY available_stock DESC
ALTER TABLE inventory
ADD INDEX idx_status_stock (status, available_stock DESC) COMMENT '상태+가용재고 복합 인덱스 (재고 상태별 조회 최적화)';

-- ============================================
-- [P3] Priority 3 - MEDIUM: 분기별 추가 권장
-- ============================================

-- 9. Coupons 테이블: 유효기간+활성화 복합 인덱스
-- 문제: 현재 유효한 쿠폰 조회
// 최적화:
--   - SELECT * FROM coupons WHERE valid_from <= NOW() AND valid_until >= NOW() AND is_active = 1
--   - 배치: 만료된 쿠폰 상태 업데이트 시
// 쿠�리 예: SELECT * FROM coupons WHERE is_active = 1 AND valid_until >= CURRENT_TIMESTAMP
ALTER TABLE coupons
ADD INDEX idx_active_valid (is_active, valid_until DESC) COMMENT '활성화+유효종료일 복합 인덱스 (유효 쿠폰 조회 최적화)';

-- 10. Webhook_Logs 테이블: 상태+생성시간 복합 인덱스
-- 문제: 웹훅 처리 현황 조회 및 재시도 대상 찾기
// 최적화:
--   - SELECT * FROM webhook_logs WHERE status = 'FAILED' ORDER BY created_at ASC
--   - 최근 웹훅: SELECT * FROM webhook_logs WHERE status = 'COMPLETED' ORDER BY created_at DESC LIMIT 100
// 쿼리 예: SELECT * FROM webhook_logs WHERE status = 'PROCESSING' OR status = 'QUEUED' ORDER BY created_at ASC
ALTER TABLE webhook_logs
ADD INDEX idx_status_created (status, created_at DESC) COMMENT '상태+생성시간 복합 인덱스 (웹훅 로그 조회 최적화)';

-- ============================================
-- [SUPPLEMENTARY] 추가 최적화 인덱스 (선택사항)
-- ============================================

-- Point_Histories 테이블: 사용자+생성시간 복합 인덱스 (포인트 이력 조회 최적화)
-- 문제: 사용자의 최근 포인트 변동 내역 조회
// 쿼리 예: SELECT * FROM point_histories WHERE user_id = 1 AND type = 'EARNED' ORDER BY created_at DESC
ALTER TABLE point_histories
ADD INDEX idx_user_created (user_id, created_at DESC) COMMENT '사용자+생성시간 복합 인덱스 (포인트 이력 조회 최적화)';

-- Restock_Notifications 테이블: 상태+생성시간 복합 인덱스
// 쿼리 예: SELECT * FROM restock_notifications WHERE status = 'PENDING' ORDER BY created_at ASC
ALTER TABLE restock_notifications
ADD INDEX idx_status_created (status, created_at DESC) COMMENT '상태+생성시간 복합 인덱스 (알림 처리 최적화)';

-- ============================================
-- 인덱스 추가 완료 메시지
-- ============================================
-- 총 12개 인덱스 추가 완료
-- Priority 1: 3개 (즉시 적용)
-- Priority 2: 5개 (1개월 내)
-- Priority 3: 2개 (분기별)
-- Supplementary: 2개 (선택)
--
-- 다음 단계:
-- 1. 쿼리 성능 모니터링: MySQL SLOW_QUERY_LOG 활성화
-- 2. 인덱스 사용률 확인: SHOW STATISTICS FROM each table
-- 3. 쿼리 최적화: EXPLAIN 분석으로 인덱스 사용 여부 확인
-- 4. 정기적 유지보수: ANALYZE TABLE를 월 1회 실행
-- ============================================
