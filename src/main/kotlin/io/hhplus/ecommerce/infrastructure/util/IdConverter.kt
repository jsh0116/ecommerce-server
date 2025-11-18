package io.hhplus.ecommerce.infrastructure.util

import java.nio.ByteBuffer
import java.util.*

/**
 * ID 변환 유틸리티
 * - DB: BIGINT AUTO_INCREMENT로 저장 (성능, 저장공간 최적화)
 * - 외부 API: UUID로 노출 (보안, API 일관성)
 */
object IdConverter {
    /**
     * Long ID를 UUID 문자열로 변환
     *
     * @param id BIGINT ID
     * @return UUID 문자열 (36자)
     */
    fun longToUuid(id: Long): String {
        return UUID.nameUUIDFromBytes(
            ByteBuffer.allocate(8).putLong(id).array()
        ).toString()
    }

    /**
     * UUID 문자열을 Long ID로 변환
     *
     * @param uuid UUID 문자열
     * @return BIGINT ID
     */
    fun uuidToLong(uuid: String): Long {
        return UUID.fromString(uuid).mostSignificantBits
    }
}

/**
 * 확장 함수: Long을 UUID 문자열로 변환
 */
fun Long.toUuid(): String = IdConverter.longToUuid(this)

/**
 * 확장 함수: String(UUID)을 Long ID로 변환
 */
fun String.uuidToLong(): Long = IdConverter.uuidToLong(this)