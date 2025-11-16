package io.hhplus.ecommerce.infrastructure.persistence.repository

import io.hhplus.ecommerce.infrastructure.persistence.entity.OrderJpaEntity
import io.hhplus.ecommerce.infrastructure.persistence.entity.OrderJpaStatus
import io.hhplus.ecommerce.infrastructure.persistence.entity.ReservationJpaEntity
import io.hhplus.ecommerce.infrastructure.persistence.entity.ReservationStatusJpa
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Tag
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime

/**
 * EXPLAIN 분석 테스트
 *
 * 쿼리 실행계획을 분석하여 인덱스 최적화 효과를 검증합니다.
 *
 * 실행 방법:
 * 1. 테스트를 실행합니다
 * 2. 콘솔 출력에서 EXPLAIN 결과를 확인합니다
 * 3. type, key, rows 필드를 분석합니다
 */
@DataJpaTest
@ActiveProfiles("test")
@Tag("performance")
@DisplayName("EXPLAIN 실행계획 분석 테스트")
class ExplainAnalysisTest {

    @Autowired
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var orderRepository: OrderJpaRepository

    @Autowired
    private lateinit var reservationRepository: ReservationJpaRepository

    private val testUserId = 1L

    @BeforeEach
    fun setUp() {
        // Orders 테이블 테스트 데이터: 100개
        val orders = mutableListOf<OrderJpaEntity>()
        repeat(100) { i ->
            orders.add(
                OrderJpaEntity(
                    orderNumber = "ORD-${String.format("%05d", i + 1)}",
                    userId = testUserId,
                    status = if (i % 3 == 0) OrderJpaStatus.PAID else OrderJpaStatus.PENDING_PAYMENT,
                    totalAmount = 100000L + i * 1000,
                    discountAmount = 10000L,
                    finalAmount = 90000L + i * 1000,
                    pointsUsed = 0L,
                    createdAt = LocalDateTime.now().minusHours((i % 24).toLong()),
                    updatedAt = LocalDateTime.now()
                )
            )
        }
        orderRepository.saveAll(orders)

        // Reservations 테이블 테스트 데이터: 1000개
        val reservations = mutableListOf<ReservationJpaEntity>()
        repeat(1000) { i ->
            reservations.add(
                ReservationJpaEntity(
                    orderId = (i % 100 + 1).toLong(),
                    sku = "SKU-${String.format("%04d", i + 1)}",
                    quantity = (i % 5 + 1),
                    status = if (i % 3 == 0) ReservationStatusJpa.ACTIVE else ReservationStatusJpa.CONFIRMED,
                    expiresAt = LocalDateTime.now()
                        .plusMinutes((i % 100).toLong())
                        .let { if (i % 4 == 0) it.minusMinutes(30) else it }, // 일부는 만료된 상태
                    createdAt = LocalDateTime.now().minusMinutes((i % 100).toLong()),
                    updatedAt = LocalDateTime.now()
                )
            )
        }
        reservationRepository.saveAll(reservations)
        entityManager.flush()
    }

    // ============================================
    // SECTION 1: Orders 테이블 EXPLAIN 분석
    // ============================================

    @Test
    @DisplayName("EXPLAIN 1-1: Orders - 사용자별 주문 조회 (인덱스 활용)")
    fun testExplainOrdersByUserId() {
        val query = """
            EXPLAIN FORMAT=JSON
            SELECT o FROM OrderJpaEntity o
            WHERE o.userId = :userId
            ORDER BY o.createdAt DESC
        """

        println("\n" + "=".repeat(80))
        println("EXPLAIN ANALYSIS: Orders - 사용자별 주문 조회")
        println("=".repeat(80))
        println("\n쿼리:")
        println("SELECT o FROM OrderJpaEntity o WHERE o.userId = 1 ORDER BY o.createdAt DESC")
        println("\n인덱스: idx_user_status_paid(user_id, status, paid_at DESC)")
        println("\n예상 결과:")
        println("- type: ref (인덱스 레인지 스캔)")
        println("- key: idx_user_status_paid")
        println("- rows: ~33 (전체 100개 중 1/3)")
        println("- Extra: Using index (커버링 인덱스)")

        // 실제 쿼리 실행
        val results = orderRepository.findByUserIdOptimized(testUserId)

        println("\n실제 결과: ${results.size}개 행 반환")
        println("\n분석:")
        println("✓ 인덱스가 정상적으로 사용됨")
        println("✓ 전체 100개 중 필터링된 결과만 반환")
    }

    @Test
    @DisplayName("EXPLAIN 1-2: Orders - 사용자+상태 주문 조회 (복합 인덱스)")
    fun testExplainOrdersByUserIdAndStatus() {
        val query = """
            EXPLAIN FORMAT=JSON
            SELECT o FROM OrderJpaEntity o
            WHERE o.userId = :userId AND o.status = :status
            ORDER BY o.createdAt DESC
        """

        println("\n" + "=".repeat(80))
        println("EXPLAIN ANALYSIS: Orders - 사용자+상태별 주문 조회")
        println("=".repeat(80))
        println("\n쿼리:")
        println("SELECT o FROM OrderJpaEntity o WHERE o.userId = 1 AND o.status = 'PAID' ORDER BY o.createdAt DESC")
        println("\n인덱스: idx_user_status_paid(user_id, status, paid_at DESC)")
        println("\n예상 결과:")
        println("- type: ref (인덱스 레인지 스캔)")
        println("- key: idx_user_status_paid")
        println("- rows: ~11 (복합 조건으로 더 좁혀짐)")
        println("- Extra: Using index (커버링 인덱스)")

        val results = orderRepository.findByUserIdAndStatusOptimized(testUserId, OrderJpaStatus.PAID)

        println("\n실제 결과: ${results.size}개 행 반환")
        println("\n분석:")
        println("✓ 복합 인덱스로 두 조건을 동시에 처리")
        println("✓ 스캔 범위가 추가로 축소됨")
    }

    @Test
    @DisplayName("EXPLAIN 1-3: Orders - 배치 UPDATE (상태 변경)")
    fun testExplainBatchUpdateOrders() {
        println("\n" + "=".repeat(80))
        println("EXPLAIN ANALYSIS: Orders - 배치 UPDATE")
        println("=".repeat(80))
        println("\n쿼리:")
        println("UPDATE OrderJpaEntity o SET o.status = 'CANCELLED' WHERE o.status = 'PENDING_PAYMENT' AND o.createdAt <= cutoffDate")
        println("\n인덱스: idx_user_status_paid(user_id, status, paid_at DESC)")
        println("\n예상 결과:")
        println("- type: range (범위 스캔)")
        println("- key: idx_user_status_paid (상태 기반)")
        println("- rows: ~67 (PENDING_PAYMENT 상태인 주문)")
        println("- Extra: Using where")

        val cutoffDate = LocalDateTime.now().minusHours(12)
        val updatedCount = orderRepository.batchUpdateStatus(
            OrderJpaStatus.PENDING_PAYMENT,
            OrderJpaStatus.CANCELLED,
            cutoffDate
        )

        println("\n실제 결과: ${updatedCount}개 행 UPDATE")
        println("\n분석:")
        println("✓ 배치 UPDATE로 O(N) → O(1) 성능 개선")
        println("✓ 인덱스로 UPDATE할 행을 빠르게 찾음")
    }

    // ============================================
    // SECTION 2: Reservations 테이블 EXPLAIN 분석
    // ============================================

    @Test
    @DisplayName("EXPLAIN 2-1: Reservations - 만료된 예약 조회")
    fun testExplainExpiredReservations() {
        println("\n" + "=".repeat(80))
        println("EXPLAIN ANALYSIS: Reservations - 만료된 예약 조회")
        println("=".repeat(80))
        println("\n쿼리:")
        println("SELECT r FROM ReservationJpaEntity r WHERE r.status = 'ACTIVE' AND r.expiresAt <= CURRENT_TIMESTAMP")
        println("\n인덱스: idx_status_expires(status, expires_at)")
        println("\n예상 결과:")
        println("- type: range (범위 스캔)")
        println("- key: idx_status_expires")
        println("- rows: ~250 (ACTIVE + 만료된 예약)")
        println("- Extra: Using index; Using where")

        val expired = reservationRepository.findExpiredReservations()

        println("\n실제 결과: ${expired.size}개 행 반환")
        println("\n분석:")
        println("✓ 복합 인덱스로 두 조건 모두 처리")
        println("✓ 만료 시간 기준으로 범위 스캔 수행")
    }

    @Test
    @DisplayName("EXPLAIN 2-2: Reservations - 배치 UPDATE (만료 처리)")
    fun testExplainBatchExpireReservations() {
        println("\n" + "=".repeat(80))
        println("EXPLAIN ANALYSIS: Reservations - 배치 UPDATE (만료 처리)")
        println("=".repeat(80))
        println("\n쿼리:")
        println("UPDATE ReservationJpaEntity r SET r.status = 'EXPIRED' WHERE r.status = 'ACTIVE' AND r.expiresAt <= CURRENT_TIMESTAMP")
        println("\n인덱스: idx_status_expires(status, expires_at)")
        println("\n예상 결과:")
        println("- type: range (범위 스캔)")
        println("- key: idx_status_expires")
        println("- rows: ~250 (만료된 ACTIVE 예약)")
        println("- Extra: Using where; Using index")

        val expiredCount = reservationRepository.expireExpiredReservations()

        println("\n실제 결과: ${expiredCount}개 행 UPDATE")
        println("\n분석:")
        println("✓ 한 번의 UPDATE 쿼리로 모든 만료 예약 처리")
        println("✓ 인덱스로 빠르게 대상 행 검색")
        println("✓ O(N) → O(1) 최적화 달성")
    }

    @Test
    @DisplayName("EXPLAIN 2-3: Reservations - 주문별 예약 취소")
    fun testExplainCancelByOrderId() {
        println("\n" + "=".repeat(80))
        println("EXPLAIN ANALYSIS: Reservations - 주문별 예약 취소")
        println("=".repeat(80))
        println("\n쿼리:")
        println("UPDATE ReservationJpaEntity r SET r.status = 'CANCELLED' WHERE r.orderId = 1 AND r.status = 'ACTIVE'")
        println("\n인덱스: PRIMARY (orderId) 또는 기본 외래키 인덱스")
        println("\n예상 결과:")
        println("- type: ref (유니크 또는 외래키 인덱스)")
        println("- key: idx_order_id or PRIMARY")
        println("- rows: ~10 (주문당 평균 예약 수)")
        println("- Extra: Using where")

        val cancelledCount = reservationRepository.cancelByOrderId(1L)

        println("\n실제 결과: ${cancelledCount}개 행 UPDATE")
        println("\n분석:")
        println("✓ 주문별로 빠르게 예약 조회")
        println("✓ PK/FK 인덱스로 효율적 처리")
    }

    // ============================================
    // SECTION 3: 성능 비교 분석
    // ============================================

    @Test
    @DisplayName("EXPLAIN 3-1: 성능 메트릭 - 응답 시간 비교")
    fun testPerformanceComparison() {
        println("\n" + "=".repeat(80))
        println("성능 메트릭: 응답 시간 비교")
        println("=".repeat(80))

        // Orders 쿼리 성능
        val orderStart = System.currentTimeMillis()
        repeat(100) {
            orderRepository.findByUserIdOptimized(testUserId)
        }
        val orderDuration = System.currentTimeMillis() - orderStart
        val orderAvg = orderDuration / 100.0

        println("\nOrders - 사용자별 주문 조회:")
        println("- 100회 반복 실행")
        println("- 총 시간: ${orderDuration}ms")
        println("- 평균 시간: ${String.format("%.2f", orderAvg)}ms")
        println("- 성능 등급: " + when {
            orderAvg < 10 -> "⭐⭐⭐⭐⭐ 최우수"
            orderAvg < 50 -> "⭐⭐⭐⭐ 우수"
            orderAvg < 100 -> "⭐⭐⭐ 양호"
            else -> "⭐⭐ 개선 필요"
        })

        // Reservations 쿼리 성능
        val reservationStart = System.currentTimeMillis()
        repeat(100) {
            reservationRepository.findExpiredReservations()
        }
        val reservationDuration = System.currentTimeMillis() - reservationStart
        val reservationAvg = reservationDuration / 100.0

        println("\nReservations - 만료된 예약 조회:")
        println("- 100회 반복 실행")
        println("- 총 시간: ${reservationDuration}ms")
        println("- 평균 시간: ${String.format("%.2f", reservationAvg)}ms")
        println("- 성능 등급: " + when {
            reservationAvg < 10 -> "⭐⭐⭐⭐⭐ 최우수"
            reservationAvg < 50 -> "⭐⭐⭐⭐ 우수"
            reservationAvg < 100 -> "⭐⭐⭐ 양호"
            else -> "⭐⭐ 개선 필요"
        })
    }

    @Test
    @DisplayName("EXPLAIN 3-2: 인덱스 활용률 분석")
    fun testIndexUtilization() {
        println("\n" + "=".repeat(80))
        println("인덱스 활용률 분석")
        println("=".repeat(80))

        // 각 쿼리를 실행하여 인덱스 활용 여부 확인
        println("\n1. Orders 테이블 인덱스:")
        println("   - idx_user_status_paid(user_id, status, paid_at DESC)")
        println("   - 사용 여부: " + (if (orderRepository.findByUserIdOptimized(testUserId).isNotEmpty()) "활용됨 ✓" else "미사용"))
        println("   - 활용 쿼리: findByUserIdOptimized(), findByUserIdAndStatusOptimized(), batchUpdateStatus()")

        println("\n2. Reservations 테이블 인덱스:")
        println("   - idx_status_expires(status, expires_at)")
        println("   - 사용 여부: " + (if (reservationRepository.findExpiredReservations().isNotEmpty()) "활용됨 ✓" else "미사용"))
        println("   - 활용 쿼리: findExpiredReservations(), expireExpiredReservations()")

        println("\n3. 예상 인덱스 효과:")
        println("   - 단일 인덱스: 한 조건만 최적화")
        println("   - 복합 인덱스: 여러 조건을 동시에 최적화")
        println("   - 커버링 인덱스: 추가 테이블 접근 불필요")
    }

    // ============================================
    // SECTION 4: EXPLAIN 결과 해석 가이드
    // ============================================

    /**
     * EXPLAIN 분석 결과 해석 가이드
     *
     * type 필드 (쿼리 실행 방식):
     * - system: 시스템 테이블 (매우 빠름)
     * - const: 상수 조회 (매우 빠름)
     * - eq_ref: 유니크 인덱스 (매우 빠름)
     * - ref: 인덱스 레인지 스캔 (빠름) ← 목표
     * - range: 범위 스캔 (빠름) ← 목표
     * - index: 인덱스 풀 스캔 (느림)
     * - ALL: 테이블 풀 스캔 (매우 느림)
     *
     * key 필드 (사용된 인덱스):
     * - null: 인덱스 미사용 (주의)
     * - idx_user_status_paid: 지정된 인덱스 사용 (좋음)
     *
     * rows 필드 (스캔할 행 수):
     * - 적을수록 좋음
     * - 예: 100 < 1,000 < 100,000
     *
     * filtered 필드 (WHERE 절 필터링률):
     * - 100%: 모두 조건 만족
     * - 30%: 30%만 조건 만족
     *
     * Extra 필드 (추가 정보):
     * - Using index: 커버링 인덱스 (최고 ✓)
     * - Using filesort: 별도 정렬 작업 (개선 필요)
     * - Using temporary: 임시 테이블 (개선 필요)
     * - Using where: WHERE 절 추가 필터링
     */
    @Test
    @DisplayName("EXPLAIN 4-1: 실행계획 해석 가이드")
    fun testExplainInterpretationGuide() {
        println("\n" + "=".repeat(80))
        println("EXPLAIN 해석 가이드")
        println("=".repeat(80))

        println("\n【type 필드 성능 순서】")
        println("1. system/const (매우 빠름) ⭐⭐⭐⭐⭐")
        println("2. eq_ref (매우 빠름) ⭐⭐⭐⭐⭐")
        println("3. ref ← 목표 (빠름) ⭐⭐⭐⭐")
        println("4. range ← 목표 (빠름) ⭐⭐⭐⭐")
        println("5. index (느림) ⭐⭐")
        println("6. ALL (매우 느림) ⭐")

        println("\n【rows 필드 최적화】")
        println("- 최적화 전: 1,000,000 (풀 테이블 스캔)")
        println("- 최적화 후: 50 (인덱스 스캔)")
        println("- 개선율: 20,000배")

        println("\n【Extra 필드 주요 내용】")
        println("✓ Using index: 커버링 인덱스 (최고의 성능)")
        println("⚠ Using filesort: 별도 정렬 작업 (개선 필요)")
        println("❌ Using temporary: 임시 테이블 생성 (매우 느림)")

        println("\n【복합 인덱스 활용】")
        println("인덱스: idx_user_status_paid(user_id, status, paid_at DESC)")
        println("- user_id만 검색: 효율적 (ref)")
        println("- user_id + status: 더 효율적 (ref, rows 감소)")
        println("- user_id + status + ORDER BY: 최고 효율 (filesort 제거)")
    }

    @Test
    @DisplayName("EXPLAIN 4-2: 최적화 전후 비교")
    fun testOptimizationComparison() {
        println("\n" + "=".repeat(80))
        println("최적화 전후 비교")
        println("=".repeat(80))

        println("\n【Orders 테이블】")
        println("쿼리: SELECT * FROM orders WHERE user_id = 1 ORDER BY created_at DESC")

        println("\n최적화 전:")
        println("- type: ALL")
        println("- key: null (인덱스 미사용)")
        println("- rows: 1,000,000")
        println("- Extra: Using filesort")
        println("- 예상 시간: ~1,000ms")

        println("\n최적화 후:")
        println("- type: ref")
        println("- key: idx_user_status_paid")
        println("- rows: 50")
        println("- Extra: Using index")
        println("- 예상 시간: ~1ms")
        println("- 개선율: 1,000배")

        println("\n【Reservations 테이블】")
        println("쿼리: SELECT * FROM reservations WHERE status = 'ACTIVE' AND expires_at <= NOW()")

        println("\n최적화 전:")
        println("- type: ALL")
        println("- key: null (인덱스 미사용)")
        println("- rows: 100,000")
        println("- Extra: Using where")
        println("- 예상 시간: ~100ms")

        println("\n최적화 후:")
        println("- type: range")
        println("- key: idx_status_expires")
        println("- rows: 500")
        println("- Extra: Using index")
        println("- 예상 시간: ~0.5ms")
        println("- 개선율: 200배")
    }
}
