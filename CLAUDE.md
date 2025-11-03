# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**hhplus-week2** is a Spring Boot 3.2.0 Kotlin microservice starter template. It provides a minimal boilerplate with centralized exception handling and a fully configured testing infrastructure.

- **Language:** Kotlin 1.9.21
- **Framework:** Spring Boot 3.2.0
- **Build Tool:** Gradle 8.4+ (Kotlin DSL)
- **Java Target:** Java 17
- **Package Root:** `io.hhplus.week2`

## Common Development Commands

### Building the Project
```bash
./gradlew build                    # Full build with tests and JAR creation
./gradlew clean build              # Clean rebuild
./gradlew bootJar                  # Create executable JAR
```

### Running Tests
```bash
./gradlew test                     # Run all tests
./gradlew test --tests "*ClassName*"  # Run specific test class
./gradlew test --tests "*ClassName*.*methodName*"  # Run specific test method
```

### Code Quality
```bash
./gradlew jacocoTestReport         # Generate code coverage report (output: build/reports/jacoco/test/html/index.html)
```

### Running the Application
```bash
./gradlew bootRun                  # Run application in development mode
java -jar build/libs/hhplus-week2-0.0.1-SNAPSHOT.jar  # Run compiled JAR
```

### Gradle Information
```bash
./gradlew projects                 # List all projects
./gradlew dependencies             # Show dependency tree
./gradlew properties               # Show Gradle properties
```

## Project Architecture

### High-Level Structure

```
src/
├── main/
│   ├── kotlin/io/hhplus/week2/
│   │   ├── Week2Application.kt          (Spring Boot entry point)
│   │   └── ApiControllerAdvice.kt       (Global exception handler)
│   └── resources/
│       └── application.yml              (Application configuration)
└── test/
    ├── kotlin/io/hhplus/week2/          (Test packages)
    └── resources/                        (Test resources)
```

### Key Architectural Patterns

#### 1. Global Exception Handling (RestControllerAdvice)
- **Location:** `src/main/kotlin/io/hhplus/week2/ApiControllerAdvice.kt`
- All unhandled exceptions are caught and return a standardized `ErrorResponse` with:
  - Error code (String)
  - Error message (String)
  - HTTP 500 status by default
- Extends `ResponseEntityExceptionHandler` for built-in exception support
- Uses SLF4J logging for error tracking

#### 2. Application Entry Point
- **Location:** `src/main/kotlin/io/hhplus/week2/Week2Application.kt`
- Uses `@SpringBootApplication` for component scanning and auto-configuration
- Standard Spring Boot main function

### Testing Infrastructure

The project includes a comprehensive testing setup with:

- **Test Framework:** JUnit 5 (Jupiter) with AssertJ assertions
- **Mocking:** MockK (Kotlin-native mocking library with Spring integration)
- **Integration Testing:** TestContainers for Docker-based database testing
- **Test Data Generation:** FixtureMonkey for creating test fixtures
- **Database Testing:** Pre-configured MySQL TestContainers bundle
- **Code Coverage:** JaCoCo (v0.8.7) enabled for code coverage analysis

Tests should be placed in `src/test/kotlin/io/hhplus/week2/` and follow the same package structure as the main source code.

### Configuration

- **Configuration File:** `src/main/resources/application.yml`
- **Application Name:** hhplus-week2
- **Gradle Properties:** `gradle.properties` contains build settings and application metadata
  - Build caching and parallel builds enabled for faster builds
  - JVM max heap: 2GB

### Dependencies

**Core Stack:**
- Spring Boot 3.2.0 with Spring Cloud 2023.0.0
- Kotlin standard library and reflection
- Jackson for JSON processing (via spring-boot-starter-web)

**Optional Libraries Available** (configured in version catalog):
- Spring Data JPA for database access
- MySQL Connector for MySQL database support
- H2 Database for in-memory testing
- Redisson for Redis operations
- Micrometer/Prometheus for metrics and monitoring
- Spring Actuator for health checks

## Development Notes

1. **Null Safety:** The Kotlin compiler is configured with `-Xjsr305=strict` for Spring's null-safety annotations.

2. **Component Scanning:** Spring automatically scans the `io.hhplus.week2` package and subpackages for components (controllers, services, repositories, etc.).

3. **Error Responses:** All exceptions return the standardized `ErrorResponse` format. Customize exception handling by adding specific `@ExceptionHandler` methods to `ApiControllerAdvice`.

4. **Test Execution:** By default, test failures don't block the build (ignoreFailures = true) - adjust this in `build.gradle.kts` if you want strict test enforcement.

5. **Code Style:** Official Kotlin code style is enforced via `kotlin.code.style=official` in gradle.properties.

## Testing Best Practices for This Project

- Use MockK for mocking dependencies in unit tests
- Use TestContainers for integration tests requiring database access
- Use FixtureMonkey to generate test data instead of manual object creation
- Leverage AssertJ for readable, fluent assertions
- Place unit tests alongside the code they test using the same package structure
