# Architecture

Expected to change. If the code or the user's decision differs from this file, tell the user and ask before updating.

## Style: MSA in one repo (Gradle multi-module)
Each service is its own Spring Boot app (own main class, `application.yaml`, port, jar). They are deployed separately; only the code lives in one repo.

| Module | Port | Role | Store |
|---|---|---|---|
| `services/gateway` | 8000 | Entry point. Spring Cloud Gateway (WebFlux) routes by path, Redis rate limiting, serves the test console (`static/index.html`). 8000 because another local project holds 8080 | Redis (rate-limit counters) |
| `services/queue-service` | 8081 | Virtual waiting room: Sorted Set queue, admission scheduler (Redis lock, N per second), entry tokens with TTL, SSE position stream, simulate endpoint | Redis |
| `services/order-service` | 8082 | Products, stock, orders: 4 stock strategies (`?strategy=`), idempotency keys, entry-token check against queue-service (`ENTRY_TOKEN_REQUIRED`, circuit breaker, fail closed), Outbox + relay to Kafka `order-events` | MySQL |
| `services/notification-service` | 8083 | Kafka consumer of `order-events`: Inbox dedup by event id, 3 retries then DLT `order-events-dlt` | MySQL (`notification_*` tables) |
| `services/payment-service` | 8084 | Saga payment step: consumes `order-events` (Inbox), calls a mock PG outside the transaction, double-entry ledger, Outbox to `payment-events`; reconciliation every 30s (UNKNOWN/REQUESTED → PG inquiry), daily settlement | MySQL (`payments`, `payment_*`, mock PG `pg_transactions`) |

order-service also consumes `payment-events`: `PENDING_PAYMENT` → `PAID`, or `CANCELLED` + stock restored (conditional UPDATE, idempotent).
Security (phase 9): the gateway `AuthFilter` verifies a JWT (HS256, `JWT_SECRET`), strips client `X-User-Id`/`X-User-Role` and sets them from the token. Backends trust only those headers. Path rules in `AccessRules` (public / user / admin / internal-only). Order and payment lookups are owner-only (404 otherwise).
Deploy (phase 7): one `infra/docker/Dockerfile` (`--build-arg SERVICE`), compose `apps` profile, `infra/k8s/` (kind, HPA on queue-service). Infra stays outside the cluster.

Infra (`docker-compose.yml`): MySQL 3306, Redis 6379, Kafka 9092 (KRaft), Prometheus 9090, Loki 3100, Grafana 3000 (dashboard provisioned from `infra/grafana/`).
Every Boot module gets the Prometheus registry and the Loki logback appender from the root `build.gradle.kts`; tests use `logback-test.xml` (no Loki).
Spring Cloud CircuitBreaker's default bulkhead (25 concurrent calls) is turned off in gateway and order-service (`docs/experiments/06-observability-load.md`).

Routes (gateway `application.yaml`): `/api/queue/**` → queue-service (rate limit per user 20/s, burst 40), `/api/orders/**` and `/api/products/**` → order-service (global 500/s, burst 1000), `/api/notifications/**` → notification-service. `/api/payments/**` → payment-service, `/api/auth/**` served by the gateway. All routes but the SSE stream have a circuit breaker (10s time window, min 100 calls, 3s timeout) with a 503 fallback.
Packages: `com.limiteddeals.gateway`, `com.limiteddeals.queue`, `com.limiteddeals.order`, `com.limiteddeals.notification`, `com.limiteddeals.payment`.

## Gradle
- `settings.gradle.kts`: list of modules (`services/<name>` mapped to `:<name>`).
- Root `build.gradle.kts`: shared config only (Java 25 toolchain, Lombok, JUnit, Spring Cloud BOM `2025.1.4`). Plugins are declared with `apply false`.
- Each module's `build.gradle.kts`: its own plugins and dependencies.

## Rules
- Inside a service: `controller` → `service` → `repository` → `domain`, `dto` at the API boundary; never expose entities.
- One database (schema) per service. Services never read another service's tables.
- Share as little as possible between services (event contracts only, if a shared module is ever added). Sharing entities or logic makes a distributed monolith.
- Services talk over HTTP (through the gateway) or Kafka events (Outbox on the producer, Inbox on the consumer).

## Flow (phases 1-9 built; reasons in `docs/design-decisions.md`)
k6 load → gateway (JWT, Rate Limit, Redis) → queue-service (admission control: queue number in a Redis Sorted Set, entry token) → order-service (sold-out fast path, then order + outbox + stock UPDATE last, one transaction) → Kafka (key = product id) → notification-service (Inbox, DLT) and payment-service (PG, ledger) → `payment-events` → order-service (PAID / CANCELLED). Metrics and logs: Prometheus, Grafana, Loki.

## Not decided yet
- Whether to split stock out of order-service.
- Test strategy (Testcontainers for MySQL, Redis, Kafka).
