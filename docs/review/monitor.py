#!/usr/bin/env python3
"""Review deliverable currency + progress monitor (Observability, stage 4.4).

Advisory, dependency-free (stdlib only). Complements verify.py:
  * verify.py  -> deliverable INTEGRITY gate (blocking, on every PR).
  * monitor.py -> deliverable CURRENCY + PROGRESS (advisory, scheduled).

Currency: findings cite `path/File.kt#symbol` against a pinned baseline commit
(the codekb snapshot). As `main` advances, a cited file may be deleted (anchor
broken) or modified (finding may be stale and needs re-verification, US-5).

Progress: reports the backlog headline (from BACKLOG.md) and, when `gh` is
available and issues exist, the open/closed counts of `review`-labelled issues
(the epic burn-down). Never fails the job on drift — it reports; the maintainer
re-verifies just-in-time before pulling a finding (stage 4.4 Q3=A).

Exit code is always 0 (advisory). Drift is signalled via the `drift=` line and,
under GitHub Actions, the `drift` step output — the workflow decides what to do.
"""
from __future__ import annotations

import os
import re
import subprocess
import sys
from pathlib import Path

REVIEW_DIR = Path(__file__).resolve().parent
REPO_ROOT = REVIEW_DIR.parent.parent
BASELINE = os.environ.get("REVIEW_BASELINE_SHA", "d6c8866b")  # codekb snapshot

# Backtick-wrapped `path/File.ext#symbol`; only resolvable full paths (skip the
# `.../` abbreviated forms the docs use for readability).
CITATION = re.compile(
    r"`([A-Za-z0-9_][A-Za-z0-9_./-]+\.(?:kt|kts|ts|tsx|sql|ya?ml|json))#[^`]+`"
)


def git(*args: str) -> tuple[int, str]:
    try:
        p = subprocess.run(
            ["git", "-C", str(REPO_ROOT), *args],
            capture_output=True, text=True, check=False,
        )
        return p.returncode, (p.stdout or "").strip()
    except FileNotFoundError:
        return 127, ""


def is_trackable(path: str) -> bool:
    """A real repo anchor: has a directory and its first segment is a top-level
    repo dir. Filters out abbreviated (`.../`), example (`path/File.kt`), and
    bare-filename (`Foo.kt`) citations that are not resolvable full paths."""
    if "..." in path or "/" not in path:
        return False
    first = path.split("/", 1)[0]
    return (REPO_ROOT / first).is_dir()


def cited_files() -> set[str]:
    files: set[str] = set()
    for md in sorted(REVIEW_DIR.glob("*.md")):
        for m in CITATION.finditer(md.read_text(encoding="utf-8")):
            path = m.group(1)
            if is_trackable(path):
                files.add(path)
    return files


def classify(files: set[str]) -> tuple[list[str], list[str], list[str]]:
    """Return (missing, changed, fresh) relative to BASELINE."""
    missing, changed, fresh = [], [], []
    for path in sorted(files):
        if not (REPO_ROOT / path).exists():
            missing.append(path)
            continue
        rc, out = git("log", "--oneline", f"{BASELINE}..HEAD", "--", path)
        if rc != 0:
            # baseline unreachable (shallow clone) — cannot judge; treat as fresh
            fresh.append(path)
        elif out:
            changed.append(path)
        else:
            fresh.append(path)
    return missing, changed, fresh


def backlog_headline() -> str:
    backlog = REVIEW_DIR / "BACKLOG.md"
    if not backlog.exists():
        return "BACKLOG.md not found"
    m = re.search(r"(\d+)\s+actionable findings", backlog.read_text(encoding="utf-8"))
    return f"{m.group(1)} actionable findings" if m else "headline count not found"


def issue_burndown() -> str | None:
    """Open/closed counts of `review` issues, if `gh` is available."""
    try:
        rc = subprocess.run(["gh", "--version"], capture_output=True, check=False).returncode
    except FileNotFoundError:
        return None
    if rc != 0:
        return None
    def count(state: str) -> str:
        p = subprocess.run(
            ["gh", "issue", "list", "--label", "review", "--state", state,
             "--limit", "200", "--json", "number"],
            capture_output=True, text=True, check=False,
        )
        if p.returncode != 0:
            return "?"
        return str(p.stdout.count('"number"'))
    return f"open={count('open')} closed={count('closed')}"


def main() -> int:
    files = cited_files()
    missing, changed, fresh = classify(files)
    drift = bool(missing or changed)

    lines: list[str] = []
    lines.append("# Review Deliverable Monitor")
    lines.append("")
    lines.append(f"- Baseline commit: `{BASELINE}`")
    lines.append(f"- Resolvable cited files: {len(files)}  "
                 f"(fresh={len(fresh)}, changed={len(changed)}, missing={len(missing)})")
    lines.append(f"- Backlog headline: {backlog_headline()}")
    bd = issue_burndown()
    if bd is not None:
        lines.append(f"- Review-issue burn-down: {bd}")
    lines.append("")

    if missing:
        lines.append("## BROKEN anchors (cited file deleted — finding likely stale)")
        lines += [f"- `{p}`" for p in missing]
        lines.append("")
    if changed:
        lines.append(f"## CHANGED since `{BASELINE}` (re-verify before acting — US-5)")
        lines += [f"- `{p}`" for p in changed]
        lines.append("")
    if not drift:
        lines.append("All cited files are unchanged since the baseline. No currency drift.")

    report = "\n".join(lines)
    print(report)

    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as fh:
            fh.write(report + "\n")
    out = os.environ.get("GITHUB_OUTPUT")
    if out:
        with open(out, "a", encoding="utf-8") as fh:
            fh.write(f"drift={'true' if drift else 'false'}\n")

    return 0


if __name__ == "__main__":
    sys.exit(main())
