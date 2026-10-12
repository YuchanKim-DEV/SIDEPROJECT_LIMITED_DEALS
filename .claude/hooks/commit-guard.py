#!/usr/bin/env python3
"""Nature: commit. In this project's repo: subject must be `type(scope): subject`, and never commit on main.
The blog repo (own convention: `Add post:` / `Fix post:`, commits on main) is not checked."""
from _common import *


def subject_of(cmd):
    """First line of the commit message, or None if it cannot be determined."""
    m = re.search(r"<<-?\s*['\"]?(\w+)['\"]?\n(.*?)\n\s*\1", cmd, re.DOTALL)
    if m:
        lines = [l for l in m.group(2).splitlines() if l.strip()]
        return lines[0].strip() if lines else None
    msg = option_value(args_after(cmd, GIT + r"commit\b", whole=True), "-m", "--message")
    if msg is None:
        return None
    lines = [l for l in msg.splitlines() if l.strip()]
    return lines[0].strip() if lines else None


def check(data):
    cmd, cwd = bash_input(data)
    if cmd is None or not re.search(GIT + r"commit\b", cmd) or not in_project(cmd, cwd):
        return
    subject = subject_of(cmd)
    if subject is not None and not TITLE_RE.match(subject):
        block("commit-guard", f"Commit subject '{subject}' does not match 'type(scope): subject' "
              f"(types: {TYPES.replace('|', ', ')}). See docs/github-convention.md.")
    creates_branch = re.search(r"\bgit\s+(?:checkout\s+-[bB]|switch\s+-[cC])\b", cmd)
    if not creates_branch and current_branch(cmd, cwd) == "main":
        block("commit-guard", "Do not commit on main. Create a work branch first: type/description/YYYY-MM-DD.")


run(check)
