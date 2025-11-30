/**
 * K6 부하 테스트: 캐싱 적용 후 성능 테스트
 *
 * 테스트 목표:
 * - 캐싱 적용 후 응답 시간과 처리량 측정
 * - 캐싱 적용 전과 비교하여 성능 개선도 검증
 * - Redis 캐시 히트율 확인
 *
 * 실행 방법:
 * k6 run k6/load-test-after-cache.js
 *
 * 테스트 시나리오:
 * - 동일한 부하 조건 (50 VU, 5분)
 * - 캐시 워밍 페이즈: 첫 요청으로 캐시 채우기
 * - 기본 페이즈: 캐시된 데이터 접근
 */

import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Rate, Trend, Counter, Gauge } from 'k6/metrics';

// 커스텀 메트릭
const errorRate = new Rate('errors');
const productListLatency = new Trend('product_list_latency');
const productDetailLatency = new Trend('product_detail_latency');
const inventoryLatency = new Trend('inventory_latency');
const cacheHitCount = new Counter('cache_hits');
const cacheMissCount = new Counter('cache_misses');
const concurrentUsers = new Gauge('concurrent_users');

// 테스트 설정
export const options = {
  stages: [
    { duration: '30s', target: 50 },    // Ramp-up: 0 -> 50 VU
    { duration: '4m', target: 50 },     // Steady state: 50 VU for 4 minutes
    { duration: '30s', target: 0 },     // Ramp-down: 50 -> 0 VU
  ],
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<1000'],   // 캐싱으로 더 빠른 응답
    http_req_failed: ['rate<0.05'],                    // 에러율 < 5%
    'product_list_latency': ['p(95)<500'],
    'product_detail_latency': ['p(95)<400'],
    'inventory_latency': ['p(95)<200'],
  },
  ext: {
    loadimpact: {
      projectID: 3505134,
      name: '[AFTER_CACHE] Load Test',
    },
  },
};

const BASE_URL = 'http://localhost:8080';

export default function () {
  concurrentUsers.add(__VU);

  // 테스트 시나리오 1: 상품 목록 조회 (캐시 적용)
  group('Product List Queries (Cached)', function () {
    for (let page = 1; page <= 3; page++) {
      const listRes = http.get(`${BASE_URL}/api/v1/products?page=${page}&limit=20`);

      const success = check(listRes, {
        'status is 200': (r) => r.status === 200,
        'response time < 500ms': (r) => r.timings.duration < 500,
        'has pagination info': (r) => r.json('pagination') !== undefined,
      });

      errorRate.add(!success);
      productListLatency.add(listRes.timings.duration);

      // 응답 시간이 매우 빠르면 캐시 히트로 간주
      if (listRes.timings.duration < 100) {
        cacheHitCount.add(1);
      } else {
        cacheMissCount.add(1);
      }
    }
  });

  // 테스트 시나리오 2: 상품 상세 조회 (캐시 적용)
  group('Product Detail Queries (Cached)', function () {
    for (let productId = 1; productId <= 5; productId++) {
      const detailRes = http.get(`${BASE_URL}/api/v1/products/${productId}`);

      const success = check(detailRes, {
        'status is 200': (r) => r.status === 200,
        'response time < 400ms': (r) => r.timings.duration < 400,
        'has variants': (r) => r.json('variants') !== undefined,
      });

      errorRate.add(!success);
      productDetailLatency.add(detailRes.timings.duration);

      if (detailRes.timings.duration < 100) {
        cacheHitCount.add(1);
      } else {
        cacheMissCount.add(1);
      }
    }
  });

  // 테스트 시나리오 3: 상품 검색 (캐시 가능)
  group('Product Search (Cached)', function () {
    const searchRes = http.get(`${BASE_URL}/api/v1/products/search?q=pants&page=1&limit=20`);

    const success = check(searchRes, {
      'status is 200': (r) => r.status === 200,
      'response time < 500ms': (r) => r.timings.duration < 500,
    });

    errorRate.add(!success);
    productListLatency.add(searchRes.timings.duration);

    if (searchRes.timings.duration < 100) {
      cacheHitCount.add(1);
    } else {
      cacheMissCount.add(1);
    }
  });

  // 테스트 시나리오 4: 재고 조회 (매우 높은 읽기 빈도 - 캐싱 효과 가장 큼)
  group('Inventory Queries (Cached)', function () {
    const skus = [
      'SKU-1-001', 'SKU-2-001', 'SKU-3-001',
      'SKU-4-001', 'SKU-5-001', 'SKU-6-001',
    ];

    for (const sku of skus) {
      const invRes = http.get(`${BASE_URL}/api/v1/inventory/skus/${sku}`);

      const success = check(invRes, {
        'status is 200 or 404': (r) => r.status === 200 || r.status === 404,
        'response time < 200ms': (r) => r.timings.duration < 200,
      });

      errorRate.add(!success);
      inventoryLatency.add(invRes.timings.duration);

      // 재고 조회는 캐시 히트가 매우 빨라야 함
      if (invRes.timings.duration < 50) {
        cacheHitCount.add(1);
      } else {
        cacheMissCount.add(1);
      }
    }
  });

  // 테스트 시나리오 5: 상품 변량 조회 (캐시 적용)
  group('Product Variants (Cached)', function () {
    for (let productId = 1; productId <= 3; productId++) {
      const variantRes = http.get(`${BASE_URL}/api/v1/products/${productId}/variants?inStock=true`);

      const success = check(variantRes, {
        'status is 200': (r) => r.status === 200,
        'response time < 400ms': (r) => r.timings.duration < 400,
      });

      errorRate.add(!success);
      productDetailLatency.add(variantRes.timings.duration);

      if (variantRes.timings.duration < 100) {
        cacheHitCount.add(1);
      } else {
        cacheMissCount.add(1);
      }
    }
  });
}