#!/usr/bin/env python3
"""Nature: workflow. UserPromptSubmit hook.

When this project's working tree has uncommitted changes, remind Claude (via added context)
to follow .claude/commit-flow.md: at a checkpoint or when the user switches tasks, announce the
commit plan and ask with AskUserQuestion before moving on. Silent when the tree is clean.
"""
import json

from _common import *

MAX_FILES = 8


def check(data):
    project = project_dir(data.get("cwd") or os.getcwd())
    lines = git_out(project, "status", "--porcelain", "-uall").splitlines()
    if not lines:
        return
    # HANDOFF.md is refreshed automatically all the time; it rides along with the next work PR.
    names = [n for n in (l[3:].split(" -> ")[-1] for l in lines) if n != ".claude/HANDOFF.md"]
    if not names:
        return
    shown = ", ".join(names[:MAX_FILES]) + (f", ... (+{len(names) - MAX_FILES})" if len(names) > MAX_FILES else "")
    msg = (f"[uncommitted-reminder] {len(names)} uncommitted file(s): {shown}. "
           "If this prompt starts a different task or the work reached a checkpoint, follow "
           ".claude/commit-flow.md: announce the commit plan (branch, commits split by nature, PR) and ask "
           "with AskUserQuestion before continuing. Otherwise ignore this note.")
    print(json.dumps({"hookSpecificOutput": {"hookEventName": "UserPromptSubmit", "additionalContext": msg}}))


run(check)
