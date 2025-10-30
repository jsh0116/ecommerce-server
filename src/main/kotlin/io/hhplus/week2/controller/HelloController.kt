package io.hhplus.week2.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/hello")
@Tag(name = "Hello API", description = "시연용 API - Swagger 자동화 예제")
class HelloController {

    @GetMapping
    @Operation(
        summary = "인사말 조회",
        description = "간단한 인사말을 반환합니다. 이 엔드포인트는 @Operation 어노테이션으로 자동 문서화됩니다."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "성공",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(
                        example = """{"message": "Hello, World!"}"""
                    )
                )]
            )
        ]
    )
    fun sayHello(): ResponseEntity<Map<String, String>> {
        return ResponseEntity.ok(mapOf("message" to "Hello, World!"))
    }

    @GetMapping("/greet")
    @Operation(
        summary = "개인화된 인사말",
        description = "이름을 받아서 개인화된 인사말을 반환합니다. API 변경 시 자동으로 Swagger에 반영됩니다."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "성공"
            )
        ]
    )
    fun greet(
        @Parameter(
            name = "name",
            description = "인사할 사람의 이름",
            required = true,
            example = "홍길동"
        )
        @RequestParam name: String
    ): ResponseEntity<Map<String, String>> {
        return ResponseEntity.ok(mapOf("message" to "Hello, $name!"))
    }

    @PostMapping("/echo")
    @Operation(
        summary = "메시지 에코",
        description = "받은 메시지를 그대로 반환합니다."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "성공"
            ),
            ApiResponse(
                responseCode = "400",
                description = "잘못된 요청"
            )
        ]
    )
    fun echo(
        @RequestBody request: EchoRequest
    ): ResponseEntity<EchoResponse> {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(EchoResponse(message = request.message, echoed = true))
    }

    data class EchoRequest(
        @io.swagger.v3.oas.annotations.media.Schema(
            description = "에코할 메시지",
            example = "Hello, Swagger!"
        )
        val message: String
    )

    data class EchoResponse(
        @io.swagger.v3.oas.annotations.media.Schema(
            description = "에코된 메시지"
        )
        val message: String,

        @io.swagger.v3.oas.annotations.media.Schema(
            description = "에코 성공 여부"
        )
        val echoed: Boolean
    )
}