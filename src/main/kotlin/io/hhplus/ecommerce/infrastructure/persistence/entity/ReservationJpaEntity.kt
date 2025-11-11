package io.hhplus.ecommerce.infrastructure.persistence.entity

import jakarta.persistence.*
import java.time.LocalDateTime

enum class ReservationStatusJpa {
    ACTIVE,      // 예약 활성 (15분 TTL 진행중)
    CONFIRMED,   // 예약 확정 (결제 완료)
    EXPIRED,     // 예약 만료 (결제 미완료 및 TTL 초과)
    CANCELLED    // 예약 취소 (주문 취소)
}

/**
 * 재고 예약 JPA Entity
 *
 * Saga 패턴 구현: 주문 생성 시 재고 예약, 15분 TTL로 자동 만료
 * - 주문 생성 → ACTIVE 상태로 예약 기록
 * - 결제 완료 → CONFIRMED 상태로 변경 + 재고 실제 차감
 * - 15분 경과 또는 결제 실패 → EXPIRED/CANCELLED 상태로 변경 + 재고 복구
 */
@Entity
@Table(
    name = "reservations",
    indexes = [
        Index(name = "idx_reservations_order_id", columnList = "order_id"),
        Index(name = "idx_reservations_sku", columnList = "sku"),
        Index(name = "idx_reservations_status", columnList = "status"),
        Index(name = "idx_reservations_expires_at", columnList = "expires_at")
    ]
)
class ReservationJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String = "",

    @Column(nullable = false)
    var orderId: Long = 0L,

    @Column(nullable = false, length = 100)
    var sku: String = "",

    @Column(nullable = false)
    var quantity: Int = 0,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: ReservationStatusJpa = ReservationStatusJpa.ACTIVE,

    @Column(nullable = false)
    var expiresAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    /**
     * 예약이 만료되었는지 확인
     */
    fun isExpired(): Boolean = status == ReservationStatusJpa.ACTIVE && LocalDateTime.now().isAfter(expiresAt)

    /**
     * 예약 확정 (결제 완료 시)
     */
    fun confirm() {
        require(status == ReservationStatusJpa.ACTIVE) { "이미 처리된 예약입니다" }
        status = ReservationStatusJpa.CONFIRMED
        updatedAt = LocalDateTime.now()
    }

    /**
     * 예약 만료 처리
     */
    fun expire() {
        require(status == ReservationStatusJpa.ACTIVE) { "활성 예약만 만료 처리 가능합니다" }
        require(isExpired()) { "아직 TTL이 남아있습니다" }
        status = ReservationStatusJpa.EXPIRED
        updatedAt = LocalDateTime.now()
    }

    /**
     * 예약 취소
     */
    fun cancel() {
        require(status == ReservationStatusJpa.ACTIVE) { "활성 예약만 취소 가능합니다" }
        status = ReservationStatusJpa.CANCELLED
        updatedAt = LocalDateTime.now()
    }
}
