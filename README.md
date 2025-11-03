# 📚 Swagger API 명세 배포 - 완벽 가이드

## 🎯 개요

이 프로젝트는 **Springdoc-OpenAPI**를 통해 자동으로 Swagger UI를 제공합니다.
API 코드가 변경되면 문서도 **자동으로 동기화**됩니다.

---

## 🚀 시작하기 (3가지 방법)

### 방법 1: 로컬 실행 (가장 간단)

```bash
./gradlew bootRun
```

**접근:**
```
http://localhost:8080/swagger-ui.html
```

---

### 방법 2: Docker 실행

```bash
# 이미지 빌드
./gradlew clean build -x test
docker build -t hhplus-ecommerce:latest .

# 컨테이너 실행
docker run -p 8080:8080 hhplus-ecommerce:latest
```

**또는 Docker Compose 사용:**

```bash
docker-compose up --build
```

**접근:**
```
http://localhost:8080/swagger-ui.html
```

---

### 방법 3: 클라우드 배포 (Google Cloud Run)

```bash
# 1. 빌드
./gradlew clean build -x test

# 2. 이미지 빌드 및 푸시
gcloud auth configure-docker
docker build -t gcr.io/YOUR_PROJECT_ID/hhplus:latest .
docker push gcr.io/YOUR_PROJECT_ID/hhplus:latest

# 3. Cloud Run 배포
gcloud run deploy hhplus-ecommerce \
  --image gcr.io/YOUR_PROJECT_ID/hhplus:latest \
  --platform managed \
  --region asia-northeast1 \
  --port 8080 \
  --memory 512Mi
```

**접근:**
```
https://hhplus-ecommerce-{hash}.run.app/swagger-ui.html
```

---

## 📖 문서 구조

```

프로젝트 루트/
├── Dockerfile ....................... Docker 이미지 정의
├── docker-compose.yml ............... Docker Compose 설정
├── .dockerignore .................... Docker 제외 파일
├── docs/
│   ├── swagger/
│     ├── README_SWAGGER.md .................. 이 파일 (개요)
│     ├── QUICK_START.md .................... 5분 시작 가이드
│     ├── SWAGGER_DEPLOYMENT.md ............ 전체 배포 옵션 설명
│     └── DEPLOYMENT_CHECKLIST.md .......... 배포 확인 목록
│   ├── api-specification.md ......... API 명세서 (P0 이슈 포함)
│   ├── swagger.yaml ................. OpenAPI 정의 (자동 생성)
│   ├── requirements.md .............. 요구사항 명세서
│   ├── user-stories.md .............. 사용자 스토리
│   ├── data-models.md ............... 데이터 모델
│   ├── flow-chart.md ................ 플로우 차트
│   └── self-check-report.md ......... 자체 검증 보고서
│
└── src/main/
    ├── kotlin/com/hhplus/ecommerce/config/
    │   └── OpenApiConfig.kt ......... Swagger 설정
    │
    └── resources/
        ├── application.yml .......... Spring Boot 설정
        └── swagger.yaml ............ OpenAPI 정의 복사본
```

---

## 🛠️ 설정 파일 설명

### 1. build.gradle.kts

```kotlin
// Swagger UI & OpenAPI
implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.0.2")
```

**역할:** Springdoc-OpenAPI 라이브러리 제공

---

### 2. application.yml

```yaml
springdoc:
  swagger-ui:
    enabled: true
    path: /swagger-ui.html
  api-docs:
    path: /v3/api-docs
```

**역할:** Swagger UI 경로 설정

---

### 3. OpenApiConfig.kt

```kotlin
@Configuration
class OpenApiConfig {
    @Bean
    fun openAPI(): OpenAPI { ... }
}
```

**역할:** OpenAPI 메타데이터 정의 (제목, 설명, 연락처 등)

---

## 📊 API 접근 방법

### Swagger UI
```
GET http://localhost:8080/swagger-ui.html
```

### OpenAPI JSON
```
GET http://localhost:8080/v3/api-docs
```

### OpenAPI YAML
```
GET http://localhost:8080/v3/api-docs.yaml
```

---

## 🔍 Swagger UI 사용법

### 1. API 탐색
- 좌측: 카테고리별 API 그룹화
- 우측: 상세 정보 표시

### 2. API 테스트 (Try It Out)

```
1. 엔드포인트 클릭
2. "Try it out" 버튼 클릭
3. 파라미터 입력
4. "Execute" 버튼 클릭
5. 응답 확인
```

### 3. JWT 인증

```
1. 우측 상단의 "Authorize" 버튼 클릭
2. "Bearer {token}" 입력
3. "Authorize" 클릭
4. 이후 모든 요청에 자동 적용
```

---

## 🔄 API 문서 동기화

### 자동 동기화 (권장)

```
API 코드 수정 → Spring Boot 재시작 → Swagger UI 자동 업데이트
```

**적용되는 항목:**
- ✅ @RestController, @GetMapping 등 어노테이션
- ✅ @RequestParam, @PathVariable 파라미터
- ✅ @RequestBody, @ResponseBody 스키마
- ✅ 메서드 주석 (Javadoc/KDoc)

**수동 갱신:**
```bash
./gradlew bootRun  # 재시작으로 수동 갱신
```

---

## 🐳 Docker 배포 상세

### Docker 이미지 크기
```bash
docker images | grep hhplus
# REPOSITORY     TAG     SIZE
# hhplus...      latest  ~300MB
```

### 컨테이너 리소스 사용

```bash
docker stats hhplus-ecommerce-api
# CONTAINER CPU   MEM
# hhplus...   0.1% 200MB
```

### 로그 확인

```bash
docker logs hhplus-ecommerce-api
docker logs -f hhplus-ecommerce-api  # 실시간 로그
```

---

## ☁️ 클라우드 배포 비교

| 플랫폼 | 비용 | 설정 | 추천 상황 |
|--------|------|------|----------|
| **Google Cloud Run** | 무료~$20/월 | ⭐⭐ | 일반 프로젝트 |
| **AWS ECS** | $50/월+ | ⭐⭐⭐ | 엔터프라이즈 |
| **Heroku** | 무료~$50/월 | ⭐ | 개발/테스트 |
| **로컬 Docker** | 무료 | ⭐ | 개발 환경 |

---

## 🔒 보안 주의사항

### 1. 민감한 정보 숨김

```kotlin
@Hidden  // Swagger UI에서 숨김
fun internalApi() { }
```

### 2. HTTPS 강제

```yaml
server:
  ssl:
    enabled: true
    key-store: classpath:keystore.p12
```

### 3. 인증 필수

```kotlin
// Swagger UI 접근 시 인증 필요
@Configuration
class SwaggerSecurityConfig {
    // ... 설정
}
```

---

## 📈 모니터링 및 로깅

### CloudWatch (AWS)

```bash
# 로그 확인
aws logs tail /ecs/hhplus-ecommerce --follow
```

### Cloud Logging (GCP)

```bash
# 로그 확인
gcloud logging read "resource.type=cloud_run_revision" --limit 50
```

### 로컬 로그

```bash
./gradlew bootRun 2>&1 | grep -i swagger
```

---

## 🚨 문제 해결

### Q1: Swagger UI 404 에러

**원인:** 라이브러리 누락 또는 설정 오류

```bash
# 1. 의존성 확인
./gradlew dependencies | grep springdoc

# 2. 재빌드
./gradlew clean build

# 3. 재시작
./gradlew bootRun
```

### Q2: OpenAPI JSON이 비어 있음

**원인:** API 엔드포인트 없음

```bash
# 1. 컨트롤러 확인
find src -name "*Controller.kt"

# 2. @RestController 어노테이션 확인
grep -r "@RestController" src/
```

### Q3: Docker 빌드 실패

**원인:** JAR 파일 없음

```bash
# 1. 빌드
./gradlew clean build -x test

# 2. JAR 확인
ls -la build/libs/

# 3. Dockerfile에서 JAR 경로 확인
cat Dockerfile | grep -i "copy"
```

---

## 📚 추가 참고 자료

- **Springdoc 공식 문서:** https://springdoc.org/
- **OpenAPI 명세:** https://spec.openapis.org/
- **Swagger UI:** https://swagger.io/tools/swagger-ui/
- **Google Cloud Run:** https://cloud.google.com/run/docs

---

## 🎯 다음 단계

### 즉시 실행
1. `./gradlew bootRun`
2. http://localhost:8080/swagger-ui.html 접근
3. API 테스트

### 배포
1. `QUICK_START.md` 참고
2. Docker 또는 클라우드 선택
3. 배포 실행

### CI/CD
1. `.github/workflows/deploy.yml` 생성
2. GitHub Actions 구성
3. 자동 배포 설정

### 모니터링
1. CloudWatch/Cloud Logging 설정
2. 알람 구성
3. 대시보드 생성

---

## 📞 지원

**문제 발생 시:**
1. DEPLOYMENT_CHECKLIST.md 확인
2. 로그 확인 (`./gradlew bootRun`)
3. Docker 로그 확인 (`docker logs`)

**배포 가이드:**
- `QUICK_START.md` (빠른 시작)
- `SWAGGER_DEPLOYMENT.md` (상세 가이드)

---

## ✅ 체크리스트

- [ ] Spring Boot 실행 성공
- [ ] Swagger UI 접근 가능
- [ ] API 엔드포인트 표시됨
- [ ] "Try it out" 작동
- [ ] JWT 인증 작동
- [ ] Docker 이미지 빌드 성공
- [ ] 클라우드 배포 완료

---

**마지막 수정:** 2024-03-15
**버전:** 1.0.0
**작성자:** Backend Team

🎉 **준비 완료! Swagger API 명세 배포를 시작하세요!**
