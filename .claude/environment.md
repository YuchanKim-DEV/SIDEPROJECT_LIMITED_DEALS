# Environment

Facts that cannot be read from the code. If reality differs from this file, tell the user and ask before updating.

## Stack
- Spring Boot 4.1.1, Gradle 9.7.1 (Kotlin DSL), editor: VS Code
- Java 25 via Gradle toolchain (`build.gradle.kts`). System default `java` is 21.
- `starter-webmvc` (MVC, not WebFlux), `starter-data-jpa`, `starter-validation`, `starter-actuator`, Lombok, DevTools
- DB drivers: H2 (+ `spring-boot-h2console`) and PostgreSQL. No MySQL driver.
- **No datasource configured yet.** `application.yaml` only sets the app name and JPA SQL logging, so Spring Boot uses embedded H2. DB choice is open.
- Root package: `com.limiteddealproducts`

## JDK
- Gradle finds JDK 25 through `gradle.properties` (`org.gradle.java.installations.paths`, Homebrew `openjdk@25`). The path is specific to this MacBook.
- Verify with `./gradlew javaToolchains`. Lombok prints a harmless `sun.misc.Unsafe` warning on JDK 25.
- Do not change the system default `java` or `JAVA_HOME`.

## Git identity
- Commit author comes from the global git config (name `Yuchan Kim`; the email is the one registered on the GitHub account). The GitHub account is `YuchanKim-DEV`; GitHub links them by email. `gh` has two accounts logged in; the active one is `YuchanKim-Dev`.
- Older `main` commits are authored as `YuchanKim-DEV`. The user chose to leave that.
- The repo-local git config is intentionally empty.

## Shell (zsh)
- `"$var:ref"` is parsed as a modifier; write `"${var}:ref"`. Unquoted variables are not word-split.

## Claude files and git
`CLAUDE.md` and `.claude/` are tracked and public (to show how the user works with AI). Only `.claude/settings.local.json` (personal settings) and Python caches are ignored. Never commit secrets or personal data; `stage-guard.py` blocks the obvious ones.
