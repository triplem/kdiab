# Code Summary — U7 backlog-assembly

**Unit:** U7 · **Bolt:** B5 · **Status:** complete.
**Deliverables:** `docs/review/BACKLOG.md`, `docs/review/README.md`.

## Files created
| File | Kind | Notes |
|---|---|---|
| `docs/review/BACKLOG.md` | deliverable | 30 actionable findings, single value-density order, positive verdicts, materialization note. |
| `docs/review/README.md` | deliverable | Navigation index + solo-maintainer reading guide + at-a-glance stats. |
| record: plan + summary | record | This unit's traceability. |

## Key decisions
- **References, not copies** — the backlog cites each finding's theme doc rather than duplicating it, so
  there is one source of truth per finding (a finding appears once in its theme doc, once by reference here).
- **Safety-first ordering made explicit** — clinical-safety rows 1–9, then cross-theme High by value-density,
  then Medium/Low; the ordering key and rationale are printed so the order is auditable, not asserted.
- **0 Critical is stated as a result**, not an omission — with the 9 positive verdicts listed, so the
  maintainer trusts the "nothing catastrophic found" conclusion.
- **Single phase authority (ADR-RVW-006)** — each row's phase matches the roadmap band it will fall into.

## Test coverage summary
No tests (recommendations-only). The deliverable's integrity is the evidence links + live-verify ledger.

## Deviations from plan
None. Quick-wins (U8) and roadmap (U9) are downstream projections of this backlog; issue set (U10) appends
the queued projection.
