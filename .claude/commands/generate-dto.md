# name: generate-dto
# description: Kotlin data class 기반의 Request/Response DTO를 생성합니다.

# inputs:
# - entity: 엔티티 이름 (예: User)
# - fields: 필드 목록 (예: id:Long, name:String, email:String)

`${entity}` 엔티티의 요청/응답 DTO를 생성하세요.

### 요구사항:
- DTO는 **불변 객체 (`val`)**로 정의
- **Bean Validation** (`@field:NotBlank`, `@field:Email`, `@field:Positive`) 포함
- `@Schema(description = “…”)` 애노테이션으로 Swagger 문서화
- 엔티티 변환 유틸 포함: `fun toEntity(): ${entity}`
- Response DTO는 `from(entity: ${entity})` 팩토리 메서드 포함
- null-safe 매핑 사용 (`?.let {}`)

출력: Kotlin 코드 블록 2개 (Request DTO, Response DTO)