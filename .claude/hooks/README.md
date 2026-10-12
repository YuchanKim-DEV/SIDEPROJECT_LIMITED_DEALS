# Hooks

`PreToolUse` hooks registered in `.claude/settings.json`. Each script checks one kind of action. Exit 2 = blocked (the message goes back to Claude), exit 0 = allowed. A bug inside a hook is ignored (allowed) so it never breaks a session.

| Script | Tool | Checks | Scope |
|---|---|---|---|
| `no-attribution.py` | Bash | no `Co-Authored-By` / "Generated with Claude Code" in `git commit`, `gh pr create/edit` | all repos |
| `commit-guard.py` | Bash | subject `type(scope): subject`; no commit on `main` | this repo only |
| `push-guard.py` | Bash | no `-f/--force/--delete/+refspec/:branch`; no direct push to `main` | forms: all repos, main: this repo |
| `branch-guard.py` | Bash | new branch name `type/description/YYYY-MM-DD` with a real date | this repo only |
| `pr-guard.py` | Bash | PR title format; body has the 4 template headings | this repo only |
| `stage-guard.py` | Bash | no secrets (`.env`, `*.pem`, ...), no `git add -f`, bulk adds checked via `git status` | all repos |
| `blog-guard.py` | Write/Edit/MultiEdit | files in `_posts/`: no future date, no attribution | blog posts |

One `UserPromptSubmit` hook: `uncommitted-reminder.py`. When the working tree has uncommitted changes, it adds a note to each prompt so Claude follows `.claude/commit-flow.md` (announce the commit plan and ask with a choice UI at a checkpoint or task switch). Silent when the tree is clean.

Two `PostToolUse` hooks (Bash), both triggered when `gh pr create` succeeds in this repo (a hook cannot write prose; it only tells Claude to):
- `handoff-reminder.py`: refresh `.claude/HANDOFF.md`.
- `blog-reminder.py`: propose blog posts for the PR and ask how to write them (`.claude/blog.md`).

`uncommitted-reminder.py` ignores `.claude/HANDOFF.md`, which changes constantly and rides along with the next work PR.

`_common.py` holds the shared helpers. The blog repo is exempt from commit/branch/PR rules (it uses `Add post:` / `Fix post:` and commits on `main`).

## Known limits
- Matching is text-based. A command that only *mentions* attribution text next to `git commit` (for example in a test script) is also blocked.
- `sed -i` or other shell edits of `_posts/` bypass `blog-guard.py` (it watches the Write/Edit tools).
- Compound or obfuscated commands (`eval`, variables holding the command) can slip through. Permissions in `settings.json` and `CLAUDE.md` are the other layers.

## Test
Feed fake hook JSON to the scripts; nothing real runs. Run all hooks from `settings.json` against sample commands and compare exit codes.
