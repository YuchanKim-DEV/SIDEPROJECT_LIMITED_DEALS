#!/usr/bin/env python3
"""Nature: pull request. Title must be `type(scope): subject`; body must follow the PR template headings."""
from _common import *

HEADINGS = ("## 요약", "## 변경 사항", "## 테스트 방법", "## 관련 이슈")


def check(data):
    cmd, cwd = bash_input(data)
    m = re.search(r"\bgh\s+pr\s+(create|edit)\b", cmd or "")
    if not m or not in_project(cmd, cwd):
        return
    tokens = args_after(cmd, r"\bgh\s+pr\s+(?:create|edit)\b", whole=True)
    title = option_value(tokens, "--title", "-t")
    body = option_value(tokens, "--body", "-b")
    if title is not None and not TITLE_RE.match(title.strip()):
        block("pr-guard", f"PR title '{title}' must match 'type(scope): subject' "
              f"(types: {TYPES.replace('|', ', ')}).")
    if body is not None:
        missing = [h for h in HEADINGS if h not in body]
        if missing:
            block("pr-guard", f"PR body must follow .github/pull_request_template.md. Missing: {', '.join(missing)}")


run(check)
