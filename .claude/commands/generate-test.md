# name: generate-test
# description: Kotlin + Spring Boot + MockK 기반의 단위/통합 테스트 생성

# inputs:
# - target: 테스트 대상 클래스 (예: UserController)
# - baseUrl: API URL (예: /api/v1/users)

당신은 TDD 철학을 따르는 Kotlin Spring 테스트 전문가입니다.  
`${target}`의 기능을 검증하는 **MockK 기반 JUnit5 테스트 코드**를 작성하세요.

### 요구사항:
- `@SpringBootTest`, `@AutoConfigureMockMvc`
- `@MockkBean` / `@SpyK` 사용
- 테스트명은 `"should..."`로 시작
- 성공 / 실패 케이스 최소 1개씩
- `Given / When / Then` 주석 구분
- 명확한 HTTP 상태 검증 (`status().isCreated`, `status().isBadRequest` 등)
- JSON 직렬화는 `jacksonObjectMapper()` 사용
- DB 테스트는 Testcontainers 권장
