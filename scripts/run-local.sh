#!/usr/bin/env bash
# 로컬에서 서버 5개(gateway, queue, order, notification, payment)를 jar로 띄우거나 끈다. 로그는 build/run-logs/에 남는다.
# 사용법: scripts/run-local.sh start [order-service에 줄 추가 옵션...]
#         scripts/run-local.sh stop
# 예:     ENTRY_TOKEN_REQUIRED=true scripts/run-local.sh start --spring.jpa.show-sql=false
# MySQL 비밀번호는 .env 대신 실행 중인 MySQL 컨테이너의 환경변수에서 읽는다(화면에 출력하지 않는다).
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
LOGS="$ROOT/build/run-logs"
JAVA=${JAVA_HOME:-/opt/homebrew/opt/openjdk@25}/bin/java
# 힙 상한. 기본값(메모리의 1/4, 8GB 맥에서 2GB)으로 5개를 띄우면 Docker VM(4GB)과 합쳐 메모리가 모자라 스왑이 생긴다(실험 09)
JAVA_OPTS=${JAVA_OPTS:--Xmx384m}
DOCKER=$(command -v docker || echo "$HOME/.docker/bin/docker")
mkdir -p "$LOGS"

start() {
  local name=$1; shift
  # shellcheck disable=SC2086
  nohup "$JAVA" $JAVA_OPTS -jar "$ROOT/services/$name/build/libs/$name-0.0.1-SNAPSHOT.jar" "$@" > "$LOGS/$name.log" 2>&1 &
  echo $! > "$LOGS/$name.pid"
}

case "${1:-}" in
  start)
    shift
    export MYSQL_PASSWORD="${MYSQL_PASSWORD:-$("$DOCKER" exec limited-deals-mysql printenv MYSQL_PASSWORD)}"
    start queue-service
    start order-service "$@"
    start notification-service
    start payment-service
    start gateway
    for name in queue-service order-service notification-service payment-service gateway; do
      for _ in $(seq 1 90); do
        grep -qE "Started [A-Za-z]+Application" "$LOGS/$name.log" 2>/dev/null && { echo "$name 시작됨"; break; }
        grep -qE "APPLICATION FAILED|Application run failed" "$LOGS/$name.log" 2>/dev/null && { echo "$name 실패 (로그: $LOGS/$name.log)"; exit 1; }
        sleep 1
      done
    done
    ;;
  stop)
    for name in gateway payment-service notification-service order-service queue-service; do
      [ -f "$LOGS/$name.pid" ] && kill "$(cat "$LOGS/$name.pid")" 2>/dev/null && echo "$name 종료" || true
      rm -f "$LOGS/$name.pid"
    done
    ;;
  restart)   # 서버 하나만 다시 띄운다. 예: FAILING_PRODUCT_IDS=7 scripts/run-local.sh restart notification-service
    name=$2; shift 2
    export MYSQL_PASSWORD="${MYSQL_PASSWORD:-$("$DOCKER" exec limited-deals-mysql printenv MYSQL_PASSWORD)}"
    [ -f "$LOGS/$name.pid" ] && kill "$(cat "$LOGS/$name.pid")" 2>/dev/null || true
    sleep 2
    start "$name" "$@"
    for _ in $(seq 1 90); do
      grep -qE "Started [A-Za-z]+Application" "$LOGS/$name.log" 2>/dev/null && { echo "$name 다시 시작됨"; exit 0; }
      sleep 1
    done
    echo "$name 시작 실패"; exit 1
    ;;
  kill)   # 실험용: 서버 하나를 끈다
    [ -f "$LOGS/$2.pid" ] && kill "$(cat "$LOGS/$2.pid")" 2>/dev/null && rm -f "$LOGS/$2.pid" && echo "$2 종료"
    ;;
  *) echo "사용법: $0 start|stop|restart <서버>|kill <서버>"; exit 1 ;;
esac
