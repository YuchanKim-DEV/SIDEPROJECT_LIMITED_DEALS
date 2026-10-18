#!/usr/bin/env python3
"""Nature: branches. In this project's repo:
- a new branch is `type/description/YYYY-MM-DD`, or a backup `backup/description/YYYY-MM-DD-HHMM`;
- force-deleting (`git branch -D`) is allowed only for `backup/*` branches (work branches use `-d`)."""
from datetime import datetime

from _common import *

WORK_RE = re.compile(rf"^({TYPES})/[a-z0-9]+(-[a-z0-9]+)*/(\d{{4}}-\d{{2}}-\d{{2}})$")
BACKUP_RE = re.compile(r"^backup/[a-z0-9]+(?:-[a-z0-9]+)*/(\d{4}-\d{2}-\d{2})-(\d{4})$")
BRANCH = GIT + r"branch\b"


def new_branch_name(cmd):
    """Name of a branch being created, or None."""
    sw = r"\bgit\s+(?:-C\s+\S+\s+)?(?:checkout|switch)\b"
    if re.search(sw, cmd):
        name = option_value(args_after(cmd, sw), "-b", "-B", "-c", "-C", "--create", "--force-create")
        if name:
            return name
    args = args_after(cmd, BRANCH)
    if re.search(BRANCH, cmd) and args and not any(a.startswith("-") for a in args):
        return args[0]  # `git branch <name> [start]`
    return None


def valid_name(name):
    m = WORK_RE.match(name)
    if m:
        date = m.group(3)
    else:
        m = BACKUP_RE.match(name)
        if not m:
            return False
        date = f"{m.group(1)} {m.group(2)}"
    try:
        datetime.strptime(date, "%Y-%m-%d %H%M" if " " in date else "%Y-%m-%d")
        return True
    except ValueError:
        return False


def check(data):
    cmd, cwd = bash_input(data)
    if cmd is None or not in_project(cmd, cwd):
        return

    # force delete: only backup branches
    if re.search(BRANCH, cmd):
        args = args_after(cmd, BRANCH)
        force = "-D" in args or ("--delete" in args and "--force" in args) or any(
            re.match(r"^-[a-zA-Z]*D[a-zA-Z]*$", a) or a in ("-df", "-fd") for a in args)
        if force:
            for name in (a for a in args if not a.startswith("-")):
                if not name.startswith("backup/"):
                    block("branch-guard", f"Force delete is only for backup/* branches, not '{name}'. "
                          "Use `git branch -d` for merged work branches, or ask the user.")

    name = new_branch_name(cmd)
    if name is not None and not valid_name(name):
        block("branch-guard", f"Branch name '{name}' must be 'type/description/YYYY-MM-DD' "
              f"(types: {TYPES.replace('|', ', ')}) or 'backup/description/YYYY-MM-DD-HHMM' "
              "(lowercase kebab-case description, a real date).")


run(check)
