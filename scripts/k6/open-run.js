// 6단계: 오픈런 전체 흐름. 가상 손님 VUS명이 동시에 줄을 서고, 순번을 확인하다가 입장권을 받으면 구매한다.
// 실제 앱처럼: 503·429를 받으면 잠시 뒤 다시 시도하고, 순번은 2~3초 간격(지터)으로 묻는다. 주문은 멱등 키로 재시도해도 한 번만 생긴다.
// 9단계부터: 손님마다 먼저 로그인해서(개발용 /api/auth/dev-login) 토큰을 받고, 그 토큰으로 줄을 서고 주문한다.
// 실행: scripts/load/k6.sh open-run.js -e VUS=1000 -e STOCK=100
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const BASE = __ENV.BASE || `http://${__ENV.HOST || 'localhost'}:8000`;
// 상품 등록은 관리자 기능이라, 준비 단계에서는 게이트웨이를 거치지 않고 주문 서버에 직접 만든다
const ORDER_BASE = __ENV.ORDER_BASE || `http://${__ENV.HOST || 'localhost'}:8082`;
const VUS = Number(__ENV.VUS || 1000);
const bought = new Counter('bought');
const soldOut = new Counter('sold_out');
const notAdmitted = new Counter('not_admitted');
const gaveUp = new Counter('order_gave_up');
const retries = new Counter('client_retries');

export const options = {
  scenarios: {
    open_run: { executor: 'per-vu-iterations', vus: VUS, iterations: 1, maxDuration: '6m' },
  },
  summaryTrendStats: ['avg', 'p(50)', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  const res = http.post(`${ORDER_BASE}/api/products`, JSON.stringify({ name: 'k6 오픈런', price: 199000, stock: Number(__ENV.STOCK || 100) }),
    { headers: { 'Content-Type': 'application/json' } });
  return { productId: res.json('id') };
}

const busy = (r) => r.status === 503 || r.status === 429 || r.status === 0;

export default function (data) {
  const user = `k6-${__VU}-${Date.now()}`;
  let jwt = null;
  for (let i = 0; i < 10 && !jwt; i++) {
    const res = http.post(`${BASE}/api/auth/dev-login`, JSON.stringify({ userId: user }),
      { headers: { 'Content-Type': 'application/json' }, tags: { step: 'login' } });
    if (res.status === 200) jwt = res.json('accessToken'); else { retries.add(1); sleep(1); }
  }
  if (!jwt) { notAdmitted.add(1); return; }
  const headers = { 'Content-Type': 'application/json', Authorization: `Bearer ${jwt}` };

  // 1) 줄 서기: 서버가 바쁘다고 하면(503, 429) 잠시 뒤 다시 선다
  let status = null;
  for (let i = 0; i < 30 && !status; i++) {
    const res = http.post(`${BASE}/api/queue/${data.productId}/enter`, null, { headers, tags: { step: 'enter' } });
    if (res.status === 200) status = res.json();
    else if (busy(res)) { retries.add(1); sleep(1 + Math.random() * 2); }
    else break;
  }

  // 2) 순번 확인: 2~3초 간격으로 묻는다(모두가 같은 순간에 묻지 않게)
  for (let i = 0; i < 120 && status && status.state !== 'ADMITTED'; i++) {
    sleep(2 + Math.random());
    const res = http.get(`${BASE}/api/queue/${data.productId}/status`, { headers, tags: { step: 'status' } });
    if (res.status === 200) status = res.json(); else retries.add(1);
  }
  if (!status || status.state !== 'ADMITTED') { notAdmitted.add(1); return; }

  // 3) 구매: 바쁘면 다시 시도. 같은 멱등 키라 여러 번 보내도 주문은 한 번이다
  for (let i = 0; i < 10; i++) {
    const res = http.post(`${BASE}/api/orders`, JSON.stringify({ productId: data.productId, quantity: 1 }), {
      headers: { ...headers, 'X-Entry-Token': status.token, 'Idempotency-Key': `${user}-order` }, tags: { step: 'order' },
    });
    if (res.status === 201 || res.status === 200) { bought.add(1); return; }
    if (res.status === 409) { soldOut.add(1); return; }
    if (!busy(res)) { check(res, { '예상하지 못한 응답': () => false }); return; }
    retries.add(1); sleep(1 + Math.random());
  }
  gaveUp.add(1);
}
