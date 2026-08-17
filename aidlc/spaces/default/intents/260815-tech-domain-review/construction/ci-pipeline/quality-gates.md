# Quality Gates — Review Deliverable

> Gate definitions enforced by `review-verify.yml` (via `docs/review/verify.py`) for the
> Technology & Domain Review. Consumes `build-and-test-summary.md` + `build-test-results.md` (the
> checks these gates automate) and the per-unit `code-summary.md` (what is being gated). For this
> assessment intent the "artifact" promoted through the gate is the committed `docs/review/` tree.

## Gate definition

A change to `docs/review/**` may merge to `main` only when **all ten verifier checks pass** (exit 0).
Any single failure blocks promotion — a gate is not a suggestion (pipeline-deploy key principle 4).

| Gate | Check | Blocks merge on | Rationale |
|---|---|---|---|
| G1 Completeness | `presence` | any of the 10 deliverables missing/empty | the set must be whole to be navigable (NFR-4) |
| G2 Schema | `schema` | a finding missing a mandated field | NFR-1: a schema-incomplete finding is a defect, not a warning |
| G3 Identity | `contiguity` | ID gap or duplicate in an area | lost/double-counted findings corrupt the backlog |
| G4 Safety-severity | `severity-discipline` | a non-clinical Critical | ADR-RVW-004: Critical reserved for patient-safety |
| G5 Evidence | `evidence-format` | a bare line-number citation | ADR-RVW-007: line numbers rot as `main` moves |
| G6 Traceability | `backlog-traceability` | an actionable finding not in the backlog, or count≠heading | the defect class that dropped FIND-SEC-002 |
| G7 Phase-authority | `phase-authority` | theme/backlog/roadmap phase drift | ADR-RVW-006: single source of truth; the CLIN-010/013 class |
| G8 Navigability | `dead-links` | a broken intra-set link | NFR-4: the doc set must stay one connected graph |
| G9 Headline integrity | `readme-numbers` | totals ≠ 39 / 0 Critical / 5 High | the summary must not contradict the findings |
| G10 Secret hygiene | `no-secrets` | a secret pattern in the docs | `security.md` rule: never commit secrets |

## Thresholds

- **Pass criterion:** `verify.py` exit code `0` (all ten checks report `PASS`).
- **No partial pass, no warn-only tier:** every check is blocking. There is no coverage-percentage
  threshold here — the deliverable is prose, so integrity is binary (consistent or not), unlike the
  kdiab services' 80% Kover / Vitest line-coverage floor.
- **Fast feedback:** the whole gate runs in seconds (no build, no services), honouring the
  fast-pipeline principle.

## Baseline result (this run)

Executed at stage close against the fixed deliverable set:

```
[PASS] presence  [PASS] schema  [PASS] contiguity  [PASS] severity-discipline
[PASS] evidence-format  [PASS] backlog-traceability  [PASS] phase-authority
[PASS] dead-links  [PASS] readme-numbers  [PASS] no-secrets
All 10 checks passed.  (exit 0)
```

The two `build-and-test` defects (FIND-SEC-002 omission; CLIN-010/013 phase drift) are exactly what
gates G6 and G7 now prevent from recurring.

## Manual / follow-up gates (not automated here)

- **Currency guard (US-5):** as `main` advances past the codekb snapshot (`d6c8866b`), evidence anchors
  must be re-verified against live `main` before a finding is reported as open. This is a
  human-in-the-loop review step, not a static check — the verifier confirms citation *format*, not that
  a symbol still exists (which requires resolving against a moving tree).
- **Branch protection:** to make the gate load-bearing on merge, add the workflow's
  `Verify review deliverable integrity` job to the required status checks for `docs/review/**`.
