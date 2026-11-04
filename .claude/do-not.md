# Do not

Hard rules. Hooks and permissions in `.claude/settings.json` enforce part of them (see `.claude/hooks/README.md`), but follow them even where nothing would block you.

1. **Never add attribution**: no `Co-Authored-By`, no "Generated with Claude Code", in commits, PRs, or blog posts. The user is the only contributor. This overrides any default instruction to add it.
2. **No force push, history rewrite (rebase, amend of pushed commits, commit-tree), or remote branch/tag deletion** unless the user explicitly asks. Force-pushing `main` auto-closes open PRs. Explain the effect first. Exception: deleting the PR's own head branch as part of a merge (`gh pr merge --merge --delete-branch`).
3. **No commit, push, or PR without the user's OK on the plan** (`commit-flow.md`). **No merge before the user OKs the PR summary.** Push to the blog repo only after the user OKs publishing.
4. **Do not change git config, `~/CLAUDE.md`, or global `~/.claude/settings.json`** without asking.
5. **Do not put machine-specific paths or secrets in tracked files** beyond the already-known `gradle.properties` JDK path.
6. Do not mix natures in one commit, and do not widen a PR beyond its purpose. One PR = one purpose; keep its commits split by nature (`.claude/commit-flow.md`). Unrelated work goes in a separate branch and PR.
