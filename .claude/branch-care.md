# Branch care

Cleanup and backups, done by Claude **without asking** (the user asked for this on 2026-10-18). Say what was done in one line each time.

## Cleanup after a merge
When the user says a PR is merged, or `gh pr list` shows it merged:
1. `git checkout main && git pull` (uncommitted files carry over; that is fine).
2. `git fetch --prune origin`
3. Delete local branches whose upstream is gone: `git branch -vv` → for each `[...: gone]` branch, `git branch -d <name>`.
4. Never force-delete a work branch. If `-d` refuses (not fully merged), keep it and tell the user.

Claude merges with `gh pr merge <n> --merge --delete-branch`, which also deletes the remote head branch. Never delete other remote branches yourself (`do-not.md` item 2).

## Backups
**When:** right before anything that could lose or rewrite work: `git stash pop/apply` with possible conflicts, deleting an unmerged branch, switching history (only if the user asked), a large refactor, or any risky operation.

**How (local only):**
```bash
git branch backup/<what>/<YYYY-MM-DD-HHMM>          # backs up the current commit
git branch backup/<what>/<YYYY-MM-DD-HHMM> <ref>    # or another ref
```
Backup branches hold commits only. For uncommitted work, use `git stash push -m "<what>"` instead (and remember to restore it).

**Keep at most 10.** After creating one, list them oldest first by the date in the name and force-delete the extras (`-D` is allowed only for `backup/*`; `branch-guard.py` enforces it):
```bash
git for-each-ref --format='%(refname:short)' refs/heads/backup/ | awk -F/ '{print $NF, $0}' | sort | awk '{print $2}'
git branch -D backup/<oldest>/<date>
```

**Going back:** tell the user the backup name and the command, e.g. `git switch -c chore/restore-<what>/<YYYY-MM-DD> backup/<what>/<YYYY-MM-DD-HHMM>`. Never `git reset --hard` (denied).
