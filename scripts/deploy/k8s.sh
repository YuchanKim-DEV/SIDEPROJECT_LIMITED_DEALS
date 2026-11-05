#!/usr/bin/env bash
# 로컬 Kubernetes(kind)에 서버 5개를 올린다. MySQL, Redis, Kafka, Loki는 docker compose 것을 그대로 쓴다.
# 사용법: scripts/deploy/k8s.sh up      클러스터 만들기(없으면) + 이미지 넣기 + 배포
#         scripts/deploy/k8s.sh down    클러스터 삭제
# 먼저: docker compose up -d && scripts/deploy/build-images.sh
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
export PATH="$HOME/.docker/bin:$PATH"
CLUSTER=limited-deals
NS=limited-deals
SERVICES=(gateway queue-service order-service notification-service payment-service)
METRICS_SERVER=https://github.com/kubernetes-sigs/metrics-server/releases/download/v0.8.0/components.yaml

case "${1:-up}" in
  up)
    FRESH=false
    if ! kind get clusters | grep -qx "$CLUSTER"; then
      kind create cluster --config "$ROOT/infra/k8s/kind-cluster.yaml"
      FRESH=true
    fi
    # kind 노드를 compose 네트워크에 붙여서, 파드가 mysql/redis/kafka/loki 이름으로 찾아가게 한다
    docker network connect limited_deals_default "$CLUSTER-control-plane" 2>/dev/null || true
    for s in "${SERVICES[@]}"; do kind load docker-image "limited-deals/${s}:local" --name "$CLUSTER"; done

    # HPA가 CPU 사용률을 읽으려면 metrics-server가 필요하다. kind의 kubelet 인증서는 자체 서명이라 검증을 끈다(로컬 전용)
    kubectl apply -f "$METRICS_SERVER" > /dev/null
    kubectl -n kube-system patch deployment metrics-server --type=json \
      -p='[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"}]' > /dev/null 2>&1 || true

    kubectl get namespace "$NS" > /dev/null 2>&1 || FRESH=true
    kubectl apply -f "$ROOT/infra/k8s/00-namespace.yaml"
    # 비밀번호는 파일에 두지 않고, 돌고 있는 MySQL 컨테이너에서 읽어 Secret으로 만든다
    kubectl -n "$NS" create secret generic limited-deals-db \
      --from-literal=MYSQL_PASSWORD="$(docker exec limited-deals-mysql printenv MYSQL_PASSWORD)" \
      --dry-run=client -o yaml | kubectl apply -f - > /dev/null
    # 게이트웨이 토큰 서명 키와 관리자 비밀번호: 없을 때만 무작위로 만든다(다시 배포해도 바뀌지 않아 로그인이 유지된다)
    if ! kubectl -n "$NS" get secret limited-deals-auth > /dev/null 2>&1; then
      kubectl -n "$NS" create secret generic limited-deals-auth \
        --from-literal=JWT_SECRET="$(openssl rand -base64 48)" \
        --from-literal=ADMIN_PASSWORD="$(openssl rand -base64 18)" > /dev/null
      echo "관리자 비밀번호: kubectl -n $NS get secret limited-deals-auth -o jsonpath='{.data.ADMIN_PASSWORD}' | base64 -d"
    fi
    for f in "$ROOT"/infra/k8s/[0-9]*.yaml; do kubectl apply -f "$f" > /dev/null; done
    # 다시 배포할 때: 이미지가 같은 태그(local)로 바뀌었어도 새로 뜨게 한다
    [ "$FRESH" = true ] || kubectl -n "$NS" rollout restart deployment > /dev/null
    for s in "${SERVICES[@]}"; do kubectl -n "$NS" rollout status "deployment/$s" --timeout=420s; done
    kubectl -n "$NS" get pods -o wide
    echo "게이트웨이: http://localhost:8000"
    ;;
  down)
    kind delete cluster --name "$CLUSTER"
    ;;
  *) echo "사용법: $0 up|down"; exit 1 ;;
esac
