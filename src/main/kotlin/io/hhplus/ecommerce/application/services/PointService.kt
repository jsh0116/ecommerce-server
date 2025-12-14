package io.hhplus.ecommerce.application.services

/**
 * 포인트 서비스 인터페이스
 *
 * 사용자 포인트 적립 및 관리를 담당합니다.
 */
interface PointService {
    /**
     * 포인트 적립
     *
     * @param userId 사용자 ID
     * @param points 적립할 포인트
     * @param reason 적립 사유
     * @return 적립 후 총 포인트
     */
    fun addPoints(userId: Long, points: Long, reason: String): Long

    /**
     * 포인트 차감
     *
     * @param userId 사용자 ID
     * @param points 차감할 포인트
     * @param reason 차감 사유
     * @return 차감 후 총 포인트
     */
    fun deductPoints(userId: Long, points: Long, reason: String): Long

    /**
     * 포인트 조회
     *
     * @param userId 사용자 ID
     * @return 현재 포인트
     */
    fun getPoints(userId: Long): Long
}
