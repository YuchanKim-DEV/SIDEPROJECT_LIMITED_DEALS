# Commit and PR flow

How Claude proposes commits and PRs. The rules themselves are in `docs/github-convention.md`; this file is the procedure. If reality differs from this file, tell the user and ask before updating it.

## Branch first
When a substantial task (one purpose) starts, create its branch `type/description/YYYY-MM-DD` from an up-to-date `main` **before** changing anything, and work on it. Never create the branch right before pushing. Run `git checkout -b <name>` as its own command (hook gap with `-q -b`).

## When to raise it
- A meaningful checkpoint is reached: a feature and its tests pass (`./gradlew test`), or a docs/config change is complete.
- The user asks for a **different task** while there are uncommitted changes. A `UserPromptSubmit` hook (`uncommitted-reminder.py`) reminds you whenever the working tree has uncommitted changes.
- Do not ask for tiny partial edits or half-finished work. Ask only at a checkpoint or on a task switch.

## Procedure
1. **Announce the plan first. Do not commit yet.** State:
   - Branch: `type/description/YYYY-MM-DD` (type = the main purpose)
   - Commits, split by nature, each as `type(scope): subject` with its files
   - PR: title and a one-line summary (one PR per purpose)
2. **Ask with the choice UI (AskUserQuestion).** Typical options:
   - Commit and open a PR first, then continue (recommended)
   - Keep working; commit later
   - Switch to the new task now without committing
   On a task switch, name the new task in the question.
3. **Only after the user picks the first option**, commit, push, and open the PR (`do-not.md` item 3). Stage files by name, never `git add -A`.
4. After `gh pr create`, (a) refresh `.claude/HANDOFF.md` and (b) propose blog posts for the PR and ask how to write them (`.claude/blog.md`). Hooks remind you of both. The user merges the PR.
5. `HANDOFF.md` changes are not committed on their own; they ride along with the next work PR (as a `docs` commit).

## Split by nature
| Changed files | Commit type |
|---|---|
| `src/main/**` | `feat`, `fix`, `changes`, `refactor` (by what the change does) |
| `src/test/**` | `test` |
| `docs/**`, `CLAUDE.md`, `.claude/**/*.md` | `docs` |
| `build.gradle.kts`, `gradle.properties`, `settings.gradle.kts` | `build` |
| `.gitignore`, `.github/**`, `.claude/settings.json`, `.claude/hooks/**` | `chore` |

One commit = one nature. A PR may contain several commits when they serve one purpose.
