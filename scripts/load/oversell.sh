#!/usr/bin/env bash
# 1단계 재고 동시성 실험: 재고 N개 상품에 M명이 동시에 1개씩 주문한다.
# 사용법: scripts/load/oversell.sh [주소] [재고] [요청 수] [동시 요청 수] [방식: naive, atomic]
# 예:     scripts/load/oversell.sh http://localhost:8082 100 1000 200 atomic
set -euo pipefail

BASE=${1:-http://localhost:8082}
STOCK=${2:-100}
REQUESTS=${3:-1000}
PARALLEL=${4:-200}
STRATEGY=${5:-atomic}

ID=$(curl -s -X POST "$BASE/api/products" -H 'Content-Type: application/json' \
  -d "{\"name\":\"동시성 실험\",\"price\":10000,\"stock\":$STOCK}" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['id'])")
echo "상품 id=$ID, 재고=$STOCK, 요청=$REQUESTS, 동시=$PARALLEL, 방식=$STRATEGY"

START=$(date +%s)
CODES=$(seq "$REQUESTS" | xargs -P "$PARALLEL" -I{} curl -s -o /dev/null -w "%{http_code}\n" \
  -X POST "$BASE/api/orders?strategy=$STRATEGY" -H 'Content-Type: application/json' \
  -d "{\"productId\":$ID,\"quantity\":1}")
ELAPSED=$(( $(date +%s) - START ))

SUCCESS=$(grep -c '^201$' <<<"$CODES" || true)
REJECTED=$(grep -c '^409$' <<<"$CODES" || true)
LEFT=$(curl -s "$BASE/api/products/$ID" | python3 -c "import sys,json;print(json.load(sys.stdin)['stock'])")

echo "주문 성공(201): $SUCCESS"
echo "품절 거절(409): $REJECTED"
echo "기타 응답:      $(( REQUESTS - SUCCESS - REJECTED ))"
echo "남은 재고:      $LEFT"
echo "초과 판매:      $(( SUCCESS - (STOCK - LEFT) > 0 ? SUCCESS - (STOCK - LEFT) : 0 ))건 (성공 주문 - 실제로 줄어든 재고)"
echo "걸린 시간:      ${ELAPSED}초"
