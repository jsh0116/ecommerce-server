package com.hhplus.ecommerce.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * OpenAPI/Swagger 설정
 *
 * Springdoc-OpenAPI가 컨트롤러의 @Operation, @Parameter 등 어노테이션을 통해
 * 자동으로 OpenAPI 스키마를 생성합니다.
 *
 * 이 설정에서는:
 * 1. 전역 메타데이터 (제목, 버전, 설명, 연락처 등) 정의
 * 2. 보안 스킴(JWT Bearer Token) 설정
 *
 * API 개발 시 컨트롤러에서 다음 어노테이션을 사용하면 자동 문서화됩니다:
 * - @Operation: API 엔드포인트 설명
 * - @Parameter: 요청 파라미터 설명
 * - @RequestBody: 요청 바디 스키마
 * - @ApiResponse: 응답 코드별 설명
 * - @Tag: 엔드포인트 그룹화
 *
 * 변경 사항: 정적 YAML 파일 대신 코드 어노테이션 기반 자동 생성으로 전환
 */
@Configuration
class OpenApiConfig {

    @Bean
    fun openAPI(): OpenAPI {
        return OpenAPI()
            .info(apiInfo())
            .addSecurityItem(
                SecurityRequirement()
                    .addList("bearerAuth")
            )
            .components(
                io.swagger.v3.oas.models.Components()
                    .addSecuritySchemes(
                        "bearerAuth",
                        SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")
                            .description("JWT Bearer Token을 사용합니다.\n\n예시: Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
                    )
            )
    }

    private fun apiInfo(): Info {
        return Info()
            .title("의류 이커머스 API")
            .version("1.0.0")
            .description(
                """
                ## 📖 개요
                의류 이커머스 플랫폼의 RESTful API 명세입니다.

                ## 🎯 주요 기능
                - 🔍 상품 검색 및 필터링 (다중 색상/사이즈)
                - 📦 실시간 재고 관리 (동시성 제어)
                - 🛒 장바구니 및 주문 처리
                - 💳 결제 및 정산
                - 🎟️ 쿠폰 및 포인트 시스템
                - 🚚 배송 추적
                - 🔄 반품 및 교환
                - ⭐ 리뷰 시스템

                ## 🔐 인증
                대부분의 API는 JWT Bearer Token 인증이 필요합니다.

                ## ⚡ Rate Limiting
                - 인증된 사용자: 1000 req/hour
                - 비인증 사용자: 100 req/hour

                ## 📋 주요 비즈니스 로직
                ### 재고 관리
                - 재고 차감 시점: **결제 승인 시**
                - 재고 예약: 주문 생성 시 15분 TTL
                - 동시성 제어: Pessimistic Lock + Redis 분산 락

                ### 주문 프로세스
                1. 장바구니 추가 → 재고 조회만
                2. 주문 생성 → 재고 예약 (15분)
                3. 결제 승인 → 실재고 차감
                4. 결제 실패 → 예약 해제

                ### 반품/교환
                - 반품 가능 기간: 배송 완료 후 7일
                - 단순 변심: 고객 부담 6,000원
                - 불량/오배송: 무료
                """.trimIndent()
            )
            .contact(
                Contact()
                    .name("API Support")
                    .email("api@fashionstore.com")
                    .url("https://www.fashionstore.com/support")
            )
            .license(
                License()
                    .name("MIT")
                    .url("https://opensource.org/licenses/MIT")
            )
    }
}
