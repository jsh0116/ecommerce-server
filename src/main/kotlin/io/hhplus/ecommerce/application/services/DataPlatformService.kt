package io.hhplus.ecommerce.application.services

/**
 * 데이터 플랫폼 전송 서비스 인터페이스
 *
 * Kafka Consumer에서 ORDER_PAID 이벤트 수신 후
 * 외부 데이터 플랫폼(Data Warehouse, Analytics, ERP 등)으로
 * 주문 데이터를 전송하는 역할
 *
 * 목적:
 * - 핵심 트랜잭션(결제 처리)과 부가 로직(데이터 전송)의 분리
 * - 비동기 처리로 성능 개선
 * - 외부 시스템 장애가 핵심 비즈니스에 영향을 주지 않도록 격리
 */
interface DataPlatformService {
    /**
     * 주문 완료 데이터를 데이터 웨어하우스로 전송
     *
     * @param orderId 주문 ID
     * @param userId 사용자 ID
     * @param finalAmount 최종 결제 금액
     * @param paidAt 결제 시각
     */
    fun sendOrderDataToWarehouse(orderId: Long, userId: Long, finalAmount: Long, paidAt: String)

    /**
     * 결제 데이터를 분석 시스템으로 전송
     *
     * @param orderId 주문 ID
     * @param userId 사용자 ID
     * @param finalAmount 최종 결제 금액
     */
    fun sendPaymentAnalytics(orderId: Long, userId: Long, finalAmount: Long)

    /**
     * ERP 시스템에 주문 정보 전달
     *
     * @param orderId 주문 ID
     */
    fun notifyErpSystem(orderId: Long)
}
