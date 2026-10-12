#!/usr/bin/env python3
"""Nature: progress notes. PostToolUse hook for Bash.

After a PR was created in this project's repo, tell Claude to refresh `.claude/HANDOFF.md`
(a hook cannot write prose itself; it injects an instruction into Claude's context).
"""
import json
import re
import sys

from _common import *

MESSAGE = (
    "A PR was just created. Update .claude/HANDOFF.md now without asking: Last updated date, "
    "Done, Current step, Open items (and the next step). Keep it short."
)


def check(data):
    cmd, cwd = bash_input(data)
    if cmd is None or not re.search(r"\bgh\s+pr\s+create\b", cmd) or not in_project(cmd, cwd):
        return
    if not re.search(r"/pull/\d+", json.dumps(data.get("tool_response", ""))):
        return  # the PR was not actually created
    print(json.dumps({"hookSpecificOutput": {"hookEventName": "PostToolUse", "additionalContext": MESSAGE}}))


run(check)
