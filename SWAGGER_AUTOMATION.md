# Swagger/OpenAPI 자동화 가이드

## 개요

이 프로젝트는 **Springdoc-OpenAPI**를 사용하여 API 코드 변경이 자동으로 Swagger 문서에 반영되도록 설정되어 있습니다.

- **이전:** 정적 YAML 파일 수동 관리 → API 변경 시 문서도 수동 수정 필요
- **현재:** 코드 어노테이션 기반 자동 생성 → API 변경 시 자동으로 문서 업데이트 ✅

---

## 문서 생성 방식

### 자동 생성되는 부분
- API 엔드포인트 목록 (`/api/hello`, `/api/hello/greet` 등)
- 요청/응답 스키마
- 파라미터 정보 및 유효성 검증
- HTTP 상태 코드 및 에러 응답
- 인증/보안 설정 (JWT Bearer Token)

### 수동 설정되는 부분
- 전역 API 메타데이터 (제목, 버전, 설명 등) → `OpenApiConfig.kt`
- JWT 보안 스킴 → `OpenApiConfig.kt`

---

## 사용 방법

### 1. 새 API 엔드포인트 추가하기

컨트롤러에 `@Operation` 어노테이션을 추가하면 자동으로 문서화됩니다.

```kotlin
@RestController
@RequestMapping("/api/products")
@Tag(name = "Products", description = "상품 관련 API")
class ProductController {

    @GetMapping("/{id}")
    @Operation(
        summary = "상품 상세 조회",
        description = "상품 ID로 상품 정보를 조회합니다."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ProductDto::class)
                )]
            ),
            ApiResponse(
                responseCode = "404",
                description = "상품을 찾을 수 없음"
            )
        ]
    )
    fun getProduct(
        @Parameter(
            name = "id",
            description = "상품 ID",
            required = true,
            example = "prod_123"
        )
        @PathVariable id: String
    ): ResponseEntity<ProductDto> {
        // 구현...
    }
}
```

### 2. 주요 어노테이션

| 어노테이션 | 용도 | 예시 |
|-----------|------|------|
| `@Operation` | 엔드포인트 설명 | `summary`, `description` |
| `@Parameter` | 요청 파라미터 설명 | 경로, 쿼리, 헤더 파라미터 |
| `@RequestBody` | 요청 바디 설명 | 자동으로 스키마 생성 |
| `@ApiResponse` | 응답 코드별 설명 | 200, 400, 404 등 |
| `@Tag` | API 그룹화 | 카테고리별 그룹 |
| `@Schema` | 필드/클래스 스키마 | DTO의 필드 설명 |

### 3. DTO에 스키마 추가

```kotlin
data class ProductDto(
    @Schema(
        description = "상품 ID",
        example = "prod_123"
    )
    val id: String,

    @Schema(
        description = "상품명",
        example = "슬림핏 청바지"
    )
    val name: String,

    @Schema(
        description = "가격",
        example = "79000",
        minimum = "0"
    )
    val price: Int
)
```

---

## 확인 방법

### 1. Swagger UI 페이지
```
http://localhost:8080/swagger-ui.html
```

### 2. OpenAPI JSON
```
http://localhost:8080/v3/api-docs
```

### 3. OpenAPI YAML
```
http://localhost:8080/v3/api-docs.yaml
```

---

## 자동화 설정 파일

### `application.yml` - Springdoc 설정
```yaml
springdoc:
  swagger-ui:
    enabled: true              # Swagger UI 활성화
    path: /swagger-ui.html     # Swagger UI 경로
    operationsSorter: method   # 메서드별 정렬
    tagsSorter: alpha          # 태그 알파벳 정렬
    tryItOutEnabled: true      # "Try it out" 기능 활성화
  api-docs:
    path: /v3/api-docs        # OpenAPI 문서 경로
  auto-schema: true           # 자동 스키마 생성
  add-openapi-methods: true   # OpenAPI 메서드 추가
```

### `OpenApiConfig.kt` - 전역 설정
```kotlin
@Configuration
class OpenApiConfig {
    @Bean
    fun openAPI(): OpenAPI {
        // 제목, 버전, 설명, 연락처, 라이선스 설정
        // JWT Bearer Token 보안 스킴 설정
    }
}
```

---

## 예제 코드

`HelloController.kt` 파일에서 완전한 예제를 볼 수 있습니다:

```kotlin
@RestController
@RequestMapping("/api/hello")
@Tag(name = "Hello API", description = "시연용 API - Swagger 자동화 예제")
class HelloController {

    @GetMapping
    @Operation(
        summary = "인사말 조회",
        description = "간단한 인사말을 반환합니다."
    )
    @ApiResponses(...)
    fun sayHello(): ResponseEntity<Map<String, String>> {
        return ResponseEntity.ok(mapOf("message" to "Hello, World!"))
    }

    // 추가 엔드포인트들...
}
```

---

## 주의사항

1. **어노테이션 누락 방지**
   - `@Operation` 없는 엔드포인트는 Swagger에서 숨겨질 수 있음
   - 모든 공개 API에 문서화 어노테이션 추가 필수

2. **API 변경 시 즉시 반영**
   - 엔드포인트 경로, 파라미터, 응답 타입 변경 시 어노테이션도 함께 수정
   - Swagger는 자동으로 최신 코드 기반으로 문서 생성

3. **보안 정보 노출 주의**
   - `@Parameter(example = "...")` 사용 시 민감한 정보 노출 주의
   - 실제 토큰, 비밀번호 등은 예제로 사용 금지

---

## 마이그레이션 정보

### 이전 방식 (정적 YAML)
- 파일: `src/main/resources/swagger.yaml` 또는 `api-docs.yaml`
- 방식: 수동 작성/관리
- 문제점: API 변경 시 별도로 문서 수정 필요

### 현재 방식 (자동 생성)
- 파일: 없음 (코드 기반)
- 방식: 어노테이션 기반 자동 생성
- 장점: API 변경 시 자동 반영

### 변경 사항
- ✅ `SwaggerController.kt` 제거
- ✅ `application.yml`에서 정적 파일 URL 제거
- ✅ `OpenApiConfig.kt` 전체 메타데이터 설정으로 통합

---

## 문제 해결

### Swagger UI에 API가 나타나지 않음
- 컨트롤러에 `@RestController` 또는 `@Controller` 추가 확인
- `@RequestMapping` 또는 `@GetMapping` 등 HTTP 메서드 어노테이션 확인
- `@Operation` 어노테이션 추가 필요

### 잘못된 요청/응답 스키마
- DTO 클래스에 기본 생성자(no-arg constructor) 필요
- `@Schema` 어노테이션으로 필드 설명 추가
- 복잡한 타입은 명시적으로 `schema = Schema(implementation = MyClass::class)` 지정

### JWT 토큰이 모든 API에 적용되지 않음
- 인증 불필요한 API는 `@Operation(security = [])` 추가
- 예: 로그인, 회원가입 등

---

## 참고 링크

- [Springdoc-OpenAPI 공식 문서](https://springdoc.org/)
- [OpenAPI 3.0 스펙](https://spec.openapis.org/oas/v3.0.3)
- [Swagger 어노테이션 가이드](https://github.com/swagger-api/swagger-core/wiki/Annotations)
