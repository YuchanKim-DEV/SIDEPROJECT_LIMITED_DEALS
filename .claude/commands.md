# Commands

Multi-module: `:<module>:<task>` runs a task in one service. Modules: `gateway`, `queue-service`, `order-service`.

```bash
./gradlew build                                # build and test all services (queue tests need local Redis; skipped otherwise)
./gradlew :order-service:test                  # test one service
./gradlew projects                             # list modules

# local run (Docker Desktop must be running)
docker compose up -d                           # MySQL 3306, Redis 6379
./gradlew bootJar
scripts/run-local.sh start --spring.jpa.show-sql=false          # gateway 8000, queue 8081, order 8082
ENTRY_TOKEN_REQUIRED=true scripts/run-local.sh start            # orders need a queue entry token
scripts/run-local.sh stop                                       # logs: build/run-logs/
open http://localhost:8000                                      # test console

# experiments (docs/experiments/)
scripts/load/oversell.sh http://localhost:8082 100 1000 200 atomic   # phase 1
scripts/load/idempotency.sh http://localhost:8082 50 50 with-key     # phase 2
scripts/load/open-run.sh http://localhost:8000 50 300 300            # phase 3 (ENTRY_TOKEN_REQUIRED=true)
```

`scripts/run-local.sh` reads `MYSQL_PASSWORD` from the running MySQL container, never from `.env`.
Claude's shell does not load `~/.zshrc`: use `~/.docker/bin/docker` or prefix `PATH="$HOME/.docker/bin:$PATH"`. Never read `.env`.
If reality differs from this file, tell the user and ask before updating it.
