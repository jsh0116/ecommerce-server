/**
 * K6 부하 테스트: 캐싱 적용 전 성능 테스트
 *
 * 테스트 목표:
 * - 캐싱 없이 데이터베이스에 직접 접근하는 상황에서의 성능 측정
 * - 응답 시간, 처리량, 에러율 기록
 * - 캐싱 적용 후 성능과 비교하기 위한 기준 데이터 수집
 *
 * 실행 방법:
 * k6 run k6/load-test-before-cache.js
 *
 * 테스트 시나리오:
 * - 50 VU (Virtual Users)
 * - 5분 지속 시간
 * - Ramp-up: 30초 (0 -> 50 VU)
 * - Steady state: 4분 (50 VU)
 * - Ramp-down: 30초 (50 -> 0 VU)
 */

import http from 'k6/http';
import { check, group } from 'k6';
import { Rate, Trend, Counter, Gauge } from 'k6/metrics';

// 커스텀 메트릭
const errorRate = new Rate('errors');
const productListLatency = new Trend('product_list_latency');
const productDetailLatency = new Trend('product_detail_latency');
const inventoryLatency = new Trend('inventory_latency');
const concurrentUsers = new Gauge('concurrent_users');

// 테스트 설정
export const options = {
  stages: [
    { duration: '30s', target: 50 },    // Ramp-up: 0 -> 50 VU
    { duration: '4m', target: 50 },     // Steady state: 50 VU for 4 minutes
    { duration: '30s', target: 0 },     // Ramp-down: 50 -> 0 VU
  ],
  thresholds: {
    http_req_duration: ['p(95)<2000', 'p(99)<3000'],  // 95% 응답시간 < 2초, 99% < 3초
    http_req_failed: ['rate<0.1'],                     // 에러율 < 10%
    'product_list_latency': ['p(95)<2000'],
    'product_detail_latency': ['p(95)<1500'],
    'inventory_latency': ['p(95)<800'],
  },
  ext: {
    loadimpact: {
      projectID: 3505134,
      name: '[BEFORE_CACHE] Load Test',
    },
  },
};

const BASE_URL = 'http://localhost:8080';

export default function () {
  concurrentUsers.add(__VU);

  // 테스트 시나리오 1: 상품 목록 조회 (높은 읽기 빈도)
  group('Product List Queries', function () {
    for (let page = 1; page <= 3; page++) {
      const listRes = http.get(`${BASE_URL}/api/v1/products?page=${page}&limit=20`);

      const success = check(listRes, {
        'status is 200': (r) => r.status === 200,
        'response time < 2s': (r) => r.timings.duration < 2000,
        'has pagination info': (r) => r.json('pagination') !== undefined,
      });

      errorRate.add(!success);
      productListLatency.add(listRes.timings.duration);
    }
  });

  // 테스트 시나리오 2: 상품 상세 조회 (inventory 포함)
  group('Product Detail Queries', function () {
    // 1~10번 상품 조회
    for (let productId = 1; productId <= 5; productId++) {
      const detailRes = http.get(`${BASE_URL}/api/v1/products/${productId}`);

      const success = check(detailRes, {
        'status is 200': (r) => r.status === 200,
        'response time < 1.5s': (r) => r.timings.duration < 1500,
        'has variants': (r) => r.json('variants') !== undefined,
      });

      errorRate.add(!success);
      productDetailLatency.add(detailRes.timings.duration);
    }
  });

  // 테스트 시나리오 3: 상품 검색 (읽기 작업)
  group('Product Search', function () {
    const searchRes = http.get(`${BASE_URL}/api/v1/products/search?q=pants&page=1&limit=20`);

    const success = check(searchRes, {
      'status is 200': (r) => r.status === 200,
      'response time < 2s': (r) => r.timings.duration < 2000,
    });

    errorRate.add(!success);
    productListLatency.add(searchRes.timings.duration);
  });

  // 테스트 시나리오 4: 재고 조회 (매우 높은 읽기 빈도)
  group('Inventory Queries', function () {
    const skus = [
      'SKU-1-001', 'SKU-2-001', 'SKU-3-001',
      'SKU-4-001', 'SKU-5-001', 'SKU-6-001',
    ];

    for (const sku of skus) {
      const invRes = http.get(`${BASE_URL}/api/v1/inventory/skus/${sku}`);

      const success = check(invRes, {
        'status is 200 or 404': (r) => r.status === 200 || r.status === 404,
        'response time < 800ms': (r) => r.timings.duration < 800,
      });

      errorRate.add(!success);
      inventoryLatency.add(invRes.timings.duration);
    }
  });

  // 테스트 시나리오 5: 상품 변량 조회
  group('Product Variants', function () {
    for (let productId = 1; productId <= 3; productId++) {
      const variantRes = http.get(`${BASE_URL}/api/v1/products/${productId}/variants?inStock=true`);

      const success = check(variantRes, {
        'status is 200': (r) => r.status === 200,
        'response time < 1.5s': (r) => r.timings.duration < 1500,
      });

      errorRate.add(!success);
      productDetailLatency.add(variantRes.timings.duration);
    }
  });
}
