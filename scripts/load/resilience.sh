#!/usr/bin/env bash
# 5단계 장애 대응 실험: 대기열 서버가 느리거나 죽었을 때 주문 서버와 게이트웨이가 어떻게 버티는지 본다.
# 이 스크립트가 서버 4개를 직접 띄우고(입장권 필수, 장애 스위치 켬) 끝나면 끈다.
# 사용법: scripts/load/resilience.sh
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
GW=http://localhost:8000
ORDER=http://localhost:8082
QUEUE=http://localhost:8081
RUN="$ROOT/scripts/run-local.sh"

"$RUN" stop > /dev/null 2>&1 || true
CHAOS_ENABLED=true ENTRY_TOKEN_REQUIRED=true "$RUN" start --spring.jpa.show-sql=false > /dev/null
echo "서버 4개 시작 (입장권 필수, 장애 스위치 켬)"

PID=$(curl -s -X POST "$ORDER/api/products" -H 'Content-Type: application/json' \
  -d '{"name":"장애 실험","price":1000,"stock":1000}' | python3 -c "import sys,json;print(json.load(sys.stdin)['id'])")
USER_ID="resilience-$RANDOM"
curl -s -X POST "$QUEUE/api/queue/$PID/enter" -H "X-User-Id: $USER_ID" > /dev/null
for _ in $(seq 1 20); do
  TOKEN=$(curl -s "$QUEUE/api/queue/$PID/status" -H "X-User-Id: $USER_ID" | python3 -c "import sys,json;print(json.load(sys.stdin).get('token') or '')")
  [ -n "$TOKEN" ] && break; sleep 0.5
done
echo "상품 $PID, 사용자 $USER_ID 입장권 받음"

order() { curl -s -o /dev/null -w "%{http_code} %{time_total}" -X POST "$ORDER/api/orders" -H 'Content-Type: application/json' \
  -H "X-User-Id: $USER_ID" -H "X-Entry-Token: $TOKEN" -H "Idempotency-Key: $RANDOM-$RANDOM" -d "{\"productId\":$PID,\"quantity\":1}"; }
state() { curl -s "$ORDER/actuator/metrics/resilience4j.circuitbreaker.state?tag=name:queue-service&tag=state:$1" \
  | python3 -c "import sys,json;print(int(json.load(sys.stdin)['measurements'][0]['value']))" 2>/dev/null || echo "?"; }

echo; echo "== 1. 정상"
for i in 1 2 3; do echo "  주문 $i: $(order)"; done

echo; echo "== 2. 대기열 서버를 2초씩 느리게 만들고 주문 15건"
curl -s -X POST "$QUEUE/api/queue/admin/chaos?validateDelayMs=2000" > /dev/null
for i in $(seq 1 15); do echo "  주문 $i: $(order)   회로 열림=$(state open)"; done

echo; echo "== 3. 대기열 서버를 정상으로 되돌리고 6초 기다림 (열림 5초 → 시험 호출)"
curl -s -X POST "$QUEUE/api/queue/admin/chaos?validateDelayMs=0" > /dev/null
sleep 6
for i in $(seq 1 5); do echo "  주문 $i: $(order)   회로 닫힘=$(state closed)"; done

echo; echo "== 4. 대기열 서버를 끄고 게이트웨이로 대기열 조회 12건"
GW_JWT=$(curl -s -X POST "$GW/api/auth/dev-login" -H 'Content-Type: application/json' -d "{\"userId\":\"$USER_ID\"}" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['accessToken'])")   # 9단계부터 게이트웨이는 토큰이 필요하다
"$RUN" kill queue-service > /dev/null
for i in $(seq 1 12); do
  echo "  조회 $i: $(curl -s -o /dev/null -w "%{http_code} %{time_total}" "$GW/api/queue/$PID/status" -H "Authorization: Bearer $GW_JWT")"
done
echo -n "  응답 본문: "; curl -s "$GW/api/queue/$PID/status" -H "Authorization: Bearer $GW_JWT"; echo

echo; echo "== 5. 주문 서버를 끄고 게이트웨이로 상품 조회"
"$RUN" kill order-service > /dev/null
echo "  $(curl -s -o /dev/null -w "%{http_code} %{time_total}" "$GW/api/products/$PID")"

"$RUN" stop > /dev/null 2>&1 || true
echo; echo "서버 종료"
