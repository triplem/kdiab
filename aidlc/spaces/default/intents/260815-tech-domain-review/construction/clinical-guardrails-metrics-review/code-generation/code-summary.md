# Code Summary — U2 clinical-guardrails-metrics-review

**Unit:** U2 · **Bolt:** B3 · **Status:** complete.

## Files modified

| File | Kind | Notes |
|---|---|---|
| `docs/review/clinical-safety.md` | deliverable | Appended § 2 (guardrails + metric definitions) after U1's § 1. |
| `aidlc/.../clinical-guardrails-metrics-review/code-generation/code-generation-plan.md` | record | Plan. |
| `aidlc/.../clinical-guardrails-metrics-review/code-generation/code-summary.md` | record | This file. |

## Findings produced (5)

| ID | Severity | Area of check | Summary |
|---|---|---|---|
| FIND-CLIN-010 | Medium | HbA1c/GMI | Uses ADAG eAG inversion (mislabeled DCCT), not consensus GMI; diverges ≤1% at extremes + over-strong "HbA1c" label |
| FIND-CLIN-011 | (verdict) | TIR | Bands 54/70/180/250 correct per Battelino 2019 |
| FIND-CLIN-012 | (verdict) | AGP | Percentile math correct; local-time 288 buckets (CLAUDE.md doc drift noted) |
| FIND-CLIN-013 | Medium | Guardrail | Treatments store has no implausible-dose plausibility bound |
| FIND-CLIN-014 | High | Guardrail | No correction-bolus stacking detection anywhere (cross-ref FIND-CLIN-001) |

## Key decisions

- **GMI vs ADAG is a genuine definitional finding**, quantified at three glucose levels (100/150/250 mg/dL)
  to show the divergence is clinically material, not cosmetic.
- **Stacking (014) is the highest-severity finding** — traced across two services to show the guardrail is
  absent system-wide, and linked to the calc-side IOB default (FIND-CLIN-001) so the two form one theme.
- **Positive verdicts (011/012) recorded with evidence**, plus a cross-cutting note on the advanced,
  source-cited metrics (GRI/PGS/CGP) — the review credits genuine rigor, not only gaps.

## Test coverage summary

No tests (recommendations-only). Findings are the deliverable.

## Deviations from plan

None. `clinical-safety.md` is a living doc: U1 created it, U2 appended; later theme docs are separate files.
