#!/usr/bin/env python3
"""Nature: blog writing. For files in `_posts/`: no future date (Jekyll hides the post) and no Claude attribution."""
import os
from datetime import datetime, timedelta, timezone

from _common import *

DATE_RE = re.compile(r"^date:\s*(\d{4}-\d{2}-\d{2})\s+(\d{2}:\d{2})(?::(\d{2}))?\s*([+-]\d{4})?", re.MULTILINE)
KST = timezone(timedelta(hours=9))


def texts(tool_input):
    if "content" in tool_input:
        yield tool_input["content"]
    if "new_string" in tool_input:
        yield tool_input["new_string"]
    for e in tool_input.get("edits", []):
        yield e.get("new_string", "")


def check(data):
    if data.get("tool_name") not in ("Write", "Edit", "MultiEdit"):
        return
    ti = data.get("tool_input", {})
    path = ti.get("file_path", "")
    if "/_posts/" not in path or not path.endswith(".md"):
        return
    now = datetime.now(KST)
    m = re.match(r"(\d{4}-\d{2}-\d{2})-", os.path.basename(path))
    if m and m.group(1) > now.strftime("%Y-%m-%d"):
        block("blog-guard", f"File name date {m.group(1)} is in the future. Use today's date (run `date`).")
    for t in texts(ti):
        if ATTRIBUTION_RE.search(t):
            block("blog-guard", "Claude attribution is not allowed in blog posts.")
        for d, hm, sec, off in DATE_RE.findall(t):
            tz = timezone(timedelta(hours=int(off[:3]), minutes=int(off[0] + off[3:5]))) if off else KST
            when = datetime.strptime(f"{d} {hm}:{sec or '00'}", "%Y-%m-%d %H:%M:%S").replace(tzinfo=tz)
            if when > now:
                block("blog-guard", f"Post date {when:%Y-%m-%d %H:%M %z} is in the future; Jekyll will hide the post. "
                      f"Use a time at or before now ({now:%Y-%m-%d %H:%M %z}); run `date`.")


run(check)
