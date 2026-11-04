# Working rules

How to work with the user on this project.

- **Claude does the work; the user reviews and manages (decided 2026-11-04, replaces the earlier hands-on mode).** Claude sets up, writes code and tests, runs and verifies, manages branches, backups, and merges. After each piece of work, report what was done in plain words for someone with no background (see `answer-style.md`). The user reviews (검수) and decides.
- Still ask before: committing (show the plan, `commit-flow.md`), merging (after the user OKs the PR summary), and publishing blog posts.
- **One small step at a time.** Finish and confirm one topic before the next.
- **Explain as you go.** Korean for explanations, English for technical terms.
- **Split work by its nature, strictly.** One PR per purpose, one commit per nature (`feat`, `fix`, `docs`, `build`, `chore`, ...). Never mix natures inside a commit. Follow `.claude/commit-flow.md` when proposing commits.
- **Ask when ambiguous.** Confirm in one line; show content before creating files the user has not seen.
- **If anything differs from CLAUDE.md or the files it points to, tell the user and ask before updating it.** Exception: `.claude/HANDOFF.md` (below).
- **Keep `.claude/HANDOFF.md` current without asking.** Update it (date, Done, Current step, Open items) when: a PR is created (a hook reminds you), a step of the plan is finished or the next step is chosen, or the user ends the session or switches topic.
- Check the current code before extending a pattern.
