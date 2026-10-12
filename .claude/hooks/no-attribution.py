#!/usr/bin/env python3
"""Nature: authorship. Block Claude attribution in commits and PRs (any repo)."""
from _common import *


def check(data):
    cmd, _ = bash_input(data)
    if cmd is None:
        return
    writes_text = re.search(GIT + r"commit\b", cmd) or re.search(r"\bgh\s+pr\s+(create|edit)\b", cmd)
    if writes_text and ATTRIBUTION_RE.search(cmd):
        block("no-attribution", "Claude attribution (Co-Authored-By / Generated with Claude Code) is not allowed. "
              "The user is the only contributor. Remove it and retry.")


run(check)
