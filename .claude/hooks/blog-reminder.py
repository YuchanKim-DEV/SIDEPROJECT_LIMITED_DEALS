#!/usr/bin/env python3
"""Nature: blog. PostToolUse hook for Bash.

After a PR was created in this project's repo, remind Claude that this is the moment to propose
blog posts (see .claude/blog.md). A hook cannot write posts; it injects an instruction into Claude's context.
"""
import json
import re

from _common import *

MESSAGE = (
    "A PR was just created, which is the trigger for blog posts. Follow .claude/blog.md: propose post topics "
    "for this PR (one topic per post; use the PR and the conversation; check `learned-topics` in memory) and ask "
    "the user how to write them with AskUserQuestion. Do not write or publish anything before they answer."
)


def check(data):
    cmd, cwd = bash_input(data)
    if cmd is None or not re.search(r"\bgh\s+pr\s+create\b", cmd) or not in_project(cmd, cwd):
        return
    if not re.search(r"/pull/\d+", json.dumps(data.get("tool_response", ""))):
        return  # the PR was not actually created
    print(json.dumps({"hookSpecificOutput": {"hookEventName": "PostToolUse", "additionalContext": MESSAGE}}))


run(check)
