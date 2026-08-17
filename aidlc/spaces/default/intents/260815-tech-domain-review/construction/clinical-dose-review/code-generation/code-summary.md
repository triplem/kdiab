# Code Summary — U1 clinical-dose-review

**Unit:** U1 · **Bolt:** B2 · **Status:** complete. **The single non-trimmable deliverable (FR-1.4).**

## Files created / modified

| File | Kind | Notes |
|---|---|---|
| `docs/review/clinical-safety.md` | deliverable (workspace root) | Created with the dose-calculation section (§1); U2 appends §2 (guardrails + metrics). |
| `aidlc/.../construction/clinical-dose-review/code-generation/code-generation-plan.md` | record | Plan + traceability. |
| `aidlc/.../construction/clinical-dose-review/code-generation/code-summary.md` | record | This file. |

## Findings produced (9)

| ID | Severity | Dimension | Summary |
|---|---|---|---|
| FIND-CLIN-001 | High | IOB | `activeIob` caller-supplied, defaults to 0 → insulin-stacking risk |
| FIND-CLIN-002 | Medium | Unit handling | `glucoseUnit` unvalidated free string; malformed unit mis-scales BG |
| FIND-CLIN-003 | Medium | Guardrails | 30 U max-dose cap is global, not personalized |
| FIND-CLIN-004 | Medium | Guardrails | Trend adjustment pre-emptively adds insulin (aggressive) |
| FIND-CLIN-005 | Low | Rounding | 0.01 U rounding finer than pump increments |
| FIND-CLIN-006 | Low | Input | `currentBg` lacks plausibility validation |
| FIND-CLIN-007 | (verdict) | ISF/correction | Correct — no concern |
| FIND-CLIN-008 | (verdict) | Carb-ratio | Correct — no concern |
| FIND-CLIN-009 | (verdict) | Unit consistency | Cross-unit bug hypothesis ruled out (profiles = mg/dL canonical) |

## Key decisions

- **Highest-value finding is FIND-CLIN-001** (IOB default 0): the stateless calc trusts the caller for
  the most safety-critical input. Flagged High with a small, self-contained fix (make `activeIob`
  required) and a separate larger option (server-side IOB from treatments).
- **Honest negatives.** Three positive verdicts (007/008/009) are recorded as explicit findings with
  evidence, not omissions — satisfying FR-1.1's "explicit no concern found with a code reference."
- **A plausible Critical was investigated and disproven** (009) rather than asserted — the review earns
  trust by ruling concerns out with evidence, not only by raising them.

## Test coverage summary

No tests authored (recommendations-only, RA-Q2=A). The dose findings are the deliverable.

## Deviations from plan

None.
