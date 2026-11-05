#!/usr/bin/env bash
# 3단계 오픈런 전체 흐름 실험: 손님 N명이 동시에 줄을 서고, 입장권을 받은 순서대로 구매한다.
# 주문 서버는 ENTRY_TOKEN_REQUIRED=true로 띄워야 한다(입장권 없는 주문 거절).
# 사용법: scripts/load/open-run.sh [게이트웨이 주소] [재고] [손님 수] [동시 실행 수]
# 예:     scripts/load/open-run.sh http://localhost:8000 50 300 300
set -euo pipefail
BASE=${1:-http://localhost:8000}
STOCK=${2:-50}
USERS=${3:-300}
PARALLEL=${4:-300}
DIR=$(cd "$(dirname "$0")" && pwd)

PID=$(curl -s -X POST "$BASE/api/products" -H 'Content-Type: application/json' \
  -d "{\"name\":\"오픈런 실험\",\"price\":199000,\"stock\":${STOCK}}" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['id'])")
echo "상품 id=${PID}, 재고=${STOCK}, 손님=${USERS}명, 동시=${PARALLEL}"

echo -n "입장권 없이 바로 주문: "
curl -s -o /dev/null -w "%{http_code}\n" -X POST "$BASE/api/orders" -H 'Content-Type: application/json' \
  -H "X-User-Id: cheater" -d "{\"productId\":${PID},\"quantity\":1}"

START=$(date +%s)
CODES=$(seq "$USERS" | xargs -P "$PARALLEL" -I{} "$DIR/open-run-user.sh" "$BASE" "$PID" "u{}-$RANDOM")
ELAPSED=$(( $(date +%s) - START ))
LEFT=$(curl -s "$BASE/api/products/$PID" | python3 -c "import sys,json;print(json.load(sys.stdin)['stock'])")
STATS=$(curl -s "$BASE/api/queue/$PID/stats")

echo "결과:          $(sort <<<"$CODES" | uniq -c | awk '{printf "%s×%s ", $2, $1}')"
echo "  201 = 구매 성공, 409 = 품절, 403 = 입장권 거절, 429 = 너무 많은 요청, timeout = 입장 못 함"
echo "남은 재고:     ${LEFT} (팔린 수 $(( STOCK - LEFT )))"
echo "대기열:        ${STATS}"
echo "걸린 시간:     ${ELAPSED}초 (입장 속도: 초당 admit-per-second명)"
