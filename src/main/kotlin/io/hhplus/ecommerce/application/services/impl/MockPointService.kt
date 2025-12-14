package io.hhplus.ecommerce.application.services.impl

import io.hhplus.ecommerce.application.services.PointService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * 포인트 서비스 Mock 구현
 *
 * 실제 포인트 시스템 구현 전까지 사용하는 In-Memory Mock 서비스입니다.
 * 테스트 및 개발 용도로 사용합니다.
 */
@Service
class MockPointService : PointService {
    private val logger = LoggerFactory.getLogger(javaClass)

    // In-Memory 포인트 저장소 (userId -> points)
    private val pointsStorage = ConcurrentHashMap<Long, Long>()

    override fun addPoints(userId: Long, points: Long, reason: String): Long {
        val currentPoints = pointsStorage.getOrDefault(userId, 0L)
        val newPoints = currentPoints + points
        pointsStorage[userId] = newPoints

        logger.info(
            "[포인트 Mock] 포인트 적립 - userId: {}, 적립: {}P, 총: {}P, 사유: {}",
            userId, points, newPoints, reason
        )

        return newPoints
    }

    override fun deductPoints(userId: Long, points: Long, reason: String): Long {
        val currentPoints = pointsStorage.getOrDefault(userId, 0L)

        if (currentPoints < points) {
            logger.warn(
                "[포인트 Mock] 포인트 부족 - userId: {}, 현재: {}P, 차감요청: {}P",
                userId, currentPoints, points
            )
            throw IllegalStateException("포인트가 부족합니다. 현재: ${currentPoints}P, 필요: ${points}P")
        }

        val newPoints = currentPoints - points
        pointsStorage[userId] = newPoints

        logger.info(
            "[포인트 Mock] 포인트 차감 - userId: {}, 차감: {}P, 총: {}P, 사유: {}",
            userId, points, newPoints, reason
        )

        return newPoints
    }

    override fun getPoints(userId: Long): Long {
        return pointsStorage.getOrDefault(userId, 0L)
    }
}
