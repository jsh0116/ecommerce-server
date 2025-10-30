package io.hhplus.week2.config

import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.nio.charset.StandardCharsets

/**
 * Swagger/OpenAPI YAML 파일을 서빙하는 컨트롤러
 * - /api-docs.yaml 엔드포인트를 통해 OpenAPI 명세서 제공
 * - Swagger UI가 이 파일을 로드하여 표시
 */
@RestController
class SwaggerController {

    /**
     * OpenAPI 명세서를 YAML 형식으로 반환
     */
    @GetMapping("/api-docs.yaml")
    fun getOpenApiYaml(): ResponseEntity<String> {
        return try {
            val resource = ClassPathResource("api-docs.yaml")
            val content = resource.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/yaml; charset=UTF-8")
                .body(content)
        } catch (e: Exception) {
            ResponseEntity.notFound().build()
        }
    }
}