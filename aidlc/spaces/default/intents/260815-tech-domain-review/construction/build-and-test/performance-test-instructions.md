# Performance Test Instructions — Deliverable Navigability & Scan-Time

> **"Performance" for a documentation deliverable is fitness-for-use, not latency.** The review's
> performance-shaped NFR is **NFR-4 (solo-maintainer navigability)**: one maintainer must be able to go
> from "open the review" to "know the single next thing to do" in seconds, without reading all ten
> docs. There is no runtime, no load test, no throughput/percentile target — the "user" is one reader
> and the metric is time-to-first-action. Driven by the U7/U8/U9 navigation contract in the
> `code-generation-plan.md` doc-set contract and `code-summary.md`.

## What "load" means here

The deliverable is read, not executed. The equivalent of a load profile is **reader task time**:

| Reader task (NFR-4) | Target | How measured |
|---|---|---|
| Find "the one ordered list to work from" | ≤ 1 hop from README | README reading-guide row → BACKLOG link resolves |
| Find "what to do this week" | ≤ 1 hop from README | README → QUICK-WINS link resolves |
| Find the phased sequence | ≤ 1 hop from README | README → ROADMAP link resolves |
| Trace any backlog row to full evidence | ≤ 1 hop | BACKLOG row references the owning theme doc |
| Understand a finding's schema/scales | ≤ 1 hop | any doc → CONVENTIONS link |

## Test 1 — navigation graph is fully connected (no dead links)

Every intra-set Markdown link (`./*.md`) must resolve to an existing deliverable file.

```bash
python3 - <<'PY'
import re,pathlib
root=pathlib.Path('docs/review'); bad=0
for f in root.glob('*.md'):
    for tgt in re.findall(r'\]\(\.\/([A-Za-z0-9._-]+\.md)',f.read_text()):
        if not (root/tgt).exists(): print(f"DEAD LINK {f.name} -> {tgt}"); bad+=1
print("PASS — navigation graph fully connected" if not bad else f"{bad} dead link(s)")
PY
```

Expected: `PASS`.

## Test 2 — single entry point resolves the three primary reader tasks

README must link BACKLOG (the one ordered list), QUICK-WINS (this week), and ROADMAP (the sequence).

```bash
r=docs/review/README.md
for t in BACKLOG.md QUICK-WINS.md ROADMAP.md CONVENTIONS.md; do
  grep -q "($t)\|(\./$t)" "$r" && echo "OK README -> $t" || echo "FAIL README missing -> $t"
done
```

Expected: all `OK`.

## Test 3 — scan-time proxy: document size stays skimmable

A solo-maintainer deliverable degrades if any single doc becomes a wall of text. Advisory soft budget:
no deliverable should dwarf the set. Report line counts; flag any theme/index doc > ~400 lines for a
human skim-review (not a hard fail).

```bash
wc -l docs/review/*.md | sort -n
awk 'END{}' /dev/null; for f in docs/review/*.md; do n=$(wc -l < "$f"); \
  [ "$n" -gt 400 ] && echo "REVIEW (long) $f=$n lines"; done; echo "size scan done"
```

Expected: all deliverables comfortably under the soft budget (the largest, `clinical-safety.md`, is the
richest theme and is expected to be the longest); no `REVIEW (long)` lines.

## Test 4 — single ordered list (no competing priority orders)

NFR-3 / FR-1.4 require exactly one authoritative ordering (the BACKLOG). QUICK-WINS and ROADMAP are
*views* of it, not competing lists — each must state it is a filtered/derived view.

```bash
grep -qiE 'filtered view|subset of the .?\[?backlog|references them, it does not duplicate' \
  docs/review/QUICK-WINS.md && echo "OK QUICK-WINS declares itself a view" || echo "CHECK QUICK-WINS"
grep -qiE 'single source of truth|sequenced|the backlog' docs/review/ROADMAP.md \
  && echo "OK ROADMAP declares itself a sequence of the backlog" || echo "CHECK ROADMAP"
```

Expected: both `OK`.

## Acceptance

NFR-4 is met when Tests 1–2 pass (connected graph, single entry point resolves all reader tasks),
Test 4 passes (one authoritative order), and Test 3 shows no doc has grown beyond a skimmable size.
These are advisory quality signals, not a runtime gate.
