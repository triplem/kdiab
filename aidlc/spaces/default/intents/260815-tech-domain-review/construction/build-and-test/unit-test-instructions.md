# Unit Test Instructions — Finding-Record & Document Validation

> **Unit-level = the single Finding Record and the single document.** These checks operationalize the
> `validate()` / `assess()` contract from application-design and the Finding-Record schema in
> `CONVENTIONS.md`. They are the deliverable equivalent of unit tests: each asserts one property of one
> finding or one doc in isolation. Driven by the per-unit `code-generation-plan.md` doc-set contract and
> the `code-summary.md` finding inventories.

## Framework & how to run

No test runner — the "unit under test" is a Markdown finding block, so the checks are `grep`/`python3`
assertions run against `docs/review/*.md`. Each check below is independent, repeatable, and
order-free. Run from the repo root. A check that emits any `FAIL`/`DEFECT` line fails that unit test.

## Test 1 — every finding has all mandated Finding-Record fields

Per `CONVENTIONS.md`, a finding missing a mandated field is a **defect**, not a warning. Mandated:
`id`, `area`, `severity`, `evidence-link` (`Evidence:`), `recommendation` (`Recommendation:`),
`patient-safety-impact` (`Patient-safety impact:`). Conditional: `incremental-alternative` iff the
recommendation is a rewrite; `cross-reference` iff already tracked.

```bash
python3 - <<'PY'
import re,pathlib
# each mandated field is a regex: the label may carry a parenthetical qualifier before the colon,
# e.g. "Recommendation (rewrite):" on a rewrite finding (FIND-MOD-002).
req={'Severity':r'Severity:',
     'Evidence':r'Evidence:',
     'Recommendation':r'Recommendation(?:\s*\([^)]*\))?:',
     'Patient-safety impact':r'Patient-safety impact:'}
bad=0
for area,fn in [('CLIN','clinical-safety'),('DATA','data-model'),('SEC','security'),
                ('DEBT','tech-debt'),('MOD','modernization')]:
    txt=pathlib.Path(f'docs/review/{fn}.md').read_text()
    for b in re.split(r'(?=^#### FIND-)',txt,flags=re.M):
        m=re.match(r'^#### (FIND-\S+)',b)
        if not m: continue
        miss=[k for k,pat in req.items() if not re.search(pat,b)]
        if miss: print(f"DEFECT {m.group(1)} missing {miss}"); bad+=1
print("PASS — all findings carry mandated fields" if not bad else f"{bad} defective finding(s)")
PY
```

Expected: `PASS`. (Positive / `no concern found` verdicts still carry the field skeleton so the check
remains uniform. The `Recommendation` matcher allows a `(rewrite)` qualifier per the C-1 rewrite rule.)

## Test 2 — finding IDs contiguous & unique within each area

The `FIND-<AREA>-NNN` scheme requires `NNN` zero-padded and unique within an area; a gap or duplicate
signals a lost or double-counted finding.

```bash
python3 - <<'PY'
import re,pathlib
for area,fn,exp in [('CLIN','clinical-safety',14),('DATA','data-model',5),('SEC','security',7),
                    ('DEBT','tech-debt',8),('MOD','modernization',5)]:
    nums=sorted(int(x) for x in re.findall(rf'^#### FIND-{area}-(\d+)',
                pathlib.Path(f'docs/review/{fn}.md').read_text(),re.M))
    ok = nums==list(range(1,exp+1)) and len(nums)==len(set(nums))
    print(f"{'PASS' if ok else 'FAIL'} {area}: {nums}")
PY
```

Expected: PASS for all five (CLIN 1–14, DATA 1–5, SEC 1–7, DEBT 1–8, MOD 1–5 = 39 total).

## Test 3 — severity discipline (Critical reserved for clinical safety)

Per ADR-RVW-004, a non-clinical finding caps at High; only clinical/domain findings may be Critical.

```bash
python3 - <<'PY'
import re,pathlib,sys
bad=0
for area,fn in [('DATA','data-model'),('SEC','security'),('DEBT','tech-debt'),('MOD','modernization')]:
    txt=pathlib.Path(f'docs/review/{fn}.md').read_text()
    for b in re.split(r'(?=^#### FIND-)',txt,flags=re.M):
        m=re.match(r'^#### (FIND-\S+)',b); 
        if not m: continue
        if re.search(r'Severity:\s*Critical',b): print(f"DEFECT non-clinical Critical: {m.group(1)}"); bad+=1
print("PASS — no non-clinical Critical" if not bad else f"{bad} discipline violation(s)")
PY
```

Expected: `PASS`. (This review raised **0 Critical** total — an intentionally reassuring result.)

## Test 4 — evidence-link format (symbol, not line number)

Per ADR-RVW-007, citations use `path/File.kt#symbol` (or changelog changeSet / config key) — never a
bare line number, which rots as `main` moves.

```bash
# Any 'Evidence:' line ending in ':<number>' (a line-number cite) is a format defect
grep -rnE '^- Evidence:.*:[0-9]+`?\s*$' docs/review/*.md && echo "DEFECT line-number cites found" \
  || echo "PASS — all evidence cites are symbol/key based"
```

Expected: `PASS`.

## Coverage target (unit level)

100% of finding blocks pass Tests 1–4 (schema, contiguity, discipline, evidence format). This is a
hard gate: the review's own NFR-1 makes a schema-incomplete finding inadmissible. Positive-verdict
findings count toward coverage (they are validated the same way).
