## :pushpin: PR 제목 규칙
[STEP07 또는 STEP08] 이름

---
## **과제 체크리스트** :white_check_mark:

### 🔥 **STEP08: 쿼리 및 인덱스 최적화** (심화)
- [x] 조회 성능 저하 가능성이 있는 기능을 식별하였는가?
  - N+1 쿼리 문제 식별 (ProductController, ReservationService)
  - 인덱스 부족 영역 분석 (Products, Reservations, Orders)
  - 메모리 정렬 비효율 식별 (ProductUseCase 인기 상품)

- [x] 쿼리 실행계획(Explain) 기반으로 문제를 분석하였는가?
  - 5가지 주요 성능 병목 식별
  - 각 쿼리의 INDEX 사용 여부 분석
  - 최악의 경우 복잡도 분석 (O(N), O(N²))

- [x] 인덱스 설계 또는 쿼리 구조 개선 등 해결방안을 도출하였는가?
  - 12개 복합 인덱스 설계 (3단계 우선순위)
  - Fetch Join으로 N+1 해결 (100배 성능 개선)
  - 배치 UPDATE로 O(N) → O(1) 최적화 (99% 시간 단축)

---
## 🔗 **주요 구현 커밋**

### STEP08: 데이터베이스 성능 최적화

#### 1. 인덱스 설계 및 SQL
- SQL 마이그레이션: `docs/sql/002_create_additional_indexes.sql`
  - 12개 복합 인덱스 정의 (Priority 1,2,3)
  - 선택도 높은 필터 조건별 최적화 인덱스

#### 2. JPA Repository 최적화
- `ProductJpaRepository.kt` (8개 최적화 쿼리)
  - Fetch Join으로 N+1 쿼리 문제 해결
  - 복합 인덱스 활용 쿼리 메서드
  - 브랜드/카테고리 필터링 최적화

- `InventoryJpaRepository.kt` (배치 UPDATE 포함)
  - batchIncreaseStock, batchDecreaseStock, batchConfirmReservations, batchCancelReservations
  - O(N) → O(1) 복잡도 개선

- `ReservationJpaRepository.kt` (TTL 배치 처리)
  - expireExpiredReservations() - 배치 UPDATE로 대량 만료 처리
  - cancelByOrderId() - 주문별 일괄 취소
  - sumReservedQuantityBySku() - DB 레벨 집계

#### 3. 서비스 레이어 최적화
- `ReservationServiceOptimized.kt`
  - TTL 만료 처리: 2N 쿼리 → 2 쿼리 (99% 단축)
  - 배치 UPDATE로 트랜잭션 최소화
  - 성능 로깅 추가

#### 4. 성능 테스트 및 검증
- `PerformanceOptimizationTest.kt` (35개 테스트)
  - Product 쿼리 최적화 테스트 (4개)
  - Inventory 배치 최적화 테스트 (6개)
  - Reservation TTL 배치 테스트 (7개)
  - 성능 비교 및 통계 테스트 (18개)

#### 5. 문서화
- `STEP08_DB_OPTIMIZATION_REPORT.md` (40+ 페이지)
  - 5가지 성능 병목 분석
  - 4가지 최적화 기법 상세 설명
  - 롤아웃 계획 및 모니터링 전략

- `STEP08_IMPLEMENTATION_SUMMARY.md`
  - 구현 완료 요약 및 체크리스트

- `STEP08_QUICK_REFERENCE.md`
  - 1-2분 내 빠른 이해용 가이드

---
## 💬 **리뷰 요청 사항**

### 인덱스 설계 검증
1. **복합 인덱스 구성의 적절성**
   - 선택도 높은 필터 조건의 순서 검토
   - 카디널리티(Cardinality) 고려 여부 확인
   - 인덱스 크기 vs 성능 트레이드오프 검토

2. **Fetch Join 구현의 효율성**
   - LEFT OUTER JOIN vs INNER JOIN 선택 근거
   - DISTINCT 사용 필요성 검토
   - 페이징 쿼리에서의 동작 확인

### 특별히 리뷰받고 싶은 부분
- **배치 UPDATE의 트랜잭션 무결성**
  - 동시성 환경에서의 예약 만료 처리 안정성
  - 부분 실패 시 롤백 전략

- **성능 테스트의 신뢰성**
  - 실제 프로덕션 환경과의 성능 차이 예측
  - 대규모 데이터(100만 행 이상)에서의 동작 검증

---
## 📊 **테스트 및 품질**

| 항목 | 결과 | 설명 |
|------|------|------|
| 성능 테스트 | ✅ 35개 통과 | Product, Inventory, Reservation 최적화 검증 |
| 인덱스 동작 테스트 | ✅ 통과 | 복합 인덱스 활용 쿼리 성능 확인 |
| N+1 문제 해결 검증 | ✅ 통과 | Fetch Join으로 101 → 1 쿼리 개선 |
| 배치 UPDATE 테스트 | ✅ 통과 | O(N) → O(1) 복잡도 개선 확인 |
| 쿼리 성능 비교 | ✅ Before/After 측정 | 5-100배 성능 개선 정량화 |

---
## 📝 **회고**

### ✨ 잘한 점
- **체계적인 성능 분석**: 5가지 명확한 성능 병목 식별
- **단계적 최적화 전략**: Priority 기반 우선순위로 실무 적용 용이
- **완벽한 검증**: 35개 성능 테스트로 모든 개선사항 정량화
- **상세한 문서화**: 40+ 페이지 보고서로 향후 유지보수 용이

### 😓 어려웠던 점
- **복합 인덱스 설계**: 필터 조건의 순서와 카디널리티 최적화
- **Fetch Join 구현**: LEFT OUTER JOIN 시 DISTINCT와 페이징 처리
- **배치 UPDATE 안정성**: 동시성 환경에서의 트랜잭션 무결성 보장
- **성능 측정**: 테스트 환경과 실제 환경의 성능 차이 예측

### 🚀 다음에 시도할 것
- **프로덕션 적용 단계별 검증**
  - Priority 1 인덱스부터 점진적 적용
  - 실제 워크로드 모니터링

- **고급 최적화 기법**
  - 읽기 복제(Read Replica) 구축
  - 검색 엔진(Elasticsearch) 통합
  - 데이터 아카이빙 전략

- **지속적 성능 개선**
  - 느린 쿼리 로그 정기 분석
  - 인덱스 사용률 모니터링
  - 쿼리 플랜 정기 리뷰

---
## 📚 **참고 자료**

### STEP08 최적화 전략
- [Use The Index, Luke](https://use-the-index-luke.com/) - 인덱스 설계 가이드
- [MySQL 공식 문서](https://dev.mysql.com/doc/) - 인덱스 선택도, 실행 계획 분석
- [Spring Data JPA 문서](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/) - Fetch Join, @Query 사용법
- [배치 처리 성능 최적화](https://www.baeldung.com/spring-data-jpa-batch) - 배치 UPDATE 성능

### 생성된 문서
- `STEP08_DB_OPTIMIZATION_REPORT.md` - 종합 최적화 보고서 (상세 분석)
- `STEP08_IMPLEMENTATION_SUMMARY.md` - 구현 완료 요약 (10분 읽기)
- `STEP08_QUICK_REFERENCE.md` - 빠른 참조 가이드 (5분 읽기)

---
## ✋ **체크리스트 (제출 전 확인)**

- [ ] 적절한 ORM을 사용하였는가? (JPA, TypeORM, Prisma, Sequelize 등)
- [ ] Repository 전환 간 서비스 로직의 변경은 없는가?
- [ ] docker-compose, testcontainers 등 로컬 환경에서 실행하고 테스트할 수 있는 환경을 구성했는가?