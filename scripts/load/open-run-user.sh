#!/usr/bin/env bash
# 오픈런 손님 한 명: 줄 서기 → 입장할 때까지 순번 확인 → 입장권으로 구매. 결과 상태 코드 하나를 출력한다.
# open-run.sh가 사람 수만큼 동시에 실행한다.
set -uo pipefail
BASE=$1; PID=$2; USER_ID=$3
json() { python3 -c "import sys,json;d=json.load(sys.stdin);print(d.get('$1') or '')" 2>/dev/null; }

# 9단계부터: 개발용 로그인으로 토큰을 받는다. 게이트웨이는 X-User-Id를 믿지 않고 토큰 주인을 붙인다
JWT=$(curl -s -X POST "$BASE/api/auth/dev-login" -H 'Content-Type: application/json' -d "{\"userId\":\"$USER_ID\"}" | json accessToken)
[ -n "$JWT" ] || { echo "login-failed"; exit 0; }
AUTH="Authorization: Bearer $JWT"
STATUS=$(curl -s -X POST "$BASE/api/queue/$PID/enter" -H "$AUTH")
for _ in $(seq 1 600); do                       # 최대 약 3분 대기
  STATE=$(json state <<<"$STATUS")
  [ "$STATE" = "ADMITTED" ] && break
  sleep 0.3
  STATUS=$(curl -s "$BASE/api/queue/$PID/status" -H "$AUTH")
done
[ "$(json state <<<"$STATUS")" = "ADMITTED" ] || { echo "timeout"; exit 0; }
TOKEN=$(json token <<<"$STATUS")

curl -s -o /dev/null -w "%{http_code}\n" -X POST "$BASE/api/orders" \
  -H 'Content-Type: application/json' -H "$AUTH" -H "X-Entry-Token: $TOKEN" \
  -H "Idempotency-Key: $USER_ID-$PID" -d "{\"productId\":$PID,\"quantity\":1}"
