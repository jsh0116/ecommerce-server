package io.hhplus.ecommerce.application.services.impl

import io.hhplus.ecommerce.domain.Order
import io.hhplus.ecommerce.dto.DataPayload
import io.hhplus.ecommerce.application.services.DataTransmissionService
import org.springframework.stereotype.Service

/**
 * 외부 데이터 전송 서비스 Mock 구현
 */
@Service
class MockDataTransmissionService : DataTransmissionService {
    override fun send(payload: DataPayload) {
        // Mock 구현: 로그만 출력
        println("Data transmitted: Order ${payload.orderId} for user ${payload.userId}")
    }

    override fun addToRetryQueue(order: Order) {
        // Mock 구현: 로그만 출력
        println("Order ${order.id} added to retry queue")
    }
}