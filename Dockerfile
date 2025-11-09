# 다단계 빌드 (Multi-stage Build)
# 1단계: 빌드 스테이지
FROM openjdk:17-jdk-slim as builder

WORKDIR /build

# Gradle 래퍼 복사
COPY gradlew .
COPY gradle gradle
RUN chmod +x gradlew

# 소스 코드 복사
COPY settings.gradle.kts .
COPY build.gradle.kts .
COPY gradle.properties .
COPY src src

# 빌드
RUN ./gradlew clean build -x test --no-daemon

# 2단계: 런타임 스테이지
FROM openjdk:17-jdk-slim

LABEL maintainer="Backend Team <api@fashionstore.com>"
LABEL description="의류 이커머스 API 서버"

WORKDIR /app

# 빌드 스테이지에서 JAR 파일 복사
COPY --from=builder /build/build/libs/hhplus-ecommerce-*.jar app.jar

# 리소스 복사 (Swagger, application.yml 등)
COPY --from=builder /build/src/main/resources /app/resources

# 사용자 생성 (보안)
RUN useradd -m appuser && chown -R appuser:appuser /app
USER appuser

# 환경 변수 설정
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:InitiatingHeapOccupancyPercent=35 -XX:G1HeapRegionSize=16m"
ENV SERVER_PORT=8080

# 포트 노출
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD java -cp app.jar com.hhplus.ecommerce.health.HealthCheck || exit 1

# 시작 명령
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]