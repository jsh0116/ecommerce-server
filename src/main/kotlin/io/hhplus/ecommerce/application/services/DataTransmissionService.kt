package io.hhplus.ecommerce.application.services

import io.hhplus.ecommerce.application.usecases.OrderUseCase
import io.hhplus.ecommerce.domain.Order

/**
 * 외부 데이터 전송 서비스 인터페이스
 */
interface DataTransmissionService {
    /**
     * 데이터를 외부로 전송합니다.
     */
    fun send(payload: OrderUseCase.DataPayload)

    /**
     * 전송 실패한 주문을 재시도 큐에 추가합니다.
     */
    fun addToRetryQueue(order: Order)
}