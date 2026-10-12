# HANDOFF

Read only when asked or when resuming a session. Kept current automatically (see working-rules.md).

Last updated: 2026-10-12

## Done
- Project skeleton from Spring Initializr (Spring Boot 4.1.1) with a `Member` CRUD slice and a repository test.
- JDK 25 toolchain through `gradle.properties` (PR #4). Default `java` stays 21.
- GitHub convention in `docs/github-convention.md` and PR template (PR #1, #2). Merge method: Create a merge commit.
- Blog posts published (SIDE PROJECTS / LIMITED_DEALS): project setup (Spring Boot, JDK 25) and GitHub convention.
- **Claude setup finished**: slim `CLAUDE.md` index + `.claude/*.md` detail files, permissions (`settings.json`), 7 PreToolUse hooks + 1 PostToolUse hook (`.claude/hooks/`), memory cleaned to 3 files.
- Home `~/CLAUDE.md` (merged guidance of LIGIDFPoC, Algorithm, ADJU) was deleted at the user's request, backup included. Other projects no longer get that guidance.

## Current step
Claude setup is done (skills are added later, as the project needs them). The Claude files are being uploaded in ONE PR (`chore/claude-setup/2026-10-12`, 4 commits split by nature); the user merges it. After the merge: `git checkout main && git pull`, delete the local branch, then plan step 4.

## Decisions made on the way
- One PR per purpose, one commit per nature (`docs/github-convention.md` section 3, `.claude/commit-flow.md`).
- At a checkpoint or when the user switches tasks with uncommitted work: announce the commit plan and ask with AskUserQuestion (`uncommitted-reminder.py` hook reminds).
- Claude files are public now; personal email and local paths were generalized before publishing. Only `.claude/settings.local.json` stays ignored.

## Plan after that
4. Confirm tech stack, 5. Decide architecture (package structure, layered details), 6. Test strategy.

## Open items
- VS Code null analysis popup: user decided to choose Yes, not yet clicked.
- DB: no datasource configured; choose H2 for local first, PostgreSQL profile later.
- First domain idea: `Product` and `Deal` entities with CRUD.
- External skills surveyed (not installed): revisit `java-springboot` when deciding architecture and a TDD skill when starting tests. Built-in `/code-review`, `/simplify`, `/security-review` already exist.
- Optional: restrict the GitHub repo to the chosen merge method and auto-delete head branches (Settings).
- Optional: move the blog repo remote to `YuchanKim-DEV/YuchanKim-Dev.github.io` (GitHub reports the repo moved).

## Known state notes
- Closed PR #3 was replaced by PR #4 after a `main` history rewrite.
- `main` commits from the rewrite are authored as `YuchanKim-DEV`; new commits use the global `Yuchan Kim`. The user chose to leave it.
