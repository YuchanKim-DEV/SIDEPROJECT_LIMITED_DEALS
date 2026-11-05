#!/usr/bin/env bash
# 서버 5개의 jar를 만들고 Docker 이미지(limited-deals/<서버>:local)로 굽는다.
# 사용법: scripts/deploy/build-images.sh
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
DOCKER=$(command -v docker || echo "$HOME/.docker/bin/docker")
cd "$ROOT"
./gradlew bootJar -q
for s in gateway queue-service order-service notification-service payment-service; do
  "$DOCKER" build -q -f infra/docker/Dockerfile --build-arg SERVICE="$s" -t "limited-deals/$s:local" . > /dev/null
  echo "이미지 완료: limited-deals/$s:local"
done
