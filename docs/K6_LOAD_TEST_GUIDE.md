# K6 부하 테스트 가이드

## 1. K6 설치

### macOS (Homebrew)
```bash
brew install k6
```

### Windows (Chocolatey)
```bash
choco install k6
```

### Linux
```bash
# Debian/Ubuntu
sudo gpg -k
sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update
sudo apt-get install k6
```

### Docker
```bash
docker pull grafana/k6:latest
```

---

## 2. 테스트 실행 전 준비

### 애플리케이션 실행
```bash
# 터미널 1: 애플리케이션 실행
./gradlew bootRun

# 또는 Docker로 실행
docker-compose up
```

### 테스트 데이터 준비
부하 테스트를 위해 데이터베이스에 초기 데이터가 필요합니다:
- 상품 데이터 (최소 10개)
- 쿠폰 데이터 (ID=1인 쿠폰)
- 사용자 데이터
- 재고 데이터

---

## 3. 테스트 스크립트 실행

### 간단한 테스트 (30초, 10명 사용자)
```bash
k6 run k6-simple-test.js
```

### 전체 시나리오 테스트 (5분, 다단계)
```bash
k6 run k6-load-test.js
```

### 환경변수 설정
```bash
# 다른 서버 테스트
k6 run --env BASE_URL=http://localhost:8080 k6-load-test.js

# 결과를 JSON으로 저장
k6 run --out json=test-results.json k6-load-test.js

# 결과를 InfluxDB로 전송 (모니터링)
k6 run --out influxdb=http://localhost:8086/k6 k6-load-test.js
```

---

## 4. 테스트 시나리오

### k6-simple-test.js (간단 버전)
- **VUs**: 10명의 가상 사용자
- **Duration**: 30초
- **시나리오**:
  1. 상품 목록 조회
  2. 상품 상세 조회
  3. 쿠폰 발급 (동시성 테스트)

### k6-load-test.js (전체 버전)
총 5분 소요, 3개 시나리오 순차 실행:

#### Scenario 1: 상품 조회 (0~2분)
- 읽기 성능 테스트
- 0 → 50 → 50 → 0 VUs
- 상품 목록/상세 조회

#### Scenario 2: 쿠폰 발급 (2~3분)
- Redis 분산 락 동시성 테스트
- 0 → 100 → 100 → 0 VUs
- 쿠폰 중복 발급 방지 확인

#### Scenario 3: 주문 및 결제 (3~5분)
- 트랜잭션 부하 테스트
- 0 → 30 → 30 → 0 VUs
- 주문 생성 → 결제 처리

---

## 5. 성능 임계값 (Thresholds)

```javascript
thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<1000'], // 95%는 500ms, 99%는 1초 이하
    http_req_failed: ['rate<0.05'],                  // 에러율 5% 이하
    errors: ['rate<0.1'],                             // 비즈니스 에러율 10% 이하
}
```

---

## 6. 결과 해석

### 주요 메트릭

```
http_req_duration..............: avg=245ms  min=50ms   med=200ms  max=1.2s   p(95)=450ms p(99)=800ms
http_req_failed................: 2.34%     // 실패율
http_reqs......................: 15234     // 총 요청 수
iteration_duration.............: avg=3.2s   // 반복 주기
vus............................: 50        // 현재 가상 사용자 수
vus_max........................: 100       // 최대 가상 사용자 수
```

### 체크 항목
- ✅ `p(95) < 500ms`: 95%의 요청이 500ms 이하
- ✅ `http_req_failed < 5%`: 에러율 5% 이하
- ❌ `p(99) > 1000ms`: 99% 요청이 1초 초과 → 최적화 필요

---

## 7. Docker로 실행

```bash
# 간단한 테스트
docker run --rm -i grafana/k6 run - <k6-simple-test.js

# 네트워크 연결하여 실행 (localhost 접근)
docker run --rm -i --network=host grafana/k6 run - <k6-load-test.js

# 결과 파일 저장
docker run --rm -i -v $(pwd):/workspace grafana/k6 run --out json=/workspace/results.json - <k6-load-test.js
```

---

## 8. 모니터링 및 시각화

### Grafana + InfluxDB 연동
```bash
# docker-compose.yml에 추가
services:
  influxdb:
    image: influxdb:1.8
    ports:
      - "8086:8086"
    environment:
      - INFLUXDB_DB=k6

  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    depends_on:
      - influxdb
```

```bash
# 테스트 실행 (InfluxDB로 결과 전송)
k6 run --out influxdb=http://localhost:8086/k6 k6-load-test.js

# Grafana 접속: http://localhost:3000
# InfluxDB 데이터소스 추가 후 대시보드 구성
```

---

## 9. 성능 개선 체크리스트

부하 테스트 결과를 바탕으로 확인할 사항:

### 응답 시간 개선
- [ ] 데이터베이스 인덱스 확인
- [ ] N+1 쿼리 최적화
- [ ] 캐시 적용 (Redis)
- [ ] 커넥션 풀 튜닝 (HikariCP)

### 동시성 처리
- [ ] Redis 분산 락 성능
- [ ] 재고 예약 동시성
- [ ] 쿠폰 발급 동시성

### 에러율 개선
- [ ] 트랜잭션 타임아웃 설정
- [ ] 재시도 로직
- [ ] Circuit Breaker 패턴

---

## 10. 실전 팁

### 점진적 부하 증가
```javascript
stages: [
    { duration: '30s', target: 10 },   // Warm-up
    { duration: '1m', target: 50 },    // Normal load
    { duration: '30s', target: 100 },  // Peak load
    { duration: '1m', target: 100 },   // Sustained peak
    { duration: '30s', target: 0 },    // Cool-down
]
```

### 스파이크 테스트
```javascript
stages: [
    { duration: '10s', target: 200 },  // 급격한 부하
    { duration: '30s', target: 200 },  // 유지
    { duration: '10s', target: 0 },    // 종료
]
```

### 스트레스 테스트
```javascript
stages: [
    { duration: '2m', target: 100 },
    { duration: '5m', target: 100 },
    { duration: '2m', target: 200 },
    { duration: '5m', target: 200 },
    { duration: '2m', target: 300 },  // 한계 찾기
]
```

---

## 참고 자료
- [K6 공식 문서](https://k6.io/docs/)
- [K6 예제 스크립트](https://k6.io/docs/examples/)
- [K6 Best Practices](https://k6.io/docs/testing-guides/test-types/)
