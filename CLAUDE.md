# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**hhplus-ecommerce** is an e-commerce platform built with Spring Boot 3.2.0 and Kotlin, implementing a clothing retail system with product management, inventory control, order processing, and coupon functionality. The project follows clean architecture principles with domain-driven design and layered architecture.

- **Language:** Kotlin 1.9.21
- **Framework:** Spring Boot 3.2.0
- **Build Tool:** Gradle 8.4+ (Kotlin DSL)
- **Java Target:** Java 17
- **Package Root:** `io.hhplus.ecommerce`
- **API Documentation:** Swagger/OpenAPI (Springdoc) at `/swagger-ui.html`
- **Server Port:** 8080 (default)

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
./gradlew bootRun                  # Run application in development mode (server starts on port 8080)
java -jar build/libs/hhplus-ecommerce-0.0.1-SNAPSHOT.jar  # Run compiled JAR
```

### Docker
```bash
docker-compose up --build          # Start application with Docker Compose
docker build -t hhplus-ecommerce:latest .  # Build Docker image
docker run -p 8080:8080 hhplus-ecommerce:latest  # Run container
```

### Swagger UI
```bash
# After starting the application, access Swagger UI at:
# http://localhost:8080/swagger-ui.html
# OpenAPI JSON spec: http://localhost:8080/v3/api-docs
```

### Gradle Information
```bash
./gradlew projects                 # List all projects
./gradlew dependencies             # Show dependency tree
./gradlew properties               # Show Gradle properties
```

## Project Architecture

### High-Level Structure

The project follows a clean architecture / layered architecture pattern:

```
src/main/kotlin/io/hhplus/ecommerce/
├── EcommerceApplication.kt              (Spring Boot entry point)
├── ApiControllerAdvice.kt           (Global exception handler)
│
├── presentation/                     (Presentation Layer - REST endpoints)
│   └── controllers/
│       ├── ProductController.kt     (Product APIs)
│       ├── OrderController.kt       (Order APIs)
│       ├── CouponController.kt      (Coupon APIs)
│       └── InventoryController.kt   (Inventory APIs)
│
├── application/                      (Application Layer - Use cases/orchestration)
│   ├── usecases/
│   │   ├── ProductUseCase.kt        (Product business workflows)
│   │   ├── OrderUseCase.kt          (Order creation & payment workflows)
│   │   └── CouponUseCase.kt         (Coupon issuance & validation)
│   │
│   └── services/
│       ├── ProductService.kt        (Product business logic)
│       ├── OrderService.kt          (Order business logic)
│       ├── CouponService.kt         (Coupon business logic)
│       ├── InventoryService.kt      (Inventory business logic)
│       └── impl/                    (Service implementations)
│
├── domain/                           (Domain Layer - Business entities & logic)
│   ├── Product.kt                   (Product entity with business methods)
│   ├── Order.kt                     (Order entity with state management)
│   ├── Coupon.kt                    (Coupon entity with validation logic)
│   ├── Inventory.kt                 (Inventory entity with stock management)
│   └── User.kt                      (User entity)
│
├── infrastructure/                   (Infrastructure Layer - Persistence & external services)
│   ├── repositories/                (Data Access Layer - Repository implementations)
│   │   └── memory/                  (In-memory mock implementations for testing)
│   │       ├── ProductRepositoryMemory.kt
│   │       ├── OrderRepositoryMemory.kt
│   │       ├── CouponRepositoryMemory.kt
│   │       ├── InventoryRepositoryMemory.kt
│   │       └── UserRepositoryMemory.kt
│   │
│   ├── cache/                        (Caching layer)
│   │   ├── CacheService.kt
│   │   └── impl/
│   │       └── MockCacheService.kt
│   │
│   └── services/
│       ├── DataTransmissionService.kt
│       └── impl/
│           └── MockDataTransmissionService.kt
│
├── dto/                              (Data Transfer Objects)
│   ├── ProductDtos.kt               (Request/Response DTOs for products)
│   ├── OrderDtos.kt                 (Request/Response DTOs for orders)
│   ├── CouponDtos.kt                (Request/Response DTOs for coupons)
│   └── InventoryDtos.kt             (Request/Response DTOs for inventory)
│
└── config/
    └── OpenApiConfig.kt             (Swagger/OpenAPI configuration)
```

### Key Architectural Patterns

#### 1. Clean Architecture Layers
The codebase follows a layered architecture with clear separation of concerns:

- **Controller Layer**: HTTP endpoints that handle requests/responses, delegating to use cases
- **Application Layer (Use Cases)**: Orchestrates complex business workflows involving multiple services
- **Service Layer**: Business logic for individual domains
- **Domain Layer**: Rich domain models with business rules and validation
- **Repository Layer**: Abstraction for data persistence (interfaces + implementations)
- **Infrastructure Layer**: External integrations (cache, external APIs, etc.)

**Key Flow Pattern**:
```
Controller → UseCase → Service → Domain → Repository
```

Example: Order creation flows from `OrderController` → `OrderUseCase` → `OrderService` + `ProductService` → `Order` domain entity → `OrderRepository`

#### 2. Domain-Driven Design
- **Rich Domain Models**: Domain entities (`Order`, `Product`, `Coupon`, `Inventory`) contain business logic, not just data
- **Business Rules in Domain**: Stock validation, order state transitions, coupon validation live in domain entities
- **Repository Pattern**: Data access abstracted behind interfaces with mock implementations for testing

#### 3. Global Exception Handling (RestControllerAdvice)
- **Location:** `ApiControllerAdvice.kt`
- All unhandled exceptions are caught and return a standardized `ErrorResponse` with:
  - Error code (String)
  - Error message (String)
  - HTTP 500 status by default
- Extends `ResponseEntityExceptionHandler` for built-in exception support
- Uses SLF4J logging for error tracking

#### 4. Use Case Pattern
- **Location:** `application/` package
- Use cases orchestrate complex workflows that involve multiple services
- Example: `OrderUseCase.processPayment()` coordinates order validation, balance checking, stock reduction, coupon usage, and external data transmission
- Use cases handle cross-cutting concerns like data transmission failures (retry queue pattern)

#### 5. Constructor Injection
- All Spring components use constructor-based dependency injection (Kotlin primary constructor)
- Example: `class OrderController(private val orderService: OrderService, ...)`
- Improves testability and makes dependencies explicit

### Testing Infrastructure

The project includes a comprehensive testing setup with:

- **Test Framework:** JUnit 5 (Jupiter) with AssertJ assertions
- **Mocking:** MockK (Kotlin-native mocking library with Spring integration)
- **Integration Testing:** TestContainers for Docker-based database testing
- **Test Data Generation:** FixtureMonkey for creating test fixtures
- **Database Testing:** Pre-configured MySQL TestContainers bundle
- **Code Coverage:** JaCoCo (v0.8.7) enabled for code coverage analysis

Tests should be placed in `src/test/kotlin/io/hhplus/ecommerce/` and follow the same package structure as the main source code.

### Configuration

- **Configuration File:** `src/main/resources/application.yml`
- **Application Name:** hhplus-ecommerce
- **Gradle Properties:** `gradle.properties` contains build settings and application metadata
  - Build caching and parallel builds enabled for faster builds
  - JVM max heap: 2GB

### Dependencies

**Core Stack:**
- Spring Boot 3.2.0 with Spring Cloud 2023.0.0
- Kotlin standard library and reflection
- Jackson for JSON processing (via spring-boot-starter-web)
- Springdoc OpenAPI 2.0.2 for Swagger UI and API documentation

**Optional Libraries Available** (configured in version catalog):
- Spring Data JPA for database access
- MySQL Connector for MySQL database support
- H2 Database for in-memory testing
- Redisson for Redis operations
- Micrometer/Prometheus for metrics and monitoring
- Spring Actuator for health checks

## Business Domain

This e-commerce platform implements the following business capabilities:

### Product Management
- Product catalog with categories, pricing, and variants
- Stock availability checking
- Top-selling products tracking
- Product search and filtering

### Order Processing
- Order creation with multiple items
- Order state management (PENDING → PAID → PREPARING → SHIPPED → DELIVERED)
- Order cancellation with business rules (cannot cancel after shipping)
- Stock reservation during order creation (15-minute TTL)

### Inventory Management
- Real-time stock tracking
- Stock reservation system
- Stock deduction on payment completion
- Stock restoration on order cancellation

### Coupon System
- First-come-first-served coupon issuance with quantity limits
- Coupon validation and discount calculation
- User coupon management with expiration
- Coupon usage tracking

### Payment Flow
1. Order creation validates stock and reserves inventory
2. Payment validation checks user balance
3. Payment completion triggers:
   - Stock deduction
   - Coupon usage
   - Order status update to PAID
   - External data transmission (with retry queue on failure)

## Architecture Evolution & Refactoring

The codebase recently underwent a significant refactoring to improve architectural clarity and separation of concerns:

### Key Changes
1. **Layer Reorganization**:
   - Created explicit `presentation/controllers/` layer for REST endpoints
   - Separated `application/usecases/` from business logic in `application/services/`
   - Consolidated repositories under `infrastructure/repositories/memory/` for in-memory implementations
2. **Service Architecture**: Services now implement business logic with clear interfaces, supporting both in-memory and future database implementations
3. **Test Structure**: Test package hierarchy mirrors main source code for easier navigation

### Migration Notes
- All feature branches and development work should use the new `io.hhplus.ecommerce` package structure
- Import statements have been updated throughout the codebase
- Swagger/OpenAPI configuration and endpoint paths remain unchanged

## Development Notes

1. **Null Safety:** The Kotlin compiler is configured with `-Xjsr305=strict` for Spring's null-safety annotations.

2. **Component Scanning:** Spring automatically scans the `io.hhplus.ecommerce` package and subpackages for components (controllers, services, repositories, etc.).

3. **Error Responses:** All exceptions return the standardized `ErrorResponse` format. Customize exception handling by adding specific `@ExceptionHandler` methods to `ApiControllerAdvice`.

4. **Test Execution:** By default, test failures don't block the build (ignoreFailures = true) - adjust this in `build.gradle.kts` if you want strict test enforcement.

5. **Code Style:** Official Kotlin code style is enforced via `kotlin.code.style=official` in gradle.properties.

6. **In-Memory Repositories:** The project uses in-memory repository implementations (in `infrastructure/repositories/memory/`) for development and testing. These provide fast, non-persistent storage suitable for unit tests and local development. Real database implementations (using Spring Data JPA, etc.) can be added when needed for production use.

7. **Data Classes:** Kotlin data classes are heavily used for domain entities, DTOs, and value objects. They provide built-in `equals()`, `hashCode()`, `toString()`, and `copy()` methods.

8. **Swagger Annotations:** Controllers use OpenAPI 3.0 annotations (`@Operation`, `@ApiResponse`, `@Parameter`, `@Tag`) for API documentation.

## Kotlin & Spring Best Practices

### Kotlin Idioms
- **Leverage Kotlin features**: Use null safety, data classes, extension functions, and Kotlin stdlib functions (`let`, `run`, `apply`, `also`, `with`)
- **Prefer Kotlin over Java style**: Use Kotlin idioms instead of Java-style code when working with Spring
- **Null safety**: Take advantage of Kotlin's null safety system and avoid using `!!` operator
- **Immutability**: Prefer immutable properties (`val`) over mutable ones (`var`)
- **Data classes**: Use data classes for DTOs, entities, and value objects

### Spring Integration
- **Constructor injection**: Use Kotlin primary constructor for dependency injection (already used throughout the codebase)
- **Extension functions**: Consider using Kotlin extension functions to enhance Spring APIs
- **Nullable parameters**: Use nullable types (`String?`) appropriately for optional request parameters

### Code Examples from This Project

**Good - Constructor injection with primary constructor:**
```kotlin
@RestController
@RequestMapping("/api/v1/orders")
class OrderController(
    private val orderService: OrderService,
    private val productService: ProductService
)
```

**Good - Rich domain model with business logic:**
```kotlin
data class Order(
    val id: String,
    val userId: String,
    var status: String = "PENDING"
) {
    fun canPay(): Boolean = status == "PENDING" && finalAmount > 0

    fun complete() {
        if (!canPay()) throw IllegalStateException("결제할 수 없는 주문입니다")
        status = "PAID"
        paidAt = LocalDateTime.now()
    }
}
```

**Good - Using companion object for factory methods:**
```kotlin
data class OrderItem(...) {
    companion object {
        fun create(product: Product, quantity: Int): OrderItem {
            return OrderItem(
                productId = product.id,
                productName = product.name,
                quantity = quantity,
                unitPrice = product.price,
                subtotal = product.calculatePrice(quantity)
            )
        }
    }
}
```

## Testing Best Practices for This Project

- Use MockK for mocking dependencies in unit tests
- Use TestContainers for integration tests requiring database access
- Use FixtureMonkey to generate test data instead of manual object creation
- Leverage AssertJ for readable, fluent assertions
- Place unit tests alongside the code they test using the same package structure
- Test domain entity business logic (e.g., `Order.canPay()`, `Product.hasStock()`)
- Test use case orchestration with mock repositories
- Write concurrency tests for critical sections (e.g., coupon issuance)
