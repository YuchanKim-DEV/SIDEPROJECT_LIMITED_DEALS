"""Shared helpers for the PreToolUse hooks in this folder.

Protocol: the hook JSON arrives on stdin. Exit 0 = allow, exit 2 = block
(stderr is sent back to Claude). A bug in a hook must never break the session,
so run() turns unexpected errors into "allow".
"""
import json
import os
import re
import shlex
import subprocess
import sys

GIT = r"\bgit\s+(?:-C\s+\S+\s+)?"
TYPES = "feat|fix|hotfix|changes|refactor|test|docs|chore|build"
TITLE_RE = re.compile(rf"^({TYPES})(\([^)]+\))?: \S.*$")
ATTRIBUTION_RE = re.compile(
    r"co-authored-by|generated with \[?claude|🤖 generated|noreply@anthropic\.com", re.IGNORECASE
)


def block(name, msg):
    print(f"[{name}] {msg}", file=sys.stderr)
    sys.exit(2)


def run(check):
    try:
        check(json.load(sys.stdin))
    except SystemExit:
        raise
    except Exception as e:
        print(f"[hook] error ignored: {e}", file=sys.stderr)
        sys.exit(0)


def bash_input(data):
    """Return (command, cwd) for Bash calls, else (None, None)."""
    if data.get("tool_name") != "Bash":
        return None, None
    return data.get("tool_input", {}).get("command", ""), data.get("cwd") or os.getcwd()


def project_dir(cwd):
    return os.path.realpath(os.environ.get("CLAUDE_PROJECT_DIR", cwd))


def target_dir(cmd, cwd):
    """Directory a git command runs in: `git -C path`, else the first `cd path`, else cwd."""
    m = re.search(r"\bgit\s+-C\s+(\"[^\"]+\"|'[^']+'|\S+)", cmd) or re.search(
        r"\bcd\s+(\"[^\"]+\"|'[^']+'|\S+)", cmd
    )
    if not m:
        return cwd
    path = os.path.expanduser(m.group(1).strip("'\""))
    return path if os.path.isabs(path) else os.path.join(cwd, path)


def git_out(directory, *args):
    try:
        r = subprocess.run(["git", "-C", directory, *args], capture_output=True, text=True, timeout=5)
        # only trailing newlines: a leading space is meaningful in `git status --porcelain` lines
        return r.stdout.rstrip("\n") if r.returncode == 0 else ""
    except Exception:
        return ""


def in_project(cmd, cwd):
    """True when the git command targets this project's repository (not the blog repo)."""
    top = git_out(target_dir(cmd, cwd), "rev-parse", "--show-toplevel")
    return bool(top) and os.path.realpath(top) == project_dir(cwd)


def current_branch(cmd, cwd):
    return git_out(target_dir(cmd, cwd), "branch", "--show-current")


def args_after(cmd, pattern, whole=False):
    """Tokens after the first match of `pattern`, up to the next ; & | or newline (or to the end if whole)."""
    m = re.search(pattern, cmd)
    if not m:
        return []
    rest = cmd[m.end():]
    seg = rest if whole else re.split(r"[;&|\n]", rest)[0]
    try:
        return shlex.split(seg)
    except ValueError:
        return seg.split()


def option_value(tokens, *names):
    for i, t in enumerate(tokens):
        for n in names:
            if t == n and i + 1 < len(tokens):
                return tokens[i + 1]
            if t.startswith(n + "=") and n.startswith("--"):
                return t.split("=", 1)[1]
    return None
