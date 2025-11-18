package io.hhplus.ecommerce.application.services

import io.hhplus.ecommerce.infrastructure.persistence.entity.ReservationJpaEntity
import io.hhplus.ecommerce.infrastructure.persistence.entity.ReservationStatusJpa
import io.hhplus.ecommerce.infrastructure.persistence.repository.ReservationJpaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 재고 예약 Saga 패턴 Service
 *
 * 주문 생성 시 재고를 예약하고, 15분 TTL로 자동 만료 처리
 * - ACTIVE: 예약 진행 중
 * - CONFIRMED: 결제 완료 (재고 차감 확정)
 * - EXPIRED: TTL 초과 (재고 복구)
 * - CANCELLED: 주문 취소 (재고 복구)
 */
@Service
class ReservationService(
    private val reservationRepository: ReservationJpaRepository,
    private val inventoryService: InventoryService
) {
    companion object {
        private const val RESERVATION_TTL_MINUTES = 15
    }

    /**
     * 재고 예약 생성 (주문 생성 시)
     *
     * TTL: 현재 + 15분
     */
    @Transactional
    fun createReservation(
        orderId: Long,
        sku: String,
        quantity: Int
    ): ReservationJpaEntity {
        // 재고 예약 (비관적 락 적용)
        inventoryService.reserveStock(sku, quantity)

        // 예약 레코드 생성
        val reservation = ReservationJpaEntity(
            orderId = orderId,
            sku = sku,
            quantity = quantity,
            expiresAt = LocalDateTime.now().plusMinutes(RESERVATION_TTL_MINUTES.toLong())
        )

        return reservationRepository.save(reservation)
    }

    /**
     * 예약 확정 (결제 완료 시)
     *
     * 예약된 재고를 실제로 차감
     */
    @Transactional
    fun confirmReservation(orderId: Long): ReservationJpaEntity {
        val reservation = reservationRepository.findByOrderId(orderId)
            ?: throw IllegalArgumentException("예약을 찾을 수 없습니다: $orderId")

        // 예약 확정
        reservation.confirm()

        // 재고 실제 차감
        inventoryService.confirmReservation(reservation.sku, reservation.quantity)

        return reservationRepository.save(reservation)
    }

    /**
     * 예약 취소 (주문 취소 시)
     *
     * 예약된 재고 해제
     */
    @Transactional
    fun cancelReservation(orderId: Long): ReservationJpaEntity {
        val reservation = reservationRepository.findByOrderId(orderId)
            ?: throw IllegalArgumentException("예약을 찾을 수 없습니다: $orderId")

        // 예약 취소
        reservation.cancel()

        // 예약된 재고 해제
        inventoryService.cancelReservation(reservation.sku, reservation.quantity)

        return reservationRepository.save(reservation)
    }

    /**
     * 만료된 예약 처리 (Scheduler에서 호출)
     *
     * TTL 초과된 예약을 EXPIRED로 변경하고 재고 복구
     */
    @Transactional
    fun expireReservations(): Int {
        val expiredReservations = reservationRepository.findExpiredReservations()

        for (reservation in expiredReservations) {
            try {
                // 예약 만료 처리
                reservation.expire()

                // 예약된 재고 해제 (예약 취소)
                inventoryService.cancelReservation(reservation.sku, reservation.quantity)

                reservationRepository.save(reservation)
            } catch (e: Exception) {
                // 로그 기록 후 계속 진행
                println("예약 만료 처리 실패: ${reservation.id}, ${e.message}")
            }
        }

        return expiredReservations.size
    }

    /**
     * 예약 조회
     */
    @Transactional(readOnly = true)
    fun getReservation(orderId: Long): ReservationJpaEntity? {
        return reservationRepository.findByOrderId(orderId)
    }

    /**
     * 활성 예약 조회
     */
    @Transactional(readOnly = true)
    fun getActiveReservations(): List<ReservationJpaEntity> {
        return reservationRepository.findByStatus(ReservationStatusJpa.ACTIVE)
    }

    /**
     * 만료 임박 예약 조회 (모니터링용)
     */
    @Transactional(readOnly = true)
    fun getExpiringReservations(minutesBefore: Long = 1): List<ReservationJpaEntity> {
        val now = LocalDateTime.now()
        val threshold = now.plusMinutes(minutesBefore)

        return reservationRepository.findByStatus(ReservationStatusJpa.ACTIVE)
            .filter { it.expiresAt.isBefore(threshold) }
    }
}
