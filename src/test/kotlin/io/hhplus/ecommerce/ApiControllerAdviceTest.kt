package io.hhplus.ecommerce

import io.hhplus.ecommerce.exception.BusinessRuleViolationException
import io.hhplus.ecommerce.exception.OrderException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 테스트용 컨트롤러
 */
@RestController
@RequestMapping("/api/test")
class TestExceptionController {
    @GetMapping("/runtime-error")
    fun throwRuntimeException(): String {
        throw RuntimeException("테스트 런타임 에러")
    }

    @GetMapping("/business-error")
    fun throwBusinessException(): String {
        throw BusinessRuleViolationException("TEST_ERROR", "테스트 비즈니스 에러")
    }

    @GetMapping("/order-error")
    fun throwOrderException(): String {
        throw OrderException.CannotPayOrder()
    }
}

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("ApiControllerAdvice 테스트")
class ApiControllerAdviceTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Nested
    @DisplayName("예외 처리 테스트")
    inner class ExceptionHandlingTest {
        @Test
        fun `RuntimeException이 500 상태로 처리된다`() {
            // When & Then
            mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/test/runtime-error")
                    .contentType(MediaType.APPLICATION_JSON)
            )
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isInternalServerError)
        }

        @Test
        fun `BusinessRuleViolationException이 400 상태로 처리된다`() {
            // When & Then
            mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/test/business-error")
                    .contentType(MediaType.APPLICATION_JSON)
            )
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest)
        }

        @Test
        fun `OrderException이 적절히 처리된다`() {
            // When & Then
            mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/test/order-error")
                    .contentType(MediaType.APPLICATION_JSON)
            )
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest)
        }
    }
}
