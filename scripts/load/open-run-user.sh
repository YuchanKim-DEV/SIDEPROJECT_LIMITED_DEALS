#!/usr/bin/env bash
# 오픈런 손님 한 명: 줄 서기 → 입장할 때까지 순번 확인 → 입장권으로 구매. 결과 상태 코드 하나를 출력한다.
# open-run.sh가 사람 수만큼 동시에 실행한다.
set -uo pipefail
BASE=$1; PID=$2; USER_ID=$3
json() { python3 -c "import sys,json;d=json.load(sys.stdin);print(d.get('$1') or '')" 2>/dev/null; }

STATUS=$(curl -s -X POST "$BASE/api/queue/$PID/enter" -H "X-User-Id: $USER_ID")
for _ in $(seq 1 600); do                       # 최대 약 3분 대기
  STATE=$(json state <<<"$STATUS")
  [ "$STATE" = "ADMITTED" ] && break
  sleep 0.3
  STATUS=$(curl -s "$BASE/api/queue/$PID/status" -H "X-User-Id: $USER_ID")
done
[ "$(json state <<<"$STATUS")" = "ADMITTED" ] || { echo "timeout"; exit 0; }
TOKEN=$(json token <<<"$STATUS")

curl -s -o /dev/null -w "%{http_code}\n" -X POST "$BASE/api/orders" \
  -H 'Content-Type: application/json' -H "X-User-Id: $USER_ID" -H "X-Entry-Token: $TOKEN" \
  -H "Idempotency-Key: $USER_ID-$PID" -d "{\"productId\":$PID,\"quantity\":1}"
