#!/usr/bin/env bash
# 8단계 결제·정산 실험: 주문 → 결제 → 주문 확정/취소(Saga), PG 거절, PG 응답 유실 + 대사, 결제 서버 장애.
# 이 스크립트가 서버 5개를 직접 띄우고(입장권 없이, 장애 스위치 켬) 끝나면 끈다.
# 사용법: scripts/load/payment-saga.sh [상품당 주문 수=100]
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
N=${1:-100}
ORDER=http://localhost:8082
PAY=http://localhost:8084
RUN="$ROOT/scripts/run-local.sh"
DOCKER=$(command -v docker || echo "$HOME/.docker/bin/docker")

sql() { "$DOCKER" exec limited-deals-mysql sh -c "mysql -uadmin -p\"\$MYSQL_PASSWORD\" -N limited_deals -e \"$1\"" 2>/dev/null; }
product() { curl -s -X POST "$ORDER/api/products" -H 'Content-Type: application/json' \
  -d "{\"name\":\"결제 실험 $1\",\"price\":10000,\"stock\":$N}" | python3 -c "import sys,json;print(json.load(sys.stdin)['id'])"; }
orders() {  # 상품 $1에 주문 N건을 20개씩 동시에
  seq 1 "$N" | xargs -P 20 -I{} curl -s -o /dev/null -X POST "$ORDER/api/orders" -H 'Content-Type: application/json' \
    -H "X-User-Id: pay-{}" -H "Idempotency-Key: pay-$1-{}" -d "{\"productId\":$1,\"quantity\":1}"
}
pg() { curl -s -o /dev/null -X POST "$PAY/api/payments/admin/pg?declineRate=$1&timeoutRate=$2"; }
summary() {  # 상품 $1의 주문 상태별 수, 남은 재고
  echo "  주문 상태: $(sql "select concat(coalesce(status,'NULL'),'=',count(*)) from orders where product_id=$1 group by status" | tr '\n' ' ')"
  echo "  남은 재고: $(sql "select stock from product where id=$1")"
}
wait_settled() {  # 상품 $1의 결제 대기 주문이 0이 될 때까지(최대 $2초)
  for _ in $(seq 1 "$2"); do
    [ "$(sql "select count(*) from orders where product_id=$1 and status='PENDING_PAYMENT'")" = "0" ] && return 0; sleep 1
  done; return 1
}

"$RUN" stop > /dev/null 2>&1 || true
CHAOS_ENABLED=true "$RUN" start --spring.jpa.show-sql=false > /dev/null
echo "서버 5개 시작 (입장권 없이, 장애 스위치 켬), 상품당 주문 ${N}건"
sleep 5   # 결제 서버 컨슈머가 파티션을 받을 때까지

echo; echo "== A. 정상"
A=$(product A); orders "$A"; wait_settled "$A" 60 && echo "  모두 결제 완료" || echo "  아직 결제 대기 남음"
summary "$A"

echo; echo "== B. PG가 30% 거절"
pg 0.3 0; B=$(product B); orders "$B"; wait_settled "$B" 60 || true
summary "$B"
echo "  확인: 취소 수 = 남은 재고 (취소된 주문의 재고가 정확히 돌아왔는지)"

echo; echo "== C. PG는 승인했지만 30%는 응답이 사라짐"
pg 0 0.3; C=$(product C); orders "$C"; sleep 3
echo "  결제 직후: $(curl -s "$PAY/api/payments/stats")"
summary "$C"
echo "  31초 기다린 뒤 대사 실행 (30초 넘게 끝나지 않은 결제를 PG에 조회)"
sleep 31; echo "  대사 결과: $(curl -s -X POST "$PAY/api/payments/reconciliation")"
wait_settled "$C" 30 || true
summary "$C"

echo; echo "== D. 주문 도중 결제 서버를 끄고, 다시 켬"
pg 0 0; D=$(product D)
orders "$D" & ORDERS_PID=$!
sleep 1; "$RUN" kill payment-service > /dev/null; echo "  결제 서버 종료"
wait "$ORDERS_PID"
summary "$D"
"$RUN" restart payment-service > /dev/null; echo "  결제 서버 다시 시작 (마지막 커밋 위치부터 다시 읽음)"
wait_settled "$D" 60 || { echo "  남은 결제는 대사로 정리: $(sleep 31; curl -s -X POST "$PAY/api/payments/reconciliation")"; wait_settled "$D" 30 || true; }
summary "$D"

echo; echo "== 전체 대사와 정산"
echo "  대사: $(curl -s -X POST "$PAY/api/payments/reconciliation")"
echo "  정산(오늘): $(curl -s "$PAY/api/payments/settlement")"
echo "  결제 상태: $(curl -s "$PAY/api/payments/stats")"

"$RUN" stop > /dev/null 2>&1 || true
echo; echo "서버 종료"
