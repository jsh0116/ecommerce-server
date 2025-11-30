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
./gradlew test                     # Run unit tests only (excludes integration tests)
./gradlew testIntegration          # Run integration tests with @Tag("integration")
./gradlew test testIntegration     # Run all tests (both unit and integration)
./gradlew test --tests "*ClassName*"  # Run specific test class
./gradlew test --tests "*ClassName*.*methodName*"  # Run specific test method
```

**Test Types:**
- **Unit Tests**: Fast, in-memory, no external dependencies (excluded by default from `./gradlew test`)
- **Integration Tests**: Run with Docker/TestContainers for MySQL and Redis, tagged with `@Tag("integration")`
- **Performance Tests**: Special tests tagged with `@Tag("performance")` for query optimization analysis

### Code Quality
```bash
./gradlew jacocoTestReport         # Generate code coverage report (output: build/reports/jacoco/test/html/index.html)
```

### Running the Application
```bash
./gradlew bootRun                  # Run application in development mode (server starts on port 8080)
java -jar build/libs/hhplus-ecommerce-0.0.1-SNAPSHOT.jar  # Run compiled JAR
```

### Docker & Local Development Environment
```bash
docker-compose up --build          # Start full stack (App + MySQL + Redis + PostgreSQL)
docker-compose up mysql redis      # Start only MySQL and Redis (lightweight)
docker build -t hhplus-ecommerce:latest .  # Build Docker image
docker run -p 8080:8080 hhplus-ecommerce:latest  # Run container

# Check service health
docker-compose ps                  # View running services
docker logs hhplus-ecommerce-api   # View application logs
docker logs hhplus-ecommerce-mysql # View database logs
```

**Services in docker-compose.yml:**
- **App**: Spring Boot application (port 8080)
- **MySQL**: Primary database (port 3306, user: root, password: root)
- **Redis**: Caching & distributed locking (port 6379)
- **PostgreSQL**: Alternative database option (port 5432, user: hhplus_user, password: hhplus_password)

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
- **Test Configuration:** `src/main/resources/application-test.yml` for integration tests with TestContainers
- **Application Name:** hhplus-ecommerce
- **Gradle Properties:** `gradle.properties` contains build settings and application metadata
  - Build caching and parallel builds enabled for faster builds
  - JVM max heap: 2GB

### Redis & Distributed Locking

The project uses **Redisson** for Redis-based distributed locking, particularly for concurrent coupon issuance:

- **Lock Service:** `application/services/impl/RedissonCouponLockService.kt`
- **Configuration:** Redis connection configured in `docker-compose.yml` (port 6379)
- **Usage Pattern:** Fine-grained locks per coupon ID to prevent race conditions while maintaining concurrency for different coupons
- **Timeout Handling:** Locks support configurable wait and hold times via `TimeUnit`

**Key Components:**
- `CouponLockService` interface for abstraction
- `RedissonCouponLockService` implementation using Redisson client
- Distributed locks ensure atomic check-then-act operations across server instances

**Docker Setup for Local Development:**
```yaml
redis:
  image: redis:7-alpine
  container_name: hhplus-redis
  ports:
    - "6379:6379"
```

Start Redis with: `docker-compose up redis` or access full stack with `docker-compose up`

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

### Unit Testing
- Use MockK for mocking Spring dependencies (`mockk`, `mockk { coEvery }`)
- Use FixtureMonkey to generate test data instead of manual object creation
- Leverage AssertJ for readable, fluent assertions
- Place unit tests alongside the code they test using the same package structure
- Test domain entity business logic (e.g., `Order.canPay()`, `Product.hasStock()`)
- Test use case orchestration with mock repositories
- Write concurrency tests for critical sections (e.g., coupon issuance)

### Integration Testing
- **Base Class:** Extend `IntegrationTestBase` which applies `@ActiveProfiles("test")`
- **TestContainers:** Automatically provision MySQL and Redis containers for tests
- **Configuration:** `application-test.yml` enables Docker-based testing with dynamic port mapping
- **Test Tags:** Use `@Tag("integration")` to mark tests that require external services
- **Running:** `./gradlew testIntegration` runs only integration tests

### Logging and Debugging in Tests
- Use SLF4J Logger for test output instead of `println()`:
```kotlin
import org.slf4j.LoggerFactory

class DatabaseIntegrationTest {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Test
    fun testSomething() {
        try {
            // ... test code ...
        } catch (e: Exception) {
            logger.warn("테스트 중 예외 발생: {}", e.message, e)
        }
    }
}
```

### JPA Persistence Context and Flush
- When modifying entities and then querying with SQL/JPQL in the same `@Transactional` method, use `flush()`:
```kotlin
@Test
fun testReservationExpiration() {
    // Save changes to Persistence Context
    val reservation = reservationRepository.findByOrderId(orderId)!!
    reservation.expiresAt = LocalDateTime.now().minusHours(1)
    reservationRepository.save(reservation)

    // Flush: Write Persistence Context changes to database immediately
    reservationRepository.flush()

    // Query: Now the SQL query will see the updated data
    val expiredCount = reservationService.expireReservations()
    assertThat(expiredCount).isEqualTo(1)
}
```
- **Why?** `save()` only updates the Persistence Context. SQL queries read directly from the database, so `flush()` is needed to sync.

**Example Integration Test Setup:**
```kotlin
@SpringBootTest
@Tag("integration")
class OrderIntegrationTest : IntegrationTestBase {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Autowired private val orderService: OrderService
    @Autowired private val orderRepository: OrderRepository

    @Test
    fun shouldCreateOrderAndReduceStock() {
        // Test with real database and Redis via TestContainers
        logger.info("테스트 시작: 주문 생성 및 재고 감소")
    }
}
```

### Performance & Query Optimization Tests
- Tests tagged with `@Tag("performance")` analyze query execution plans
- Use `EXPLAIN ANALYZE` for MySQL query optimization
- Examples: `ExplainAnalysisTest`, `OrderRepositoryOptimizationTest`
- These tests help identify N+1 queries and missing indexes

## Database Layer & JPA Integration

### Current State
- **ORM Framework:** Spring Data JPA with Hibernate
- **Primary Database:** MySQL 8.0 (configurable, see `application.yml`)
- **Schema Management:** Hibernate DDL auto mode `validate` (production) / `create-drop` (testing)
- **Dialect:** MySQL8Dialect in test environment
- **Connection Pool:** HikariCP with configurable pool size

### Key Configuration Settings
**In `application.yml`:**
- `hibernate.jdbc.lock_timeout`: 3000ms for pessimistic lock wait time
- `hibernate.jdbc.batch_size`: 20 for batch inserts/updates
- `hibernate.order_inserts/order_updates`: true for query optimization
- `show-sql: false`, `open-in-view: false` for production safety

**In `application-test.yml`:**
- `create-drop` mode for clean test isolation
- Dynamic datasource URLs via environment variables
- HikariCP pool: max-pool-size=5, minimum-idle=2 for testing

### Repository Pattern
- **Interface Layer:** Service layer depends on repository interfaces (e.g., `OrderRepository`)
- **Implementation:** Spring Data JPA repositories auto-implement CRUD operations
- **Location:** `infrastructure/repositories/` for future implementations
- **Query Methods:** Use Spring Data query derivation (e.g., `findByUserId`, `findByStatusAndCreatedAtAfter`)

### Concurrency Control in Database Layer
- **Pessimistic Locking:** `SELECT ... FOR UPDATE` with 3-second timeout (see `lock_timeout`)
- **Optimistic Locking:** Version columns with `@Version` annotation for conflict detection
- **Distributed Locks:** Redisson-based Redis locks for cross-JVM scenarios (coupon issuance)

### Performance Considerations
- **N+1 Query Prevention:** Use `@EntityGraph`, `JOIN FETCH`, or explicit projections
- **Batch Operations:** Leverage HikariCP batch size settings for bulk inserts
- **Indexing:** Ensure foreign keys and frequently queried columns have indexes
- **Query Analysis:** Run `EXPLAIN ANALYZE` tests to verify query plans before production
