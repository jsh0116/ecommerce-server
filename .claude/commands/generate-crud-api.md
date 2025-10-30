# name: generate-crud-api
# description: Kotlin + Spring Boot 3.x 기반의 RESTful CRUD API 템플릿을 생성합니다.

# inputs:
# - entity: 엔티티 이름 (예: User, Product)
# - fields: 필드 목록 (예: id:Long, name:String, email:String)

당신은 Kotlin과 Spring Boot 3.x에 숙련된 백엔드 개발자입니다.  
`${entity}` 엔티티를 기반으로 **Clean Architecture** 구조의 코드를 생성하세요:

1. `@RestController` 기반 `${entity}Controller`
2. `@Service` 기반 `${entity}Service`
3. `@Repository` 기반 `${entity}Repository`
4. `@Entity` 기반 `data class ${entity}`

### 요구사항:
- **Kotlin 스타일** (`data class`, constructor injection, `val` 사용)
- **Null 안전성 보장** (`?`, `?:`, `let`, `takeIf` 등 활용)
- **DTO 변환 메서드**를 별도로 생성 (`toEntity()`, `toResponse()`)
- **Validation**: `@field:NotBlank`, `@field:Email`, `@Valid` 등 활용
- **에러 처리**: `@RestControllerAdvice` + `ResponseEntityExceptionHandler`
- **API 문서화**: OpenAPI(`@Operation`, `@ApiResponse`)
- **테스트 용이성 고려** (MockK-friendly 구조)
- HTTP Status Code는 명확히 지정 (`201 CREATED`, `404 NOT_FOUND`, 등)
- Repository는 `JpaRepository<${entity}, Long>` 기반

### 출력:
- Kotlin 코드 블록 4개 (Entity, Repository, Service, Controller)