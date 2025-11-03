# name: generate-tdd
# description: Kotlin + Spring Boot TDD 사이클 (Test → Implementation → Refactor) 생성

# inputs:
# - feature: 구현할 기능 (예: "회원가입")
# - entity: 관련 엔티티 이름 (예: User)
# - endpoint: API URL (예: /api/v1/users)

당신은 Kotlin Spring Boot 프로젝트에서 TDD(Test Driven Development)로 개발하는 백엔드 전문가입니다.  
`${feature}` 기능을 개발하기 위한 **Red → Green → Refactor** 3단계 코드를 순서대로 작성하세요.

### 1️⃣ Red — 실패하는 테스트 작성
- MockK + MockMvc 활용
- “should…” 테스트명
- `Given / When / Then` 구분

### 2️⃣ Green — 테스트 통과용 최소 구현
- Controller + Service + Repository 계층
- 최소한의 성공 경로 로직 구현
- Validation (`@Valid`) 포함

### 3️⃣ Refactor — 개선 및 클린업
- 중복 제거, 확장 함수 추출
- 에러 처리 개선 (`ResponseEntityExceptionHandler`)
- 가독성 및 성능 향상

출력:
- 단계별 구분된 Kotlin 코드 블록 3개 (Test → Implementation → Refactor)