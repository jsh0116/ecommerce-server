-- ============================================
-- STEP09: 데이터베이스 인덱스 최적화 - 리뷰 피드백 반영
-- MySQL 8.0+ / MariaDB 10.5+
-- ============================================
-- 목적: 기존 인덱스의 커버링 인덱스 최적화 및 누락된 컬럼 추가
--
-- 주요 개선사항:
-- 1. batchDecreaseStock 최적화: (sku, physical_stock) 복합 인덱스 추가
-- 2. 쿠폰 조회 최적화: valid_from 컬럼 추가
-- 3. 이전 인덱스 문제 해결: 비효율적 인덱스 구조 개선
-- ============================================

USE hhplus_ecommerce;

-- ============================================
-- [P0] Priority 0 - CRITICAL: 즉시 추가 필요 (신규)
-- ============================================

-- 1. Inventory 테이블: SKU+물리재고 복합 인덱스
-- 문제: batchDecreaseStock에서 WHERE sku IN (...) AND physical_stock >= ? 조건 사용
-- 최적화:
--   - 배치 UPDATE: UPDATE inventory SET physical_stock = physical_stock - ? WHERE sku IN (...) AND physical_stock >= ?
--   - 복합 인덱스로 스캔 범위를 한 번에 결정 가능
--   - 비관적 락과 함께 사용하여 동시성 제어 강화
-- 쿼리 예: UPDATE inventory SET physical_stock = physical_stock - 10 WHERE sku IN ('SKU-001', 'SKU-002') AND physical_stock >= 10
ALTER TABLE inventory
ADD INDEX idx_sku_physical_stock (sku, physical_stock) COMMENT 'SKU+물리재고 복합 인덱스 (배치 감소 최적화)';

-- ============================================
-- [P1] Priority 1 - CRITICAL: 즉시 추가 필요 (기존)
-- ============================================

-- Note: 다음 인덱스들은 이미 002_create_additional_indexes.sql에서 생성됨:
-- - idx_user_status_paid (orders 테이블)
-- - idx_status_expires (reservations 테이블)

-- idx_brand_category_active 개선
-- 문제: SELECT *를 사용하므로 커버링 인덱스가 될 수 없음
-- 해결책: 인덱스 사용 목적을 명확히 하고, 커버링 인덱스가 필요한 경우 별도로 지정
-- 현재: 인덱스를 통해 WHERE 절 필터링만 최적화 (커버링은 불가)
-- 권장: 자주 함께 조회되는 컬럼만 SELECT하는 쿼리 최적화 필요
-- ALTER TABLE products
-- ADD INDEX idx_brand_category_active_covering (brand, category, is_active, id, name, price, created_at);

-- ============================================
-- [P2] Priority 2 - HIGH: 1개월 내 추가 권장 (개선)
-- ============================================

-- 2. Coupons 테이블: 유효기간 인덱스 개선
-- 문제: valid_from 컬럼이 누락되어 있음
-- 최적화:
--   - 쿠폰 유효성 확인: WHERE valid_from <= NOW() AND valid_until >= NOW() AND is_active = 1
--   - 3개 컬럼 모두를 인덱스에 포함
-- 쿼리 예: SELECT * FROM coupons WHERE is_active = 1 AND valid_from <= CURRENT_TIMESTAMP AND valid_until >= CURRENT_TIMESTAMP
-- Note: 기존 idx_active_valid는 valid_until만 포함하고 있음
-- 해결책: 별도의 인덱스 추가 (기존 인덱스는 호환성 유지)
ALTER TABLE coupons
ADD INDEX idx_active_valid_from_until (is_active, valid_from, valid_until DESC) COMMENT '활성화+유효기간 복합 인덱스 (유효 쿠폰 조회 최적화)';

-- ============================================
-- [P3] Priority 3 - MEDIUM: 분기별 추가 권장
-- ============================================

-- 3. User_Coupons 테이블: 사용자+유효기간 복합 인덱스
-- 문제: 사용자의 유효한 쿠폰 조회 시 여러 조건 사용
-- 최적화:
--   - 유효한 쿠폰 조회: WHERE user_id = ? AND status = 'AVAILABLE' AND valid_from <= NOW() AND valid_until >= NOW()
--   - 4개 컬럼의 조합으로 스캔 범위 축소
-- 쿼리 예: SELECT * FROM user_coupons WHERE user_id = 1 AND status = 'AVAILABLE' AND valid_until >= CURRENT_TIMESTAMP
ALTER TABLE user_coupons
ADD INDEX idx_user_valid_from_until (user_id, valid_from, valid_until DESC) COMMENT '사용자+유효기간 복합 인덱스 (사용자 유효 쿠폰 조회 최적화)';

-- ============================================
-- [문제 해결 가이드] 비커버링 인덱스 처리
-- ============================================
-- Q: idx_brand_category_active가 커버링 인덱스가 아닌 이유?
-- A: SELECT *를 사용하면 인덱스에 없는 컬럼(description, viewCount 등)을 조회해야 하므로
--    반드시 테이블 접근(Table Access by Row ID)이 필요함
--
-- 해결 방법:
-- 1. 쿼리를 최적화하여 필요한 컬럼만 SELECT
--    SELECT id, brand, category, is_active, name, price FROM products
--    WHERE brand = ? AND category = ? AND is_active = 1
--
-- 2. 커버링 인덱스가 필요하다면 모든 SELECT 컬럼을 인덱스에 포함
--    ALTER TABLE products
--    ADD INDEX idx_brand_category_covering (brand, category, is_active, id, name, price)
--
-- 3. 인덱스 통계 확인
--    EXPLAIN FORMAT=JSON SELECT ... FROM products WHERE brand = ? AND category = ? AND is_active = 1
--
-- ============================================
-- [인덱스 생성 후 검증]
-- ============================================
-- 1. 인덱스 생성 완료 후 다음을 실행:
--    ANALYZE TABLE inventory, coupons, user_coupons;
--
-- 2. 쿼리 실행계획 확인:
--    EXPLAIN SELECT * FROM inventory WHERE sku IN ('SKU-001') AND physical_stock >= 10;
--    EXPLAIN SELECT * FROM coupons WHERE is_active = 1 AND valid_from <= NOW() AND valid_until >= NOW();
--
-- 3. 성능 비교:
--    SET SESSION profiling = 1;
--    [쿼리 실행]
--    SHOW PROFILES;
--    SHOW PROFILE FOR QUERY 1;

-- ============================================
-- [참고: 복합 인덱스의 컬럼 순서]
-- ============================================
-- 인덱스 컬럼 순서는 다음 규칙을 따릅니다:
-- 1. WHERE 조건의 등호(=) 사용 컬럼 먼저
-- 2. WHERE 조건의 범위(<, >, <=, >=) 사용 컬럼 다음
-- 3. ORDER BY 컬럼 마지막
-- 4. SELECT 컬럼 (커버링 인덱스로 만들 경우)
--
-- 예시: WHERE sku = ? AND physical_stock >= ? ORDER BY created_at DESC
-- 정상: INDEX(sku, physical_stock, created_at DESC)
-- 비효율: INDEX(physical_stock, sku) - 범위 조건이 먼저 오면 그 뒤의 컬럼은 인덱스 효과 없음
-- ============================================
