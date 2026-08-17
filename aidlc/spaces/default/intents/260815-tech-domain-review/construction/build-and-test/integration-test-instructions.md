# Integration Test Instructions — Cross-Document Traceability

> **Integration-level = the deliverable set behaving as one coherent system.** A finding flows theme
> doc → BACKLOG → ROADMAP → QUICK-WINS → queued GitHub issues; these checks assert the flow is
> lossless and non-contradictory. This is where the **FIND-SEC-002 omission** was caught (a Medium
> security finding present in `security.md` but dropped from BACKLOG/ROADMAP/queued-issues). Driven by
> the finding→deliverable pipeline in the per-unit `code-generation-plan.md` / `code-summary.md`.

## Framework & how to run

Cross-document set-comparison in `python3`, run from the repo root. The "system boundary" under test
is the set of `docs/review/*.md` files. Each test compares two documents' finding sets and fails on any
asymmetry.

## Test 1 — every actionable theme finding appears in the BACKLOG ordered table

The master traceability check. `actionable = all 39 findings − 9 positive verdicts (CLIN-007/008/009,
CLIN-011/012, SEC-003, DEBT-002, MOD-001/005) = 30`. Every one must be a row in the BACKLOG ordered
table, and the table's row count must equal the "30 actionable findings" heading claim.

```bash
python3 - <<'PY'
import re,pathlib
theme=set()
for area,fn in [('CLIN','clinical-safety'),('DATA','data-model'),('SEC','security'),
                ('DEBT','tech-debt'),('MOD','modernization')]:
    theme|=set(re.findall(rf'^#### (FIND-{area}-\d+)',pathlib.Path(f'docs/review/{fn}.md').read_text(),re.M))
positives={'FIND-CLIN-007','FIND-CLIN-008','FIND-CLIN-009','FIND-CLIN-011','FIND-CLIN-012',
           'FIND-SEC-003','FIND-DEBT-002','FIND-MOD-001','FIND-MOD-005'}
actionable=theme-positives
ordered=pathlib.Path('docs/review/BACKLOG.md').read_text().split('## Positive verdicts')[0]
rows=set(re.findall(r'^\| \d+ \| (FIND-[A-Z]+-\d+)',ordered,re.M))
miss=actionable-rows
print(f"actionable={len(actionable)} backlog-rows={len(rows)}")
print("PASS — all actionable findings in backlog" if not miss else f"DEFECT missing: {sorted(miss)}")
PY
```

Expected: `actionable=30 backlog-rows=30` → `PASS`.

## Test 2 — BACKLOG ⇄ ROADMAP phase-authority (no drift)

ADR-RVW-006: the roadmap band is the single source of truth for a finding's `roadmap-phase`; the
BACKLOG stamps the same value. Every actionable finding must appear in exactly one roadmap band, and
its BACKLOG `Phase` column must equal its roadmap band.

```bash
python3 - <<'PY'
import re,pathlib
rm=pathlib.Path('docs/review/ROADMAP.md').read_text()
bands={}
for band in ['Near','Mid','Long']:
    seg=re.split(rf'## {band}',rm)[1].split('\n## ')[0] if f'## {band}' in rm else ''
    for fid in re.findall(r'(FIND-[A-Z]+-\d+)',seg): bands[fid]=band
bl=pathlib.Path('docs/review/BACKLOG.md').read_text().split('## Positive verdicts')[0]
bad=0
for row in re.findall(r'^\| \d+ \| (FIND-[A-Z]+-\d+) \|[^|]+\|[^|]+\|[^|]+\| (Near|Mid|Long) \|',bl,re.M):
    fid,phase=row
    if bands.get(fid)!=phase: print(f"DRIFT {fid}: backlog={phase} roadmap={bands.get(fid)}"); bad+=1
print("PASS — backlog phase == roadmap band for all rows" if not bad else f"{bad} drift(s)")
PY
```

Expected: `PASS`.

## Test 3 — QUICK-WINS ⊆ BACKLOG and effort = S

Per FR-D.2, quick-wins are the `effort=S` subset. Every quick-win must be a backlog finding, and none
may be effort M/L.

```bash
python3 - <<'PY'
import re,pathlib
bl=pathlib.Path('docs/review/BACKLOG.md').read_text()
eff={m[0]:m[1] for m in re.findall(r'\| (FIND-[A-Z]+-\d+) \|[^|]+\|[^|]+\| ([SML]) \|',bl)}
qw=set(re.findall(r'(FIND-[A-Z]+-\d+)',pathlib.Path('docs/review/QUICK-WINS.md').read_text()))
# exclude the "Not quick" contrast list items by checking they are labelled S in backlog
bad=[q for q in qw if eff.get(q) and eff[q]!='S']
missing=[q for q in qw if q not in eff]
print("effort map sample:",dict(list(eff.items())[:3]))
print("non-S quick-wins (should be only the explicit 'Not quick' contrast items):",bad or "none")
print("quick-wins not in backlog:",missing or "none")
PY
```

Expected: the only non-`S` IDs are the ones QUICK-WINS explicitly lists under **"Not quick (do not
attempt as a burst)"** as a contrast (FIND-CLIN-001 M, FIND-CLIN-014 L, FIND-SEC-004 L, FIND-DEBT-005
M, FIND-MOD-002 L); the true quick-win rows are all effort=S.

## Test 4 — README headline numbers match the deliverables

README claims: **30 actionable, 0 Critical, 5 High, 9 positive verdicts, 39 total**. Assert against the
computed tallies.

```bash
python3 - <<'PY'
import re,pathlib
from collections import Counter
sev=Counter(); total=0
for fn in ['clinical-safety','data-model','security','tech-debt','modernization']:
    for b in re.split(r'(?=^#### FIND-)',pathlib.Path(f'docs/review/{fn}.md').read_text(),flags=re.M):
        if not re.match(r'^#### FIND-',b): continue
        total+=1
        m=re.search(r'Severity:\s*(Critical|High|Medium|Med|Low)',b)
        if m: sev[m.group(1).replace('Med','Medium')]+=1
print(f"total={total} (README:39)  Critical={sev['Critical']} (README:0)  High={sev['High']} (README:5)")
print("PASS" if total==39 and sev['Critical']==0 and sev['High']==5 else "FAIL")
PY
```

Expected: `PASS`.

## Coverage target (integration level)

All four cross-document invariants hold with zero asymmetry. Test 1 is the load-bearing gate — it is
the check that would have blocked shipping the SEC-002-incomplete backlog.
