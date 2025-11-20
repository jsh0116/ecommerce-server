# 트랜잭션 기본 개념

**트랜잭션이란?**

트랜잭션(Transaction)은 데이터베이스의 **논리적 작업 단위**입니다. 여러 개의 쿼리를 하나의 작업으로 묶어서 **모두 성공하거나 모두 실패하도록** 보장합니다.

**왜 트랜잭션이 필요한가?**

실무에서는 **여러 테이블을 동시에 수정**해야 하는 경우가 빈번합니다. 트랜잭션이 없다면 일부만 성공하고 일부는 실패하여 **데이터 불일치**가 발생할 수 있습니다.

### 트랜잭션이 필요한 실무 시나리오

1. **은행 계좌 이체**: A 계좌에서 출금 + B 계좌로 입금
2. **주문 처리**: 재고 차감 + 주문 생성 + 결제 처리
3. **회원 가입**: 사용자 정보 저장 + 기본 권한 할당 + 웰컴 포인트 지급
4. **게시글 삭제**: 게시글 삭제 + 댓글 삭제 + 첨부파일 삭제

```mermaid
sequenceDiagram
    participant User
    participant App
    participant DB

    Note over User,DB: 계좌 이체 시나리오
    User->>App: A→B 10만원 이체
    App->>DB: BEGIN TRANSACTION
    App->>DB: UPDATE account SET balance -= 100000 WHERE id = 'A'
    App->>DB: UPDATE account SET balance += 100000 WHERE id = 'B'
    alt 성공
        App->>DB: COMMIT
        DB-->>User: 이체 완료
    else 실패
        App->>DB: ROLLBACK
        DB-->>User: 이체 취소
    end

```

### ACID 속성

```mermaid
graph TB
    subgraph "ACID Properties"
        A[Atomicity<br/>원자성]
        C[Consistency<br/>일관성]
        I[Isolation<br/>격리성]
        D[Durability<br/>지속성]
    end

    A --> A1[All or Nothing<br/>전부 성공 또는 전부 실패]
    C --> C1[제약조건 유지<br/>데이터 무결성 보장]
    I --> I1[동시 실행 격리<br/>트랜잭션 간 간섭 방지]
    D --> D1[영구 저장<br/>시스템 장애에도 유지]

    style A fill:#FFE4B5
    style C fill:#87CEEB
    style I fill:#90EE90
    style D fill:#F0E68C

```

> 📚 참고: ACID Properties
>
> - [Wikipedia - ACID](https://en.wikipedia.org/wiki/ACID)
> - [Martin Kleppmann - Designing Data-Intensive Applications](https://dataintensive.net/)

### 트랜잭션 상태 다이어그램

```mermaid
stateDiagram-v2
    [*] --> Active: BEGIN
    Active --> PartiallyCommitted: 마지막 문장 실행
    PartiallyCommitted --> Committed: COMMIT 성공
    Active --> Failed: 오류 발생
    PartiallyCommitted --> Failed: COMMIT 실패
    Failed --> Aborted: ROLLBACK
    Aborted --> [*]
    Committed --> [*]

```

### 트랜잭션 구현 예시

**MySQL/MariaDB**

```sql
-- 명시적 트랜잭션 시작
START TRANSACTION;
-- 또는 BEGIN;

-- 주문 처리 예시
INSERT INTO orders (user_id, total_amount, status)
VALUES (1, 50000, 'pending');

SET @order_id = LAST_INSERT_ID();

-- 주문 상품 추가
INSERT INTO order_items (order_id, product_id, quantity, price)
VALUES
    (@order_id, 101, 2, 15000),
    (@order_id, 102, 1, 20000);

-- 재고 차감
UPDATE inventory
SET quantity = quantity - 2
WHERE product_id = 101 AND quantity >= 2;

-- 영향받은 행이 0이면 재고 부족
IF ROW_COUNT() = 0 THEN
    ROLLBACK;
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = '재고 부족';
END IF;

UPDATE inventory
SET quantity = quantity - 1
WHERE product_id = 102 AND quantity >= 1;

IF ROW_COUNT() = 0 THEN
    ROLLBACK;
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = '재고 부족';
END IF;

-- 모든 작업 성공 시 커밋
COMMIT;
```

**PostgreSQL**

```sql
BEGIN;

-- SAVEPOINT 사용 (중간 체크포인트)
SAVEPOINT before_inventory_update;

UPDATE inventory SET quantity = quantity - 10
WHERE product_id = 1
RETURNING quantity;

-- 결과 확인 후 부분 롤백 가능
ROLLBACK TO SAVEPOINT before_inventory_update;

-- 다시 시도
UPDATE inventory SET quantity = quantity - 5
WHERE product_id = 1;

COMMIT;
```

### **격리 수준 (Isolation Levels)**

**격리 수준이란?**

동시에 실행되는 트랜잭션들이 서로에게 영향을 미치는 정도를 제어하는 설정입니다. 격리 수준이 높을수록 데이터 일관성은 보장되지만 동시성은 떨어집니다.

**동시성 문제 현상**

### **격리 수준별 특징**

| **격리 수준** | **Dirty Read** | **Non-Repeatable Read** | **Phantom Read** | **동시성** |
| --- | --- | --- | --- | --- |
| READ UNCOMMITTED | 발생 | 발생 | 발생 | 최고 |
| READ COMMITTED | 방지 | 발생 | 발생 | 높음 |
| REPEATABLE READ | 방지 | 방지 | 발생* | 중간 |
| SERIALIZABLE | 방지 | 방지 | 방지 | 최저 |

MySQL InnoDB는 REPEATABLE READ에서도 Phantom Read를 방지

### 각 격리 수준의 상세 동작

**READ UNCOMMITTED**

- 커밋되지 않은 데이터도 읽을 수 있음
- 거의 사용하지 않음 (데이터 정합성 보장 안 됨)
- 예: 실시간 모니터링, 대략적인 통계

**READ COMMITTED**

- 커밋된 데이터만 읽기 가능
- PostgreSQL, Oracle 기본값
- 대부분의 웹 애플리케이션에 적합

**REPEATABLE READ**

- 트랜잭션 내에서 같은 데이터를 여러 번 읽어도 같은 값
- MySQL InnoDB 기본값
- MVCC(Multi-Version Concurrency Control) 사용

**SERIALIZABLE**

- 트랜잭션을 순차적으로 실행한 것처럼 보장
- 모든 SELECT에 자동으로 LOCK
- 성능 문제로 특수한 경우만 사용

### 격리 수준 설정 및 확인

```sql
-- MySQL 현재 격리 수준 확인
SELECT @@GLOBAL.transaction_isolation, @@SESSION.transaction_isolation;

-- 세션 레벨 변경
SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;

-- 다음 트랜잭션만 변경
SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;

-- PostgreSQL
SHOW transaction_isolation;

-- 특정 트랜잭션에만 적용
BEGIN ISOLATION LEVEL REPEATABLE READ;

-- 전역 설정 (MySQL)
SET GLOBAL TRANSACTION ISOLATION LEVEL REPEATABLE READ;

-- 전역 설정 (PostgreSQL - postgresql.conf)
-- default_transaction_isolation = 'repeatable read'

```

### **실무 예제: 격리 수준별 동작 차이**

```sql
-- 테스트 테이블 준비
CREATE TABLE products (
    id INT PRIMARY KEY,
    name VARCHAR(100),
    price DECIMAL(10,2),
    stock INT
);

INSERT INTO products VALUES (1, 'Laptop', 1000000, 10);

-- Session 1: READ COMMITTED
SET TRANSACTION ISOLATION LEVEL READ COMMITTED;
BEGIN;
SELECT stock FROM products WHERE id = 1; -- 10

-- Session 2: 재고 업데이트
UPDATE products SET stock = 5 WHERE id = 1;

-- Session 1: 다시 조회
SELECT stock FROM products WHERE id = 1; -- 5 (Non-Repeatable Read 발생)

-- Session 1: REPEATABLE READ로 변경시
SET TRANSACTION ISOLATION LEVEL REPEATABLE READ;
BEGIN;
SELECT stock FROM products WHERE id = 1; -- 5

-- Session 2: 다시 업데이트
UPDATE products SET stock = 3 WHERE id = 1;

-- Session 1: 다시 조회
SELECT stock FROM products WHERE id = 1; -- 여전히 5 (Repeatable Read 보장)
```

제공해주신 락(Lock) 메커니즘 요약 자료 아주 잘 정리하셨네요. S-Lock, X-Lock, 데드락의 기본 개념을 명확히 짚어주셨습니다.

지난번 트랜잭션 설명에 이어, 이 락(Lock) 개념을 **Spring Boot + JPA (Kotlin)** 환경에서 어떻게 실용적으로 적용하는지, 그리고 요약 자료에서 한 걸음 더 나아간 실무적인 고려사항들을 보충 설명해 드릴게요.

-----

## 🚀 1. Spring (JPA)에서 락은 언제, 어떻게 동작하는가?

제공 자료의 `SELECT ... FOR UPDATE` 같은 SQL을 직접 날릴 일은 드뭅니다. JPA가 대부분을 추상화해주기 때문이죠.

### 1-1. 묵시적 락 (Implicit Locking)

개발자가 요청하지 않아도 JPA가 **자동으로** 거는 락입니다.

* **X-Lock (쓰기 락):**
    * `JPA save()` (Update 시), `delete()` 호출 시: JPA는 해당 엔티티(Row)에 **X-Lock**을 겁니다.
    * 트랜잭션이 커밋될 때까지 다른 트랜잭션은 이 Row를 수정/삭제할 수 없습니다.

### 1-2. 명시적 락 (Explicit Locking)

개발자가 **직접** 락을 요청하는 것입니다. 이것이 바로 지난번 논의했던 '재고 동시성' 문제의 핵심입니다.

JPA에서는 `@Lock` 어노테이션을 사용합니다.

```kotlin
// ProductRepository.kt
interface ProductRepository : JpaRepository<Product, Long> {

    // ⭐️ X-Lock (Exclusive Lock)
    // SQL: SELECT ... FOR UPDATE
    // "이 Row는 나만 쓸 거니까, 수정/읽기 모두 대기시켜!"
    // (지난번 재고 차감 시나리오에서 사용)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    fun findByIdWithPessimisticWriteLock(id: Long): Product?

    // ⭐️ S-Lock (Shared Lock)
    // SQL: SELECT ... FOR SHARE
    // "나는 읽기만 할게. 다른 S-Lock은 허용. (X-Lock은 대기)"
    // 예: 주문 처리 중 '상품 정보'가 절대 변경되면 안 될 때
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    fun findByIdWithPessimisticReadLock(id: Long): Product?
}

// OrderService.kt
@Service
class OrderService(private val productRepository: ProductRepository) {

    @Transactional
    fun createOrder(productId: Long, quantity: Int) {
        // 1. 트랜잭션 시작
        // 2. 이 시점에 Product Row에 X-Lock이 걸림 (PESSIMISTIC_WRITE)
        val product = productRepository.findByIdWithPessimisticWriteLock(productId)
            ?: throw EntityNotFoundException("상품 없음")

        // 3. 다른 트랜잭션은 이 상품의 Lock이 풀릴 때까지 대기
        if (product.stock < quantity) {
            throw RuntimeException("재고 부족")
        }
        product.stock -= quantity
        
        // 4. 주문 생성 로직...
        
        // 5. 트랜잭션 커밋 (이때 Lock 해제)
    }
}
```

이것을 **비관적 락 (Pessimistic Locking)** 이라고 부릅니다. "충돌은 어차피 일어날 거야"라고 가정하고 DB 레벨에서 선제적으로 락을 거는 방식이죠.

-----

## 📈 2. (자료에 없는) 낙관적 락 (Optimistic Locking)

이커머스에서 비관적 락만큼, 혹은 그보다 더 많이 쓰이는 중요한 개념입니다.

**"충돌은 잘 일어나지 않을 거야. 일단 진행하고, 혹시 충돌나면 그때 가서 처리하자\!"** 라는 접근법입니다. DB 락 대신 **버전(Version) 정보**를 사용합니다.

### 2-1. 동작 원리 (JPA `@Version`)

1.  **엔티티에 버전 필드 추가:**

    ```kotlin
    @Entity
    data class Product(
        @Id val id: Long,
        var name: String,
        var stock: Int,

        // ⭐️ 낙관적 락을 위한 버전 필드
        @Version
        val version: Long = 0L 
    )
    ```

2.  **트랜잭션 1 (T1) - 상품 수정:**

    * T1이 '노트북' (version=1, stock=10) 정보를 읽습니다.
    * T1이 재고를 9로 변경하고 `save()` (커밋) 시도.
    * JPA가 날리는 SQL:
      ```sql
      UPDATE product SET stock = 9, version = 2 
      WHERE id = 1 AND version = 1; -- ⭐️ 중요
      ```
    * 성공 (1개 Row 변경). T1 커밋 완료.

3.  **동시성 충돌 시나리오:**

    * T1: '노트북' (version=1, stock=10) 읽음.
    * T2: '노트북' (version=1, stock=10) 읽음.
    * T1: 재고 9로 변경 후 커밋. (DB: version=2, stock=9)
    * T2: (T1이 커밋된 줄 모름) 재고 8로 변경 후 커밋 시도.
    * JPA가 날리는 SQL:
      ```sql
      UPDATE product SET stock = 8, version = 2 
      WHERE id = 1 AND version = 1; -- ⭐️ T2는 여전히 version=1로 알고 있음
      ```
    * **실패\!** (0개 Row 변경). DB의 현재 버전은 2인데, T2는 1을 기준으로 업데이트하려 했기 때문입니다.
    * JPA는 T2에게 `ObjectOptimisticLockingFailureException` 예외를 발생시킵니다.

### 2-2. 비관적 vs 낙관적: 언제 무엇을 쓸까?

| 구분 | 비관적 락 (Pessimistic) | 낙관적 락 (Optimistic) |
| --- | --- | --- |
| **핵심** | DB 락 (`FOR UPDATE`) | 어플리케이션 버전 (`@Version`) |
| **동작** | 락이 풀릴 때까지 **대기** | 충돌 시 **예외 발생** (실패) |
| **성능** | 락 경합 시 성능 저하 | 롤백/재시도 로직 필요 |
| **적합한 곳** | **충돌이 빈번한** 작업 (e.g., **재고 차감**, 선착순 티켓팅) | **충돌이 드문** 작업 (e.g., 상품 설명 수정, 유저 정보 변경) |

-----

## 🔒 3. (자료 보충) Intention Lock의 역할

자료의 다이어그램에 `Intention Lock`이 언급되었네요. 이건 개발자가 직접 쓰는 락은 아니지만, MySQL(InnoDB)이 효율성을 위해 내부적으로 사용합니다.

* **목적:** 테이블 락(Table-Level)과 로우 락(Row-Level)의 공존.
* **시나리오:**
    1.  T1이 **Row A**에 X-Lock (쓰기 락)을 걸려고 합니다.
    2.  이때 T1은 **Row A**에 X-Lock을 거는 동시에, **Table 전체**에 **"IX-Lock" (Intention-Exclusive)** 이라는 '표시'를 남깁니다.
    3.  이 '표시'의 의미는 "나 이 테이블의 *어떤 Row*에 X-Lock 걸었어\!" 입니다.
    4.  이때 T2가 `ALTER TABLE` (테이블 전체 X-Lock)을 시도합니다.
    5.  T2는 테이블의 Row를 일일이 확인할 필요 없이, 테이블의 **Intention Lock (IX-Lock)** '표시'만 보고도 "아, 누가 Row 쓰고 있네. 대기해야겠다"라고 바로 판단할 수 있습니다.

**요약:** Intention Lock은 Row 락의 존재를 Table 레벨에 알려주는 '안내판' 역할을 하여 락 검사 효율을 높여줍니다.

-----

## 💥 4. Spring 환경에서의 데드락

자료에서 데드락의 고전적인 예시(T1: A-\>B, T2: B-\>A)를 잘 보여줬습니다. Spring 환경에서는 이게 서비스 로직 순서로 나타납니다.

**시나리오:** '주문 서비스'와 '유저 서비스'

* `OrderService` (T1): \*\*주문(A)\*\*을 생성하고, \*\*유저(B)\*\*의 포인트를 적립한다. (락 순서: A -\> B)
* `UserService` (T2): **유저(B)** 정보를 수정하고, 해당 유저의 최근 **주문(A)** 상태를 갱신한다. (락 순서: B -\> A)

두 트랜잭션이 동시에 실행되면 데드락이 발생할 수 있습니다.

**해결책 (자료 인용):** **Lock 순서 통일**.

* 팀(조직) 내에서 "유저와 주문을 함께 다룰 때는, 반드시 **유저(B)를 먼저** 락을 걸고, 그다음 \*\*주문(A)\*\*을 락 건다"와 같은 **규칙**을 정하고 모든 코드에서 준수해야 합니다.

이처럼 DB 락 메커니즘은 Spring과 JPA라는 추상화 뒤에 숨겨져 있지만, 고성능 이커머스 서비스를 만들기 위해서는 그 원리를 정확히 이해하고 \*\*'비관적 락'\*\*과 **'낙관적 락'** 중 상황에 맞는 전략을 선택하는 것이 시니어 개발자의 중요한 역량입니다.