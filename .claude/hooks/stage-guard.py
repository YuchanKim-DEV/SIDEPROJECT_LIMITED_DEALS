#!/usr/bin/env python3
"""Nature: staging. Block secrets and force-adding ignored files in `git add` (any repo)."""
import fnmatch
import os

from _common import *

SENSITIVE = [".env", ".env.*", "*.pem", "*.key", "*.p12", "*.pfx", "*.keystore",
             "id_rsa*", "id_ed25519*", "credentials*", "settings.local.json"]
SAFE = [".env.example", ".env.sample", ".env.template"]
BULK = {".", "-A", "--all", "-u", "--update", "*"}


def sensitive(path):
    base = os.path.basename(path.rstrip("/"))
    return base not in SAFE and any(fnmatch.fnmatch(base, p) for p in SENSITIVE)


def check(data):
    cmd, cwd = bash_input(data)
    if cmd is None or not re.search(GIT + r"add\b", cmd):
        return
    args = args_after(cmd, GIT + r"add\b")
    if any(a in ("-f", "--force") or re.match(r"^-[a-zA-Z]*f[a-zA-Z]*$", a) for a in args):
        block("stage-guard", "`git add -f` would add files that .gitignore excludes. Ask the user first.")
    for a in args:
        if not a.startswith("-") and sensitive(a):
            block("stage-guard", f"'{a}' looks like a secret or personal file. Do not stage it.")
    if any(a in BULK for a in args):
        status = git_out(target_dir(cmd, cwd), "status", "--porcelain", "-uall")
        for line in status.splitlines():
            if sensitive(line[3:].split(" -> ")[-1]):
                block("stage-guard", f"A bulk add would include '{line[3:]}', which looks like a secret or "
                      "personal file. Add files by name instead.")


run(check)
