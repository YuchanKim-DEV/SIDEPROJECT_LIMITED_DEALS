# Architecture

Expected to change. If the code or the user's decision differs from this file, tell the user and ask before updating.

## Style: MSA in one repo (Gradle multi-module)
Each service is its own Spring Boot app (own main class, `application.yaml`, port, jar). They are deployed separately; only the code lives in one repo.

| Module | Port | Role | Store |
|---|---|---|---|
| `services/gateway` | 8080 | Entry point. Spring Cloud Gateway (WebFlux) routes by path | - |
| `services/queue-service` | 8081 | Virtual waiting room, queue numbers, entry tokens (to build) | Redis |
| `services/order-service` | 8082 | Orders and stock; holds the old `Member` code | MySQL |

Routes (gateway `application.yaml`): `/api/queue/**` → queue-service, `/api/orders/**` → order-service.
Packages: `com.limiteddeals.gateway`, `com.limiteddeals.queue`, `com.limiteddeals.order`.

## Gradle
- `settings.gradle.kts`: list of modules (`services/<name>` mapped to `:<name>`).
- Root `build.gradle.kts`: shared config only (Java 25 toolchain, Lombok, JUnit, Spring Cloud BOM `2025.1.4`). Plugins are declared with `apply false`.
- Each module's `build.gradle.kts`: its own plugins and dependencies.

## Rules
- Inside a service: `controller` → `service` → `repository` → `domain`, `dto` at the API boundary; never expose entities.
- One database (schema) per service. Services never read another service's tables.
- Share as little as possible between services (event contracts only, if a shared module is ever added). Sharing entities or logic makes a distributed monolith.
- Services talk over HTTP (through the gateway) or Kafka events (planned: Outbox on the producer, Inbox on the consumer).

## Planned flow (not built yet)
k6 load → gateway (Rate Limit, Redis) → queue-service (admission control: queue number in a Redis Sorted Set, entry token) → order-service (stock lock + order + outbox in one transaction) → Kafka (key = product id) → consumer (Inbox dedup). Metrics and logs: Prometheus, Grafana, Loki. Later: Kubernetes, payments/settlement.

## Not decided yet
- Exact roadmap for the MSA version (`docs/roadmap.md` still describes the single-app plan).
- Whether to split stock out of order-service.
- Test strategy (Testcontainers for MySQL, Redis, Kafka).
