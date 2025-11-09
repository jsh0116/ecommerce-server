package io.hhplus.ecommerce

import io.hhplus.ecommerce.exception.EcommerceException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

data class ErrorResponse(val code: String, val message: String)

@RestControllerAdvice
class ApiControllerAdvice : ResponseEntityExceptionHandler() {
    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    /**
     * 커스텀 예외 처리 - 예외 코드와 상태 코드를 함께 반환
     */
    @ExceptionHandler(EcommerceException::class)
    fun handleEcommerceException(e: EcommerceException): ResponseEntity<ErrorResponse> {
        logger.warn("EcommerceException: code=${e.errorCode}, message=${e.message}")
        val httpStatus = when (e.statusCode) {
            400 -> HttpStatus.BAD_REQUEST
            403 -> HttpStatus.FORBIDDEN
            404 -> HttpStatus.NOT_FOUND
            else -> HttpStatus.BAD_REQUEST
        }
        return ResponseEntity(
            ErrorResponse(code = e.errorCode, message = e.message),
            httpStatus
        )
    }

    /**
     * 일반 예외 처리
     */
    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ErrorResponse> {
        logger.error("Unexpected exception occurred", e)
        return ResponseEntity(
            ErrorResponse(code = "INTERNAL_SERVER_ERROR", message = "서버 오류가 발생했습니다"),
            HttpStatus.INTERNAL_SERVER_ERROR,
        )
    }
}