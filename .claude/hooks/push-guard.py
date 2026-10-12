#!/usr/bin/env python3
"""Nature: push. Block destructive push forms (any repo) and direct pushes to main (this project's repo)."""
from _common import *

PUSH = GIT + r"push\b"


def destructive(a):
    return (
        a in ("-f", "--force", "--mirror", "--delete", "-d")
        or a.startswith(("--force", "+"))
        or re.match(r"^-[a-zA-Z]*f[a-zA-Z]*$", a)  # bundled short flags like -uf
        or re.match(r"^:\S+", a)  # :branch deletes the remote branch
    )


def check(data):
    cmd, cwd = bash_input(data)
    if cmd is None or not re.search(PUSH, cmd):
        return
    args = args_after(cmd, PUSH)
    for a in args:
        if destructive(a):
            block("push-guard", f"Destructive push form '{a}' is not allowed (force, delete, +refspec, :branch). "
                  "Ask the user and explain the effect first.")
    if not in_project(cmd, cwd):
        return
    refspecs = [a for a in args if not a.startswith("-")][1:]  # first positional is the remote
    on_main = current_branch(cmd, cwd) == "main"
    for r in refspecs:
        dst = r.split(":")[-1]
        if dst in ("main", "refs/heads/main") or (dst == "HEAD" and on_main):
            block("push-guard", "Do not push directly to main. Push a work branch and open a PR.")
    if not refspecs and on_main:
        block("push-guard", "You are on main. Do not push main directly; use a work branch and a PR.")


run(check)
