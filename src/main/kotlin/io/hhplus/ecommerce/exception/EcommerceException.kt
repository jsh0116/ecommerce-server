package io.hhplus.ecommerce.exception

/**
 * 이커머스 도메인의 기본 커스텀 예외
 */
open class EcommerceException(
    val errorCode: String,
    override val message: String,
    val statusCode: Int = 400
) : RuntimeException(message)

/**
 * 리소스를 찾을 수 없을 때 발생하는 예외
 */
open class ResourceNotFoundException(
    errorCode: String = "RESOURCE_NOT_FOUND",
    message: String = "요청한 리소스를 찾을 수 없습니다"
) : EcommerceException(errorCode = errorCode, message = message, statusCode = 404)

/**
 * 비즈니스 규칙 위반 시 발생하는 예외
 */
open class BusinessRuleViolationException(
    errorCode: String,
    message: String
) : EcommerceException(errorCode = errorCode, message = message, statusCode = 400)

/**
 * 유효하지 않은 요청 시 발생하는 예외
 */
open class InvalidRequestException(
    errorCode: String = "INVALID_REQUEST",
    message: String = "유효하지 않은 요청입니다"
) : EcommerceException(errorCode = errorCode, message = message, statusCode = 400)

/**
 * 승인되지 않은 접근 시 발생하는 예외
 */
open class UnauthorizedException(
    errorCode: String = "UNAUTHORIZED",
    message: String = "접근 권한이 없습니다"
) : EcommerceException(errorCode = errorCode, message = message, statusCode = 403)
