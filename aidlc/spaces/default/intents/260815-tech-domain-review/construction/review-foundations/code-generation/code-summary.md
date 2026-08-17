# Code Summary — U0 review-foundations

**Unit:** U0 · **Bolt:** B1 · **Status:** complete.

## Files created / modified

| File | Kind | Notes |
|---|---|---|
| `docs/review/CONVENTIONS.md` | deliverable (workspace root) | The shared finding contract — schema, id scheme, scales, evidence format, live-verify procedure, doc-set map, NFR-5 note. |
| `aidlc/.../construction/review-foundations/code-generation/code-generation-plan.md` | record | Plan + story traceability. |
| `aidlc/.../construction/review-foundations/code-generation/code-summary.md` | record | This file. |

## Key implementation decisions

- **Docs-as-code.** For a recommendations-only intent the deliverable is markdown under `docs/review/`;
  U0 ships the conventions note that makes every later finding comparable, prioritizable, and
  evidence-linked by construction (ADR-RVW-003).
- **Area codes fixed** as CLIN / DATA / SEC / DEBT / MOD (ADR-RVW-003 exemplified only `FIND-CLIN-001`).
- **Critical reserved for patient-safety** (ADR-RVW-004) so the backlog's "all Critical first" rule is
  unambiguous and non-clinical items cannot mis-rank.
- **Evidence = `path/File.kt#symbol`, no line number** (ADR-RVW-007) — durable across a moving `main`.
- **Live-verify is a hard gate** before any codekb anchor is reported; the three known-resolved anchors
  are pre-recorded so no already-fixed debt is reported as open (US-5 guard, `project.md` rule).

## Test coverage summary

No automated tests — the deliverable is authored markdown with no runtime. Verification is via the
stage sensors (`required-sections`, `upstream-coverage`) and downstream reuse: every U1–U9 finding must
validate against this schema, which is the real regression check.

## Deviations from plan

None. All four plan steps completed as written. (Stage-level deviations — the jump past the per-unit
design stages and inline execution — are recorded in the code-generation `memory.md` diary.)
