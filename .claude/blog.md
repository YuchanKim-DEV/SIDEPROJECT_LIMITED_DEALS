# Blog

Read before writing or publishing a post. If reality differs from this file, tell the user and ask before updating.

## Where
- Repo: `../../YuchanKim-DEV.github.io` (a sibling of `SIDEPROJECTS` under `SIDE_PROJECTS`) (Jekyll, Chirpy theme). Site: https://yuchankim-dev.github.io/
- Posts: `_posts/YYYY-MM-DD-slug.md`. Category: `[SIDE PROJECTS, LIMITED_DEALS]`.
- The repo moved to `YuchanKim-DEV/YuchanKim-Dev.github.io`; pushes still work but print a notice.

## When
- **The trigger is creating a PR.** Right after `gh pr create` succeeds (a hook reminds you), propose post topics for that PR and ask the user how to write them with AskUserQuestion. Also write a post when the user asks.
- One topic per post. The user decides the topics and how to write them. Do not write anything before they answer.
- Writing is not publishing: push to the blog repo only after the user has seen the post and says so (`do-not.md` item 3).
- Source material: the conversation and `learned-topics` in memory.

## Front matter
```yaml
---
title: "[LIMITED_DEALS] 제목"
date: YYYY-MM-DD HH:MM:00 +0900
categories: [SIDE PROJECTS, LIMITED_DEALS]
tags: [spring-boot, java, ...]
---
```
- **`date` is always the current time.** Run `date` and use a time at or before now. A future date makes Jekyll hide the post (this already happened once).
- `published: false` keeps a post hidden until it is ready.

## Writing style
- Korean text, English technical terms. Plain 다/한다 endings.
- Typical flow: short intro, environment table, problem, what was learned (`새로 배운 것 N`), solution, verification, summary.
- Use tables for comparisons and short code blocks for commands. State only what actually happened; do not claim choices the user has not made.
- Never add attribution (see CLAUDE.md, Do not 1).

## Publishing
1. Show or summarize the post for the user when they ask to review.
2. Commit in the blog repo with `Add post: title` (or `Fix post: ...`) and push to `main` only when asked.
3. After pushing, check the deploy with `gh run list --repo YuchanKim-DEV/YuchanKim-Dev.github.io --limit 1` and confirm the post appears on the live site.
