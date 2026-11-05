// 6단계: 재고 차감 방식별 응답 시간 비교.
// 한 상품(같은 행)에 초당 RATE건의 주문을 DURATION 동안 보낸다. 재고는 충분히 많아 품절되지 않는다(락 경합만 본다).
// PRODUCTS를 주면 상품 여러 개(서로 다른 행)에 고르게 나눠 보낸다. 한 행에 몰릴 때와 비교하는 용도.
// 실행: scripts/load/k6.sh order-strategies.js -e STRATEGY=atomic -e RATE=300 -e DURATION=20s [-e PRODUCTS=10]
import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE || `http://${__ENV.HOST || 'localhost'}:8082`;
const STRATEGY = __ENV.STRATEGY || 'atomic';
const PRODUCTS = Number(__ENV.PRODUCTS || 1);

export const options = {
  scenarios: {
    orders: {
      executor: 'constant-arrival-rate',   // 응답이 느려져도 정해진 속도로 계속 보낸다(실제 손님처럼)
      rate: Number(__ENV.RATE || 300),
      timeUnit: '1s',
      duration: __ENV.DURATION || '20s',
      preAllocatedVUs: 200,
      maxVUs: Number(__ENV.MAX_VUS || 2000),
    },
  },
  summaryTrendStats: ['avg', 'p(50)', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  const productIds = [];
  for (let i = 0; i < PRODUCTS; i++) {
    const res = http.post(`${BASE}/api/products`, JSON.stringify({ name: `k6-${STRATEGY}-${i}`, price: 1000, stock: 10000000 }),
      { headers: { 'Content-Type': 'application/json' } });
    productIds.push(res.json('id'));
  }
  return { productIds };
}

export default function (data) {
  const productId = data.productIds[Math.floor(Math.random() * data.productIds.length)];
  const res = http.post(`${BASE}/api/orders?strategy=${STRATEGY}`, JSON.stringify({ productId, quantity: 1 }),
    { headers: { 'Content-Type': 'application/json' }, tags: { strategy: STRATEGY } });
  check(res, { '201 주문 성공': (r) => r.status === 201 });
}
