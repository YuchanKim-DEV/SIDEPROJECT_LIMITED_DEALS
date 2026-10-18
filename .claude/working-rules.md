# Working rules

How to work with the user on this project.

- **The user does the hands-on work (decided 2026-10-12).** For the project itself (dependencies, config, Docker Compose, code, tests), do not set things up or write the code automatically. Explain the concept, then give the **complete, ready-to-type steps** (full commands and full file contents); the user types and runs them. No hint quizzes, no "find it yourself" (the user said: "you write it here and I type it myself"). After they run it, check the result (read-only) and review. Docs, Claude settings, blog posts, and the commit/PR flow stay with Claude as before.
- **One small step at a time.** Finish and confirm one topic before the next.
- **Explain as you go.** Korean for explanations, English for technical terms.
- **Split work by its nature, strictly.** One PR per purpose, one commit per nature (`feat`, `fix`, `docs`, `build`, `chore`, ...). Never mix natures inside a commit. Follow `.claude/commit-flow.md` when proposing commits.
- **Ask when ambiguous.** Confirm in one line; show content before creating files the user has not seen.
- **If anything differs from CLAUDE.md or the files it points to, tell the user and ask before updating it.** Exception: `.claude/HANDOFF.md` (below).
- **Keep `.claude/HANDOFF.md` current without asking.** Update it (date, Done, Current step, Open items) when: a PR is created (a hook reminds you), a step of the plan is finished or the next step is chosen, or the user ends the session or switches topic.
- Check the current code before extending a pattern.
