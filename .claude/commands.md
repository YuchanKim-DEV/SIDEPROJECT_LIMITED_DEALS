# Commands

Multi-module: `:<module>:<task>` runs a task in one service. Modules: `gateway`, `queue-service`, `order-service`.

```bash
./gradlew build                                # build and test all services
./gradlew :order-service:test                  # test one service
./gradlew :order-service:test --tests "com.limiteddeals.order.OrderApplicationTests"
./gradlew projects                             # list modules
./gradlew javaToolchains                       # verify JDK 25 is found

# local run (Docker Desktop must be running)
docker compose up -d                           # MySQL 3306, Redis 6379
set -a; source .env; set +a                    # MYSQL_PASSWORD for order-service
./gradlew :order-service:bootRun               # 8082
./gradlew :queue-service:bootRun               # 8081
./gradlew :gateway:bootRun                     # 8080
curl localhost:8080/api/queue/ping             # routed to queue-service
curl localhost:8080/api/orders/ping            # routed to order-service
```

Claude's shell does not load `~/.zshrc`: use `~/.docker/bin/docker`. Never read `.env`.
If reality differs from this file, tell the user and ask before updating it.
