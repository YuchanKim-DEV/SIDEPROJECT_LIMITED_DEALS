# 실험 07: 컨테이너와 Kubernetes 배포

같은 jar를 이미지로 굽고, docker compose와 로컬 Kubernetes(kind)에 올려서 "어디서 돌려도 같은 서버"가 되는지, 대기열 서버만 자동으로 늘어나는지 본다.

## 구성
| 파일 | 내용 |
|---|---|
| `infra/docker/Dockerfile` | 서버 4개 공용. `--build-arg SERVICE=<서버>`. jar를 층(의존성 / 스프링 / 앱 코드)으로 풀어 쌓는다. root가 아닌 사용자로 실행, 힙은 컨테이너 메모리의 75% |
| `docker-compose.yml` `apps` 프로필 | 서버 4개를 컨테이너로. 기본 `docker compose up`에는 뜨지 않는다 |
| `infra/k8s/` | 네임스페이스, ConfigMap(접속 정보), 서버별 Deployment + Service, 대기열 서버 HPA(2~6대, CPU 60%) |
| `scripts/deploy/build-images.sh` | jar 빌드 + 이미지 4개 |
| `scripts/deploy/k8s.sh up\|down` | kind 클러스터, 이미지 넣기, Secret(비밀번호는 MySQL 컨테이너에서 읽음), metrics-server, 배포 |

MySQL, Redis, Kafka, Loki는 Kubernetes 밖(compose)에 두고, kind 노드를 compose 네트워크에 붙여 이름으로 찾아가게 했다. 운영에서 DB와 메시지 큐를 관리형 서비스(RDS, ElastiCache, MSK)로 밖에 두고 앱만 클러스터에 올리는 모양과 같다. 상태가 있는 것(DB)과 없는 것(앱)을 나눠서, 앱은 마음대로 늘리고 죽여도 되게 한다.

## 다시 돌려보기
```bash
docker compose up -d && scripts/deploy/build-images.sh
docker compose --profile apps up -d          # A. compose로 실행
docker compose --profile apps stop gateway queue-service order-service notification-service
scripts/deploy/k8s.sh up                     # B. Kubernetes로 실행 (게이트웨이 localhost:8000)
scripts/load/k6.sh open-run.js -e VUS=300 -e STOCK=30
scripts/deploy/k8s.sh down
```

## 결과 (2026-11-05, 맥북 한 대, Docker Desktop VM 메모리 약 3.8GB, CPU 8개)
| 실험 | 결과 |
|---|---|
| 이미지 | 4개 모두 빌드, 565~678MB(JRE 포함). 코드만 바꾸면 맨 위 층(앱 코드)만 새로 만든다 |
| A. compose로 실행 | 4개 모두 health UP, Prometheus 대상 4개 up. 오픈런 500명/재고 50: DB 주문 **50건, 재고 0**(정확). 그러나 k6 집계는 구매 49, 포기 216. 게이트웨이 3초 시간 초과 639건 |
| B. kind 첫 배포 | 실패. 파드 5개가 동시에 뜨느라 기동에 **109초**. 시작 검사(최대 2분)에 걸려 재시작 반복, 메모리 요청 합이 노드보다 커서 일부 Pending |
| B. 조정 후 배포 | 시작 검사 5분, 메모리 요청 384Mi, 주문 서버 1대, JVM CPU 2개로 인식. 파드 5개 모두 Running, 재시작 0 |
| B. 오픈런 300명/재고 30 | DB 주문 **30건, 재고 0**(정확). 클라이언트는 대부분 포기(p99 15초). 부하 중 Kubernetes API 서버까지 응답 없음 |
| B. HPA | 대기열 서버 CPU 165%/60% → **2대에서 6대로 늘림**. 6번째는 `Insufficient memory`로 Pending. 게이트웨이 1회 재시작 |

### 8·9단계를 더한 뒤 다시 배포 (서버 5개, 인증 Secret 포함)
| 실험 | 결과 |
|---|---|
| 첫 배포 | 실패. 파드 6개가 뜨는 동안 CPU가 높아지자 **HPA가 대기열 서버를 기동 중에 6대로 늘렸다**. 노드 자원이 모자라 Pending과 재시작이 반복되고 API 서버까지 응답 없음 |
| HPA 끄고 대기열 1대 | 5개 모두 Ready. 로그인 → 줄 서기 → 주문 → **PAID**, 결제 **APPROVED**, 토큰 없이 주문 401, 관리자 대사 200(불일치 0), 일반 사용자 대사 403 |
| 고친 것 | HPA `scaleUp`: 1분 지켜보고 1분에 1대씩만 늘린다. Kubernetes도 막 뜬 파드의 CPU는 잠시 무시하지만(초기화 기간), 재시작이 반복되면 소용없다 |

## 무엇을 배웠나
- **데이터는 끝까지 맞았다.** 장비가 버티지 못해 응답이 늦고 클라이언트가 포기해도, 주문 수와 재고는 항상 일치했다(500명/50개, 300명/30개). 1~4단계에서 만든 정합성 장치(원자적 UPDATE, 멱등 키, Outbox)는 인프라가 느려져도 깨지지 않는다.
- **시간 초과는 실패가 아니다.** compose 실험에서 주문은 성공했는데 게이트웨이가 3초에 끊어서 사용자는 실패로 봤다(구매 49 vs 실제 50). 사용자 쪽에서는 같은 멱등 키로 다시 물어 "이미 처리됨"을 받아야 한다. 결제(8단계)에서 같은 문제를 대사(reconciliation)로 다룬다.
- **probe는 기동 시간에 맞춘다.** liveness만 있으면 느리게 뜨는 앱을 계속 죽인다. startupProbe로 기동 시간을 따로 주고, liveness는 외부(DB, Redis) 상태를 보지 않게 했다. 외부 장애 때 파드가 모두 재시작되는 연쇄를 막기 위해서다.
- **HPA는 기동 중인 JVM을 부하로 착각할 수 있다.** 뜨는 동안의 CPU(클래스 로딩, JIT 컴파일)로 파드를 늘리면, 늘어난 파드가 또 CPU를 써서 악순환이 된다. scaleUp 안정화 시간과 한 번에 늘리는 수를 제한했다.
- **HPA는 노드 자리가 있어야 의미가 있다.** 파드 수는 늘렸지만 노드 메모리가 없어 Pending이 됐다. 운영에서는 노드도 늘리는 Cluster Autoscaler(또는 Karpenter)가 짝으로 필요하다. 또 HPA는 CPU를 보고 판단해서 늦다. 오픈런처럼 시작 시각을 아는 트래픽은 미리 늘려 두는 편(예약 스케일)이 맞다.
- **requests가 판단 기준이다.** 스케줄러는 실제 사용량이 아니라 requests 합으로 자리를 잡고, HPA 사용률도 requests 대비다. CPU limit은 두지 않았다. limit에 걸리면 스로틀링으로 응답 지연이 커지기 때문이다.
- **JVM은 컨테이너를 모르면 과하게 쓴다.** 컨테이너 안에서도 CPU 8개로 보고 GC, 컴파일 스레드를 그만큼 만든다. `ActiveProcessorCount`, `MaxRAMPercentage`로 맞췄다.

## 한계
- 노트북 한 대의 Docker VM(약 3.8GB)에 인프라 6개 + Kubernetes + 파드 5개 + k6가 함께 돌아서, 처리량 숫자는 의미가 없다. "대기열 서버를 늘리면 처리량이 얼마나 오르나"는 노드를 여러 대 쓸 수 있는 환경(EKS 등)에서 다시 잰다.
- Prometheus는 아직 클러스터 안 파드를 수집하지 않는다(compose 쪽 고정 주소만 본다). 운영에서는 ServiceMonitor(Prometheus Operator)로 파드를 자동으로 찾는다.
- Ingress, TLS, 이미지 저장소(ECR), 무중단 배포 전략(롤링 업데이트 설정, PodDisruptionBudget)은 서버 배포 때 추가한다.
