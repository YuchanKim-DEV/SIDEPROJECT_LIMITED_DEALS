#!/usr/bin/env bash
# 4단계 이벤트 신뢰성 실험: 주문 이벤트가 Kafka를 거쳐 알림 서비스까지 빠짐없이, 한 번씩만 가는지 본다.
# 서버는 scripts/run-local.sh start로 띄워 둔다. Kafka와 notification-service를 껐다 켜는 실험이 들어 있다.
# 사용법: scripts/load/event-delivery.sh
set -euo pipefail
ORDER=http://localhost:8082
NOTI=http://localhost:8083
DIR=$(cd "$(dirname "$0")/../.." && pwd)
DOCKER=$(command -v docker || echo "$HOME/.docker/bin/docker")

product() { curl -s -X POST "$ORDER/api/products" -H 'Content-Type: application/json' \
  -d "{\"name\":\"$1\",\"price\":1000,\"stock\":$2}" | python3 -c "import sys,json;print(json.load(sys.stdin)['id'])"; }
orders() { seq "$2" | xargs -P 20 -I{} curl -s -o /dev/null -w "%{http_code}\n" -X POST "$ORDER/api/orders" \
  -H 'Content-Type: application/json' -d "{\"productId\":$1,\"quantity\":1}" | sort | uniq -c | awk '{printf "%s×%s ", $2, $1}'; }
stats() { curl -s "$NOTI/api/notifications/stats?productId=$1"; }
pending() { curl -s "$ORDER/actuator/metrics/outbox.pending" | python3 -c "import sys,json;print(int(json.load(sys.stdin)['measurements'][0]['value']))"; }
wait_for() { # 상품 $1의 알림이 $2개가 될 때까지 최대 60초
  for _ in $(seq 1 120); do
    [ "$(stats "$1" | python3 -c "import sys,json;print(json.load(sys.stdin)['notifications'])")" -ge "$2" ] && return 0; sleep 0.5
  done; return 1; }
mysql() { "$DOCKER" exec limited-deals-mysql sh -c "mysql -uadmin -p\"\$MYSQL_PASSWORD\" limited_deals -N -e \"$1\" 2>/dev/null"; }

echo "== A. 정상: 주문 100건"
P=$(product "A 정상" 100); echo "주문: $(orders "$P" 100)"
START=$(date +%s); wait_for "$P" 100; echo "알림: $(stats "$P"), 도착까지 $(( $(date +%s) - START ))초 이내"
echo "평균 전달 지연: $(mysql "SELECT ROUND(AVG(delay_millis)) FROM notifications WHERE product_id=$P")ms, 최대 $(mysql "SELECT MAX(delay_millis) FROM notifications WHERE product_id=$P")ms"

echo; echo "== B. Kafka가 죽은 동안 주문 50건"
"$DOCKER" stop limited-deals-kafka > /dev/null; echo "Kafka 정지"
P=$(product "B Kafka 장애" 50); echo "주문: $(orders "$P" 50)  ← 주문은 받는다"
sleep 3; echo "아직 못 보낸 이벤트(outbox.pending): $(pending), 알림: $(stats "$P")"
"$DOCKER" start limited-deals-kafka > /dev/null; echo "Kafka 다시 시작"
START=$(date +%s); wait_for "$P" 50; echo "알림: $(stats "$P"), 복구 후 $(( $(date +%s) - START ))초, pending: $(pending)"

echo; echo "== C. 같은 이벤트 30건을 다시 보냄(릴레이가 보낸 직후 죽은 상황)"
P=$(product "C 중복" 30); orders "$P" 30 > /dev/null; wait_for "$P" 30
mysql "UPDATE outbox_events SET status='PENDING' WHERE message_key='$P'"; echo "보냄 표시를 되돌려 30건을 다시 발행"
sleep 5; echo "알림: $(stats "$P")  ← 30에서 늘지 않아야 한다"
echo "notification.duplicates: $(curl -s "$NOTI/actuator/metrics/notification.duplicates" | python3 -c "import sys,json;print(int(json.load(sys.stdin)['measurements'][0]['value']))")"

echo; echo "== D. 알림 서비스가 죽은 동안 주문 40건"
"$DIR/scripts/run-local.sh" kill notification-service > /dev/null; echo "notification-service 정지"
P=$(product "D 컨슈머 장애" 40); echo "주문: $(orders "$P" 40)"
sleep 2; "$DIR/scripts/run-local.sh" restart notification-service > /dev/null; echo "notification-service 다시 시작"
START=$(date +%s); wait_for "$P" 40; echo "알림: $(stats "$P"), 재시작 후 $(( $(date +%s) - START ))초"

echo; echo "== E. 처리할 수 없는 메시지(poison) → DLT"
P=$(product "E poison" 5)
FAILING_PRODUCT_IDS=$P "$DIR/scripts/run-local.sh" restart notification-service > /dev/null; echo "상품 $P 이벤트는 항상 실패하도록 다시 시작"
Q=$(product "E 정상 상품" 5)
echo "주문: poison $(orders "$P" 1), 정상 $(orders "$Q" 3)"
wait_for "$Q" 3; sleep 8
echo "정상 상품 알림: $(stats "$Q")  ← poison 때문에 막히지 않아야 한다"
echo "poison 상품 알림: $(stats "$P")"
echo -n "DLT(order-events-dlt)에 옮겨진 poison 메시지: "
"$DOCKER" exec limited-deals-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic order-events-dlt --from-beginning --timeout-ms 15000 2>/dev/null | grep -c "\"productId\":$P" || true
echo "알림 서비스 로그: $(grep -c 'DLT로 옮김' "$DIR/build/run-logs/notification-service.log")건 'DLT로 옮김'"
"$DIR/scripts/run-local.sh" restart notification-service > /dev/null; echo "notification-service 원래대로 다시 시작"
