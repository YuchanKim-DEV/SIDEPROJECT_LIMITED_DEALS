#!/usr/bin/env bash
# k6 실행기. 맥에 k6가 설치돼 있으면(brew install k6) 그대로 쓰고, 없으면 Docker 이미지로 실행한다.
# 맥에서 직접 돌리는 쪽을 권장한다: Docker로 돌리면 모든 연결이 Docker 네트워크 중계를 거쳐서, 부하가 크면 측정이 왜곡된다.
# 사용법: scripts/load/k6.sh <scripts/k6 안의 파일> [k6 옵션...]
# 예:     scripts/load/k6.sh order-strategies.js -e STRATEGY=pessimistic -e RATE=300
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
SCRIPT=$1; shift
if command -v k6 > /dev/null; then
  exec k6 run --quiet -e HOST=localhost "$@" "$ROOT/scripts/k6/$SCRIPT"
fi
DOCKER=$(command -v docker || echo "$HOME/.docker/bin/docker")
exec "$DOCKER" run --rm -i --add-host=host.docker.internal:host-gateway \
  -v "$ROOT/scripts/k6:/scripts:ro" grafana/k6:1.8.2 run --quiet -e HOST=host.docker.internal "$@" "/scripts/$SCRIPT"
