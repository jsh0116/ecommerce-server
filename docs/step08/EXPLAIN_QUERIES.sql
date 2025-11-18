-- ============================================
-- STEP08: EXPLAIN 분석 - 실행 쿼리
-- ============================================
-- 이 파일은 실제 데이터베이스에서 실행하여
-- 최적화 전후의 쿼리 실행계획을 비교하는 데 사용됩니다.
--
-- 실행 방법:
-- 1. 테스트 데이터 생성 (OrderRepositoryOptimizationTest/ReservationRepositoryOptimizationTest)
-- 2. 아래의 EXPLAIN 쿼리를 순서대로 실행
-- 3. 결과 비교 (type, key, rows 항목 확인)
-- ============================================

USE hhplus_ecommerce;

-- ============================================
-- 준비: 테스트 데이터 삽입 (옵션)
-- ============================================

-- Orders 테이블 테스트 데이터 (100개 주문)
-- INSERT INTO orders (user_id, status, total_amount, created_at, updated_at)
-- SELECT 1, 'PAID', 100000, DATE_SUB(NOW(), INTERVAL RAND()*30 DAY), NOW()
-- FROM (SELECT 1 UNION SELECT 2 UNION SELECT 3 ...) t
-- LIMIT 100;

-- Reservations 테이블 테스트 데이터 (1000개 예약)
-- INSERT INTO reservations (order_id, sku, quantity, status, expires_at, created_at, updated_at)
-- SELECT ROW_NUMBER() OVER(), CONCAT('SKU-', @row := @row+1), 1, 'ACTIVE', DATE_ADD(NOW(), INTERVAL RAND()*30 DAY), NOW(), NOW()
-- FROM (SELECT 1 UNION SELECT 2 ... ) t, (SELECT @row := 0) r
-- LIMIT 1000;

-- ============================================
-- SECTION 1: Orders 테이블 쿼리 분석
-- ============================================

-- ==== 쿼리 1-1: 사용자별 주문 조회 (최적화 전) ====
-- 분석: 단순 WHERE 조건 (인덱스 미지정)
-- 예상: ALL (풀 테이블 스캔)
EXPLAIN FORMAT=JSON
SELECT o.id, o.order_number, o.user_id, o.status, o.created_at
FROM orders o
WHERE o.user_id = 1
ORDER BY o.created_at DESC;

-- ==== 쿼리 1-2: 사용자별 주문 조회 (최적화 후) ====
-- 분석: idx_user_status_paid 인덱스 활용
-- 예상: ref (인덱스 레인지 스캔)
EXPLAIN FORMAT=JSON
SELECT o.id, o.order_number, o.user_id, o.status, o.created_at
FROM orders o
WHERE o.user_id = 1
ORDER BY o.created_at DESC;
-- 사용될 인덱스: idx_user_status_paid(user_id, status, paid_at DESC)

-- ==== 쿼리 1-3: 사용자+상태별 주문 조회 (최적화 전) ====
-- 분석: 두 개의 WHERE 조건
-- 예상: ref + filesort
EXPLAIN FORMAT=JSON
SELECT o.id, o.order_number, o.user_id, o.status, o.created_at
FROM orders o
WHERE o.user_id = 1
AND o.status = 'PAID'
ORDER BY o.created_at DESC;

-- ==== 쿼리 1-4: 사용자+상태별 주문 조회 (최적화 후) ====
-- 분석: 복합 인덱스로 두 조건 모두 처리
-- 예상: ref (정렬 포함)
-- 사용될 인덱스: idx_user_status_paid(user_id, status, paid_at DESC)
EXPLAIN FORMAT=JSON
SELECT o.id, o.order_number, o.user_id, o.status, o.created_at
FROM orders o
WHERE o.user_id = 1
AND o.status = 'PAID'
ORDER BY o.created_at DESC;

-- ==== 쿼리 1-5: 상태별 주문 조회 (배치용) ====
-- 분석: status만으로 필터링 (복합 인덱스 부분 사용)
-- 예상: range
EXPLAIN FORMAT=JSON
SELECT o.id, o.order_number, o.user_id, o.status, o.created_at
FROM orders o
WHERE o.status = 'PENDING_PAYMENT'
ORDER BY o.created_at DESC;

-- ==== 쿼리 1-6: 주문 배치 UPDATE 분석 ====
-- 분석: UPDATE 쿼리의 WHERE 절 최적화
-- 예상: range (시간 범위 포함)
EXPLAIN FORMAT=JSON
UPDATE orders o
SET o.status = 'CANCELLED', o.updated_at = CURRENT_TIMESTAMP
WHERE o.status = 'PENDING_PAYMENT'
AND o.created_at <= DATE_SUB(NOW(), INTERVAL 12 HOUR);

-- ============================================
-- SECTION 2: Reservations 테이블 쿼리 분석
-- ============================================

-- ==== 쿼리 2-1: 만료된 예약 조회 (최적화 전) ====
-- 분석: 상태 + 시간 조건 (미지정)
-- 예상: ALL (풀 테이블 스캔)
EXPLAIN FORMAT=JSON
SELECT r.id, r.order_id, r.sku, r.quantity, r.status, r.expires_at
FROM reservations r
WHERE r.status = 'ACTIVE'
AND r.expires_at <= CURRENT_TIMESTAMP;

-- ==== 쿼리 2-2: 만료된 예약 조회 (최적화 후) ====
-- 분석: idx_status_expires 복합 인덱스 활용
-- 예상: range
EXPLAIN FORMAT=JSON
SELECT r.id, r.order_id, r.sku, r.quantity, r.status, r.expires_at
FROM reservations r
WHERE r.status = 'ACTIVE'
AND r.expires_at <= CURRENT_TIMESTAMP;
-- 사용될 인덱스: idx_status_expires(status, expires_at)

-- ==== 쿼리 2-3: 배치 UPDATE - 예약 만료 처리 ====
-- 분석: 만료된 예약을 한 번에 EXPIRED로 변경
-- 예상: range (인덱스로 범위 스캔)
EXPLAIN FORMAT=JSON
UPDATE reservations r
SET r.status = 'EXPIRED', r.updated_at = CURRENT_TIMESTAMP
WHERE r.status = 'ACTIVE'
AND r.expires_at <= CURRENT_TIMESTAMP;
-- 사용될 인덱스: idx_status_expires(status, expires_at)

-- ==== 쿼리 2-4: 주문별 예약 취소 ====
-- 분석: 특정 주문의 모든 예약 취소
-- 예상: ref (PK 또는 외래키 인덱스)
EXPLAIN FORMAT=JSON
UPDATE reservations r
SET r.status = 'CANCELLED', r.updated_at = CURRENT_TIMESTAMP
WHERE r.order_id = 1
AND r.status = 'ACTIVE';

-- ==== 쿼리 2-5: SKU별 예약 조회 ====
-- 분석: SKU 기준 조회 (기본 인덱스)
-- 예상: ref
EXPLAIN FORMAT=JSON
SELECT r.id, r.order_id, r.sku, r.quantity, r.status
FROM reservations r
WHERE r.sku = 'SKU-TEST-001';

-- ==== 쿼리 2-6: SKU+상태별 예약 조회 ====
-- 분석: 복합 조건 (복합 인덱스 사용 가능)
-- 예상: ref
EXPLAIN FORMAT=JSON
SELECT r.id, r.order_id, r.sku, r.quantity, r.status
FROM reservations r
WHERE r.sku = 'SKU-TEST-001'
AND r.status = 'ACTIVE';

-- ============================================
-- SECTION 3: 성능 비교 분석
-- ============================================

-- ==== 성능 측정 쿼리 1: 응답 시간 측정 (Orders) ====
-- 최적화 전 (캐시 제거하고 실행)
-- FLUSH QUERY CACHE;
SELECT SQL_NO_CACHE COUNT(*) as row_count, AVG(total_amount) as avg_amount
FROM orders o
WHERE o.user_id = 1
ORDER BY o.created_at DESC;

-- ==== 성능 측정 쿼리 2: 응답 시간 측정 (Reservations) ====
SELECT SQL_NO_CACHE COUNT(*) as expired_count
FROM reservations r
WHERE r.status = 'ACTIVE'
AND r.expires_at <= CURRENT_TIMESTAMP;

-- ============================================
-- SECTION 4: 인덱스 사용률 확인
-- ============================================

-- ==== 인덱스 생성 확인 ====
SHOW INDEXES FROM orders WHERE Key_name IN ('idx_user_status_paid');
SHOW INDEXES FROM reservations WHERE Key_name IN ('idx_status_expires');

-- ==== 인덱스 크기 확인 ====
SELECT
    table_name,
    index_name,
    seq_in_index,
    column_name,
    collation,
    cardinality,
    is_visible
FROM information_schema.STATISTICS
WHERE table_schema = 'hhplus_ecommerce'
AND table_name IN ('orders', 'reservations')
AND index_name IN ('idx_user_status_paid', 'idx_status_expires')
ORDER BY table_name, index_name, seq_in_index;

-- ==== 인덱스 성능 통계 (Performance Schema) ====
-- 각 인덱스의 실제 사용률 확인
SELECT
    object_schema,
    object_name,
    index_name,
    count_read,
    count_write,
    count_delete,
    count_update
FROM performance_schema.table_io_waits_summary_by_index_usage
WHERE object_schema = 'hhplus_ecommerce'
AND object_name IN ('orders', 'reservations')
ORDER BY object_name, index_name;

-- ============================================
-- SECTION 5: 실행계획 상세 분석 (MySQL 8.0+)
-- ============================================

-- ==== 쿼리 1: 자세한 실행계획 분석 ====
EXPLAIN FORMAT=JSON
SELECT o.id, o.order_number, o.user_id, o.status, o.created_at
FROM orders o
WHERE o.user_id = 1
AND o.status = 'PAID'
ORDER BY o.created_at DESC;

-- ==== 쿼리 2: 배치 UPDATE 상세 분석 ====
EXPLAIN FORMAT=JSON
UPDATE reservations r
SET r.status = 'EXPIRED', r.updated_at = CURRENT_TIMESTAMP
WHERE r.status = 'ACTIVE'
AND r.expires_at <= CURRENT_TIMESTAMP;

-- ============================================
-- SECTION 6: 대규모 데이터셋 성능 테스트
-- ============================================

-- ==== 대규모 주문 데이터로 성능 측정 ====
-- 사전 조건: 100,000개 이상의 주문 데이터 필요
SELECT SQL_NO_CACHE COUNT(*) as total_orders
FROM orders o
WHERE o.user_id = 1
AND o.status IN ('PAID', 'SHIPPED')
ORDER BY o.created_at DESC
LIMIT 20;

-- ==== 대규모 예약 데이터로 성능 측정 ====
-- 사전 조건: 100,000개 이상의 예약 데이터 필요
SELECT SQL_NO_CACHE COUNT(*) as expired_reservations
FROM reservations r
WHERE r.status = 'ACTIVE'
AND r.expires_at <= CURRENT_TIMESTAMP;

-- ============================================
-- SECTION 7: 쿼리 최적화 검증
-- ============================================

-- ==== 커버링 인덱스 검증 (Using index) ====
-- 아래 쿼리의 Extra 필드에 "Using index"가 있으면 인덱스만으로 처리
EXPLAIN FORMAT=JSON
SELECT o.user_id, o.status, o.created_at
FROM orders o
WHERE o.user_id = 1
AND o.status = 'PAID';
-- 예상: Using index (idx_user_status_paid가 모든 컬럼을 포함)

-- ==== 정렬 최적화 검증 (filesort 제거) ====
-- Extra 필드에 "Using filesort"가 없으면 인덱스로 정렬 처리
EXPLAIN FORMAT=JSON
SELECT o.id, o.order_number, o.user_id, o.status, o.created_at
FROM orders o
WHERE o.user_id = 1
ORDER BY o.created_at DESC;
-- 예상: Using index + No filesort

-- ============================================
-- 결과 해석 가이드
-- ============================================
-- type 필드 해석:
-- - system: 시스템 테이블 (최고 성능)
-- - const: 상수 조회
-- - eq_ref: 유니크 인덱스 조회
-- - ref: 인덱스 레인지 스캔 (좋음)
-- - range: 범위 스캔 (좋음)
-- - index: 인덱스 풀 스캔 (나쁨)
-- - ALL: 테이블 풀 스캔 (최악)
--
-- key 필드:
-- - null: 인덱스 미사용 (주의 필요)
-- - idx_user_status_paid 등: 지정된 인덱스 사용 (최적화 성공)
--
-- rows 필드:
-- - 스캔할 예상 행 수
-- - 작을수록 좋음 (수십 vs 백만)
--
-- Extra 필드:
-- - Using index: 커버링 인덱스 (최고)
-- - Using where: WHERE 절 필터링 추가
-- - Using filesort: 별도 정렬 작업 (개선 필요)
-- - Using temporary: 임시 테이블 (개선 필요)
-- ============================================
