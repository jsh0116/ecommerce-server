package io.hhplus.ecommerce.application.saga

/**
 * 중복 요청 예외
 *
 * Phase 2: 멱등성 키를 통한 중복 요청 감지
 */
class DuplicateRequestException(
    message: String
) : RuntimeException(message)
