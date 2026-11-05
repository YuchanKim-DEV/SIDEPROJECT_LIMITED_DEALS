# Commands

Multi-module: `:<module>:<task>` runs a task in one service. Modules: `gateway`, `queue-service`, `order-service`, `notification-service`, `payment-service`.

```bash
./gradlew build                                # build and test all services (queue tests need local Redis; skipped otherwise)
./gradlew :order-service:test                  # test one service
./gradlew projects                             # list modules

# local run (Docker Desktop must be running)
docker compose up -d                           # MySQL 3306, Redis 6379, Kafka 9092, Prometheus 9090, Loki 3100, Grafana 3000
./gradlew bootJar
scripts/run-local.sh start --spring.jpa.show-sql=false          # gateway 8000, queue 8081, order 8082, notification 8083, payment 8084 (heap 384m each)
scripts/run-local.sh restart order-service                      # also: kill <svc>
ENTRY_TOKEN_REQUIRED=true scripts/run-local.sh start            # orders need a queue entry token
scripts/run-local.sh stop                                       # logs: build/run-logs/
ADMIN_PASSWORD=<pw> scripts/run-local.sh start                 # opens admin login (gateway); JWT_SECRET optional locally
open http://localhost:8000                                      # test console (dev login per user id, admin login)
open http://localhost:3000                                      # Grafana dashboard LIMITED_DEALS

# experiments (docs/experiments/)
scripts/load/oversell.sh http://localhost:8082 100 1000 200 atomic   # phase 1
scripts/load/idempotency.sh http://localhost:8082 50 50 with-key     # phase 2
scripts/load/open-run.sh http://localhost:8000 50 300 300            # phase 3 (ENTRY_TOKEN_REQUIRED=true)
scripts/load/event-delivery.sh                                       # phase 4 (stops and starts Kafka, notification)
scripts/load/resilience.sh                                           # phase 5 (starts its own servers)
scripts/load/k6.sh order-strategies.js -e STRATEGY=atomic -e RATE=600 [-e PRODUCTS=10]   # phase 6
scripts/load/k6.sh open-run.js -e VUS=1000 -e STOCK=100              # phase 6, 9 (ENTRY_TOKEN_REQUIRED=true; logs in per user)
scripts/load/payment-saga.sh 100                                     # phase 8 (starts its own servers)

# containers and Kubernetes (phase 7)
scripts/deploy/build-images.sh                                       # jars + images limited-deals/<svc>:local
docker compose --profile apps up -d                                  # apps in containers (stop run-local first)
scripts/deploy/k8s.sh up|down                                        # kind cluster, gateway on localhost:8000
```

Calls through the gateway need `Authorization: Bearer <token>` from `POST /api/auth/dev-login {"userId":"..."}`. Admin paths need `admin-login`. `AUTH_ENABLED=false` on the gateway restores the old header trust (local experiments only).
The laptop (8GB) cannot run 5 JVMs, the compose infra and kind at once: stop run-local and Grafana/Prometheus before `k8s.sh up`.

k6: use the native binary (`brew install k6`); `k6.sh` falls back to Docker, which distorts results under load.

`scripts/run-local.sh` reads `MYSQL_PASSWORD` from the running MySQL container, never from `.env`.
Claude's shell does not load `~/.zshrc`: use `~/.docker/bin/docker` or prefix `PATH="$HOME/.docker/bin:$PATH"`. Never read `.env`.
If reality differs from this file, tell the user and ask before updating it.
