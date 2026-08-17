# Code Summary — U9 roadmap

**Unit:** U9 · **Bolt:** B10 · **Status:** complete. **Deliverable:** `docs/review/ROADMAP.md`.
**Final unit of the review.**

## What was produced
Three value-density bands over the complete 30-item backlog:
- **Near** (~8–10 days): SEC-001, CLIN-002/013/010, CLIN-001, DEBT-005 — safety + highest value-per-effort.
- **Mid** (~1–2 months): the clinical-safety hardening (incl. CLIN-014 stacking, dependent on CLIN-001),
  both regulatory decisions, the domain data-model gaps, and residual tech-debt.
- **Long** (quarter-scale): structural data-model validation, observability, and the service-consolidation
  question (incremental-first per C-1).

## Key decisions
- **Phase == backlog tag** (ADR-RVW-006) — the roadmap is the single phase authority; no drift possible.
- **The one real dependency is surfaced** (CLIN-014 stacking detection depends on CLIN-001 IOB), so the
  otherwise-independent bursts aren't mis-ordered.
- **C-1/C-2 honoured in Long** — the only rewrite (service consolidation) leads with its incremental
  alternative and is placed last, matching bounded solo-maintainer capacity.
- **NFR-5 reaffirmed** — every item is one practice-conformant change; no coordinated release required.

## Test coverage summary
No tests (recommendations-only).

## Deviations from plan
None. **All 11 units (U0–U10) of the review are now complete.** The `docs/review/` deliverable set is
whole; GitHub-issue execution remains deferred (OQ-1).
