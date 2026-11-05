#!/usr/bin/env bash
# 2단계 멱등성 실험: 같은 Idempotency-Key로 같은 주문을 N번 동시에 보낸다(결제 버튼 연타, 네트워크 재전송 흉내).
# 사용법: scripts/load/idempotency.sh [주소] [요청 수] [동시 요청 수] [키 사용 여부: with-key, no-key]
# 예:     scripts/load/idempotency.sh http://localhost:8082 50 50 with-key
set -euo pipefail

BASE=${1:-http://localhost:8082}
REQUESTS=${2:-50}
PARALLEL=${3:-50}
MODE=${4:-with-key}

ID=$(curl -s -X POST "$BASE/api/products" -H 'Content-Type: application/json' \
  -d '{"name":"멱등성 실험","price":10000,"stock":100}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['id'])")
KEY="pay-$(date +%s)-$RANDOM"
HEADER=()
[ "$MODE" = "with-key" ] && HEADER=(-H "Idempotency-Key: $KEY")
echo "상품 id=$ID (재고 100), 같은 주문 ${REQUESTS}번, 동시 ${PARALLEL}, 모드=${MODE} ${HEADER:+키=${KEY}}"

CODES=$(seq "$REQUESTS" | xargs -P "$PARALLEL" -I{} curl -s -o /dev/null -w "%{http_code}\n" \
  -X POST "$BASE/api/orders" -H 'Content-Type: application/json' ${HEADER[@]+"${HEADER[@]}"} \
  -d "{\"productId\":$ID,\"quantity\":1}")
LEFT=$(curl -s "$BASE/api/products/$ID" | python3 -c "import sys,json;print(json.load(sys.stdin)['stock'])")

echo "응답:          $(sort <<<"$CODES" | uniq -c | awk '{printf "%s×%s ", $2, $1}')"
echo "  201 = 새 주문, 200 = 처음 결과를 다시 돌려줌(replay), 409 = 첫 요청이 아직 처리 중"
echo "실제로 팔린 수: $(( 100 - LEFT ))개 (기대: with-key는 1개, no-key는 ${REQUESTS}개)"
