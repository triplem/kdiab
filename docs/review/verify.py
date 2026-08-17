#!/usr/bin/env python3
"""Deliverable-integrity verifier for the kdiab Technology & Domain Review.

Runs the build-and-test verification suite over the docs/review/*.md set so the review
stays internally consistent as findings evolve or are materialized. Dependency-free
(stdlib only); intended to run in CI on docs/review/** changes and locally.

    python3 docs/review/verify.py            # from repo root
    python3 verify.py                        # from docs/review/

Exit code 0 = all checks pass; 1 = one or more checks failed (details on stderr).

Scope note: this encodes the DURABLE deliverable-integrity checks only. The one-time
"recommendations-only" authoring invariant (no code changed during the review run) is NOT
a recurring gate — future PRs that implement a finding legitimately change kdiab source.
"""
from __future__ import annotations
import re
import sys
from collections import Counter
from pathlib import Path

REVIEW = Path(__file__).resolve().parent

# area code -> (theme file stem, expected contiguous finding count)
AREAS = {
    "CLIN": ("clinical-safety", 14),
    "DATA": ("data-model", 5),
    "SEC": ("security", 7),
    "DEBT": ("tech-debt", 9),
    "MOD": ("modernization", 5),
}
THEME_FILES = [v[0] for v in AREAS.values()]
DELIVERABLES = ["README", "CONVENTIONS", *THEME_FILES, "BACKLOG", "QUICK-WINS", "ROADMAP"]

# Positive-verdict findings (recorded for trust; NOT actionable backlog items).
POSITIVES = {
    "FIND-CLIN-007", "FIND-CLIN-008", "FIND-CLIN-009", "FIND-CLIN-011", "FIND-CLIN-012",
    "FIND-SEC-003", "FIND-DEBT-002", "FIND-MOD-001", "FIND-MOD-005",
}

# Mandated Finding-Record fields (label may carry a parenthetical qualifier before the colon,
# e.g. "Recommendation (rewrite):" on a C-1 rewrite finding).
MANDATED = {
    "Severity": r"Severity:",
    "Evidence": r"Evidence:",
    "Recommendation": r"Recommendation(?:\s*\([^)]*\))?:",
    "Patient-safety impact": r"Patient-safety impact:",
}

SECRET_PATTERNS = (
    r"BEGIN [A-Z ]*PRIVATE KEY", r"bearer [A-Za-z0-9._-]{20,}",
    r"ghp_[A-Za-z0-9]{20,}", r"xox[baprs]-", r"aws_secret",
)


def _read(stem: str) -> str:
    return (REVIEW / f"{stem}.md").read_text(encoding="utf-8")


def _blocks(stem: str):
    for b in re.split(r"(?=^#### FIND-)", _read(stem), flags=re.M):
        m = re.match(r"^#### (FIND-\S+)", b)
        if m:
            yield m.group(1), b


def check_presence(fail):
    for stem in DELIVERABLES:
        p = REVIEW / f"{stem}.md"
        if not p.exists() or not p.stat().st_size:
            fail(f"presence: missing/empty deliverable {stem}.md")


def check_schema(fail):
    for stem in THEME_FILES:
        for fid, body in _blocks(stem):
            for label, pat in MANDATED.items():
                if not re.search(pat, body):
                    fail(f"schema: {fid} missing mandated field '{label}'")


def check_contiguity(fail):
    for area, (stem, exp) in AREAS.items():
        nums = sorted(int(x) for x in re.findall(rf"^#### FIND-{area}-(\d+)", _read(stem), re.M))
        if nums != list(range(1, exp + 1)):
            fail(f"contiguity: {area} expected 1..{exp}, got {nums}")


def check_severity_discipline(fail):
    # Critical is reserved for clinical/domain findings (ADR-RVW-004).
    for area, (stem, _) in AREAS.items():
        if area == "CLIN":
            continue
        for fid, body in _blocks(stem):
            if re.search(r"Severity:\s*Critical", body):
                fail(f"discipline: non-clinical Critical not allowed: {fid}")


def check_evidence_format(fail):
    # Citations are symbol/key based (ADR-RVW-007) — never a bare line number.
    for stem in THEME_FILES:
        for i, line in enumerate(_read(stem).splitlines(), 1):
            if re.match(r"^- Evidence:.*:\d+`?\s*$", line):
                fail(f"evidence: {stem}.md:{i} line-number citation (use path#symbol)")


def _actionable():
    theme = {fid for stem in THEME_FILES for fid, _ in _blocks(stem)}
    return theme - POSITIVES


def check_backlog_traceability(fail):
    actionable = _actionable()
    ordered = _read("BACKLOG").split("## Positive verdicts")[0]
    rows = set(re.findall(r"^\| \d+ \| (FIND-[A-Z]+-\d+)", ordered, re.M))
    missing = actionable - rows
    if missing:
        fail(f"traceability: actionable findings missing from BACKLOG table: {sorted(missing)}")
    heading = re.search(r"Ordered backlog \((\d+) actionable", _read("BACKLOG"))
    if heading and int(heading.group(1)) != len(rows):
        fail(f"traceability: BACKLOG heading says {heading.group(1)} but table has {len(rows)} rows")


def check_phase_authority(fail):
    # ROADMAP band (single source of truth, ADR-RVW-006) must match backlog Phase and theme Phase.
    band, rmap = None, {}
    for ln in _read("ROADMAP").splitlines():
        h = re.match(r"^## (Near|Mid|Long)", ln)
        if h:
            band = h.group(1)
            continue
        r = re.match(r"^\| (FIND-[A-Z]+-\d+) \|", ln)
        if r and band:
            rmap[r.group(1)] = band
    ordered = _read("BACKLOG").split("## Positive verdicts")[0]
    blmap = dict(re.findall(
        r"^\| \d+ \| (FIND-[A-Z]+-\d+) \|[^|]+\|[^|]+\|[^|]+\| (Near|Mid|Long) \|", ordered, re.M))
    thmap = {}
    for stem in THEME_FILES:
        for fid, body in _blocks(stem):
            p = re.search(r"Phase:\s*(Near|Mid|Long)", body)
            if p:
                thmap[fid] = p.group(1)
    for fid, bl in blmap.items():
        stamps = {x for x in (rmap.get(fid), bl, thmap.get(fid)) if x}
        if len(stamps) > 1:
            fail(f"phase-authority: {fid} drift roadmap={rmap.get(fid)} "
                 f"backlog={bl} theme={thmap.get(fid)} (align to roadmap band)")


def check_dead_links(fail):
    for p in REVIEW.glob("*.md"):
        for tgt in re.findall(r"\]\(\.\/([A-Za-z0-9._-]+\.md)", p.read_text(encoding="utf-8")):
            if not (REVIEW / tgt).exists():
                fail(f"dead-link: {p.name} -> {tgt}")


def check_readme_numbers(fail):
    sev, total = Counter(), 0
    for stem in THEME_FILES:
        for _fid, body in _blocks(stem):
            total += 1
            m = re.search(r"Severity:\s*(Critical|High|Medium|Med|Low)", body)
            if m:
                sev[m.group(1).replace("Med", "Medium")] += 1
    if total != 40:
        fail(f"readme-numbers: expected 40 total findings, got {total}")
    if sev["Critical"] != 0:
        fail(f"readme-numbers: expected 0 Critical, got {sev['Critical']}")
    if sev["High"] != 5:
        fail(f"readme-numbers: expected 5 High, got {sev['High']}")


def check_no_secrets(fail):
    joined = "\n".join(p.read_text(encoding="utf-8") for p in REVIEW.glob("*.md"))
    for pat in SECRET_PATTERNS:
        if re.search(pat, joined, re.I):
            fail(f"secret: pattern '{pat}' found in review docs")


CHECKS = [
    ("presence", check_presence),
    ("schema", check_schema),
    ("contiguity", check_contiguity),
    ("severity-discipline", check_severity_discipline),
    ("evidence-format", check_evidence_format),
    ("backlog-traceability", check_backlog_traceability),
    ("phase-authority", check_phase_authority),
    ("dead-links", check_dead_links),
    ("readme-numbers", check_readme_numbers),
    ("no-secrets", check_no_secrets),
]


def main() -> int:
    failures: list[str] = []
    for name, fn in CHECKS:
        before = len(failures)
        fn(failures.append)
        status = "PASS" if len(failures) == before else "FAIL"
        print(f"[{status}] {name}")
    if failures:
        print("\nFAILURES:", file=sys.stderr)
        for f in failures:
            print(f"  - {f}", file=sys.stderr)
        print(f"\n{len(failures)} check failure(s).", file=sys.stderr)
        return 1
    print(f"\nAll {len(CHECKS)} checks passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
