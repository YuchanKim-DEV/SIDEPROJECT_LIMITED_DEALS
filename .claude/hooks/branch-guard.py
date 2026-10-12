#!/usr/bin/env python3
"""Nature: branch creation. In this project's repo the name must be `type/description/YYYY-MM-DD`."""
from datetime import datetime

from _common import *

NAME_RE = re.compile(rf"^({TYPES})/[a-z0-9]+(-[a-z0-9]+)*/(\d{{4}}-\d{{2}}-\d{{2}})$")


def new_branch_name(cmd):
    """Name of a branch being created, or None."""
    args = args_after(cmd, r"\bgit\s+(?:-C\s+\S+\s+)?(?:checkout|switch)\b")
    if re.search(r"\bgit\s+(?:-C\s+\S+\s+)?(?:checkout|switch)\b", cmd):
        name = option_value(args, "-b", "-B", "-c", "-C", "--create", "--force-create")
        if name:
            return name
    args = args_after(cmd, GIT + r"branch\b")
    if re.search(GIT + r"branch\b", cmd) and args and not any(a.startswith("-") for a in args):
        return args[0]  # `git branch <name> [start]`
    return None


def check(data):
    cmd, cwd = bash_input(data)
    if cmd is None or not in_project(cmd, cwd):
        return
    name = new_branch_name(cmd)
    if name is None:
        return
    m = NAME_RE.match(name)
    ok = bool(m)
    if ok:
        try:
            datetime.strptime(m.group(3), "%Y-%m-%d")
        except ValueError:
            ok = False
    if not ok:
        block("branch-guard", f"Branch name '{name}' must be 'type/description/YYYY-MM-DD' "
              f"(types: {TYPES.replace('|', ', ')}; description in lowercase kebab-case; a real date).")


run(check)
