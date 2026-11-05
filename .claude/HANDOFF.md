# HANDOFF

Read only when asked or when resuming a session. Kept current automatically (see working-rules.md).

Last updated: 2026-11-05

## Direction change (2026-11-02, decided with the user)
- The user wants **MSA** and an "online open-run" flow: simple test/ops dashboard (no fancy UI), k6 load (10k concurrent, 100k queued), Rate Limit, virtual waiting room with queue numbers (Redis), order with Outbox → Kafka → consumer with Inbox, Grafana/Prometheus/Loki for metrics and logs, later Kubernetes.
- Analyzed 19 job postings (Toss 4 + Data list, Coupang 8, TVING 3, Daangn 2, KakaoPay 1). Common: Kotlin/Java + Spring Boot, MySQL/JPA, Kafka, Redis, MSA, K8s, AWS; problems: 정합성 (money/state), traffic spikes, incident response/post-mortem, AI tool usage. Toss/KakaoPay default language is Kotlin.
- Decisions: **keep Java**, **payments/settlement (ledger, reconciliation) later**, **keep the name LIMITED_DEALS**.
- **Multi-module MSA skeleton built** (branch `build/msa-multi-module/2026-11-02`, by Claude at the user's request): `services/gateway` (8080, Spring Cloud Gateway 2025.1.4), `services/queue-service` (8081, Redis), `services/order-service` (8082, old Member code, MySQL). Redis 8.8.4 added to compose. `./gradlew build` passes (4 tests); runtime + curl routing not verified yet (Docker was off). PR #12 verified at runtime (gateway on 18080 because another project, `SIDE_PROJECTS/GOV...`, holds 8080) and **merged** by Claude on 2026-11-04. Its remote branch `build/msa-multi-module/2026-11-02` was left (merged without `--delete-branch`).
- **Role change (2026-11-04)**: Claude does all work, branches, backups, merges; the user reviews. Commit plans and merges still need the user's OK; blog publishing too. `gh pr merge` moved from deny to ask (the user asked). Branch `docs/role-change/2026-11-04`.
- PR #13 (role change) merged with `--delete-branch`; only `main` remains.
- The user confirmed the plan (online open-run, MSA, phases 0-9). Written into `docs/roadmap.md`, PR #14 open (2026-11-04). **Phase 1 (stock concurrency) in progress** on `feat/product-stock/2026-11-04` (commits local, not pushed; one PR when phase 1 is done). Teaching loop per piece: plain explanation → Claude implements → run and show → one check question (the user is still learning; go slowly). Done: 1-1 entity (Product), 1-2 product API, 1-3 transactions (order API, rollback demo test), 1-4 oversell reproduced (100 stock → 965, 255, 654 orders; `scripts/load/oversell.sh`, `docs/experiments/01-stock-concurrency.md`), 1-5 atomic UPDATE (`?strategy=atomic`, default; 0 oversell twice). **Paused after 1-5 (2026-11-04).** Next: 1-6 pessimistic lock (add `PESSIMISTIC` to `StockStrategy`), 1-7 optimistic lock, 1-8 compare at 10k. To run: start Docker, `docker compose up -d`, order-service with `MYSQL_PASSWORD` from the container env, `scripts/load/oversell.sh http://localhost:8082 100 1000 200 <strategy>`. Details: `architecture.md`, `commands.md`.
- Still open: roadmap rewrite for MSA. The `ddl-auto` experiment branch was dropped (no commits); redo it inside order-service later.

## Autonomous build (2026-11-05, the user asked: build everything locally, no questions, report at the end; no merges, no blog until review)
- Stacked PRs, merge in order after the user's review: #15 phase 1 (stock) ← #16 phase 2 (idempotency) ← #17 phase 3 (waiting room) ← #18 phase 4 (Kafka outbox/inbox/DLT) ← #19 phase 5 (resilience) ← phase 6 (observability, branch `feat/observability/2026-11-05`).
- Done: phases 1-6 with tests and experiments `docs/experiments/01..06`. Phase 6 found: one hot row caps at ~200-345 orders/s, a bigger pool is worse, 10 rows ~590/s; Docker k6 distorts; the hidden Spring Cloud CB bulkhead (25 concurrent) caused 503s, turned off. Open-run 1,000 users / stock 100 → 100 bought, 900 sold out.
- Next: phase 7 Dockerfiles + compose app profile + K8s manifests, phase 8 payments/settlement, phase 9 security (JWT), then `docs/design-decisions.md` (why Kafka, duplicates, consumer failure, Redis vs DB mismatch, 10x traffic), then blog drafts (not published).

## Now
- PR #6 (roadmap, working mode, answer style) and #7 (branch first) are merged; their local branches were cleaned up.
- PR #8 (branch care) and #9 (local MySQL compose + `.env` ignored) are merged; branches cleaned up. Local and remote have only `main`.
- `.env.example` was not added (the user created only `.env`); decision pending.
- Blog posts published: roadmap, Docker/MySQL. From now on, ask with the choice UI right after writing a post (`blog.md`, change not committed yet; goes with the next Claude-settings PR).
- 2026-10-18: the app connects to local MySQL (driver `mysql-connector-j`, datasource in `application.yaml`, password from env `MYSQL_PASSWORD`; run with `set -a; source .env; set +a; ./gradlew bootRun`). H2 is test-only (`src/test/resources/application.yaml`). Tests pass. PR #10 merged. PR #11 (blog rule: ask to publish with the choice UI) open. Next: tables with JPA `ddl-auto`, then Flyway. Later: consider `spring.jpa.open-in-view: false`.
- Roadmap blog post written, not published (`_posts/2026-10-18-limited-deals-roadmap.md`); Docker post comes with PR ②.
- Then: tables by JPA `ddl-auto` first, then Flyway.

## Done
- Project skeleton from Spring Initializr (Spring Boot 4.1.1) with a `Member` CRUD slice and a repository test.
- JDK 25 toolchain through `gradle.properties` (PR #4). Default `java` stays 21.
- GitHub convention in `docs/github-convention.md` and PR template (PR #1, #2). Merge method: Create a merge commit.
- Blog posts published (SIDE PROJECTS / LIMITED_DEALS): project setup (Spring Boot, JDK 25) and GitHub convention.
- **Claude setup finished**: slim `CLAUDE.md` index + `.claude/*.md` detail files, permissions (`settings.json`), 7 PreToolUse hooks + 1 PostToolUse hook (`.claude/hooks/`), memory cleaned to 3 files.
- Home `~/CLAUDE.md` (merged guidance of LIGIDFPoC, Algorithm, ADJU) was deleted at the user's request, backup included. Other projects no longer get that guidance.

## Current step
Claude setup is done and merged (PR #5; skills are added later, as the project needs them). A blog post about it is written but NOT published yet: `_posts/2026-10-12-limited-deals-claude-code-setup.md` in the blog repo, waiting for the user's review and OK to push. Next: plan step 4 (tech stack).

## Decisions made on the way
- One PR per purpose, one commit per nature (`docs/github-convention.md` section 3, `.claude/commit-flow.md`).
- At a checkpoint or when the user switches tasks with uncommitted work: announce the commit plan and ask with AskUserQuestion (`uncommitted-reminder.py` hook reminds).
- Blog posts are triggered by creating a PR: propose topics and ask how to write them (`blog-reminder.py`, `.claude/blog.md`). Publishing still waits for the user's OK.
- `HANDOFF.md` changes are excluded from the uncommitted reminder and ride along with the next work PR.
- Claude files are public now; personal email and local paths were generalized before publishing. Only `.claude/settings.local.json` stays ignored.

## Plan after that
4. Confirm tech stack, 5. Decide architecture (package structure, layered details), 6. Test strategy.

## Open items
- VS Code null analysis popup: user decided to choose Yes, not yet clicked.
- **Goal (decided 2026-10-12)**: build the backend with the must-have production concerns done deeply: large data volume, no data loss, concurrency, idempotency. Domain: limited-time deals (flash sale).
- **DB decided: MySQL 8.4.11 (InnoDB, image `mysql:8.4.11`, pinned; never `latest`), run with Docker Compose.** The user pulled the image in Docker Desktop and is about to write `docker-compose.yml` and `.env` (add `.env` to `.gitignore`) by typing the content Claude provided. H2 is not suitable for concurrency study. Still to add: MySQL driver (build.gradle.kts), Docker Compose file, datasource config. `environment.md` still says the DB is open; update it after asking the user.
- **Roadmap confirmed (A to H, plus step 0 setup)**: kept in `docs/roadmap.md` (the user wants it managed as its own file; update it and its change log when the plan changes). Order: 0 stack setup → A stock concurrency → B idempotency → C large data → D Redis → E events (Kafka + Outbox) → F failure handling → G observability and load tests → H security.
- **Docker: terminal and compose files first** (with deployment in mind: servers have no GUI). On 2026-10-16 the user reinstalled Docker Desktop and also wants to learn its GUI, so the GUI is fine as a viewer (Containers, Logs). The new install puts the CLI in `~/.docker/bin`, which was not on PATH (`docker: command not found`); fix given: add it to `~/.zshrc` or switch Settings → Advanced to System. A later deployment step (RDS or EC2, profiles `local`/`prod`) may be added to the roadmap as step I.
- **Step 0 progress (2026-10-18)**: `docker-compose.yml` at the root (user's version: `MYSQL_USER: admin`, volume `mysql_data`, `restart: always`) and `.env` created; `limited-deals-mysql` (8.4.11) is up on 3306 with volume `limited_deals_mysql_data`. Next: user verifies login as `admin`, creates `.env.example`, then propose 2 PRs (roadmap/working-mode docs; step 0 compose + `.gitignore`). After that: MySQL driver, datasource config, Flyway/Testcontainers decision.
- 2026-10-18: login as `admin` verified. Decisions: tables are made by code, **trying both JPA `ddl-auto` first, then Flyway** to feel the difference. Replies must be short but complete (`answer-style.md`). DBeaver is installed (user connects it themselves).
- Claude's Bash shell does not load `~/.zshrc`, so `docker` is not on its PATH: use `~/.docker/bin/docker`. Docker Desktop must be running (`open -a Docker`), otherwise `docker.sock` is missing. Never read `.env` (denied by permissions).
- **Working mode changed (2026-10-12)**: the user does the hands-on work for the project (setup, code, tests); Claude explains, gives hints and steps, checks results, reviews. See `working-rules.md`.
- First domain idea: `Product` and `Deal` entities with CRUD.
- External skills surveyed (not installed): revisit `java-springboot` when deciding architecture and a TDD skill when starting tests. Built-in `/code-review`, `/simplify`, `/security-review` already exist.
- Optional: restrict the GitHub repo to the chosen merge method and auto-delete head branches (Settings).
- Optional: move the blog repo remote to `YuchanKim-DEV/YuchanKim-Dev.github.io` (GitHub reports the repo moved).

## Known state notes
- Closed PR #3 was replaced by PR #4 after a `main` history rewrite.
- `main` commits from the rewrite are authored as `YuchanKim-DEV`; new commits use the global `Yuchan Kim`. The user chose to leave it.
