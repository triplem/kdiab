# Code-Generation Plan — U7 backlog-assembly (+ README index)

**Unit:** U7 · **Story:** US-7 (docs half) · **Priority:** Must · **Bolt:** B5.
**Deliverables:** `docs/review/BACKLOG.md` + `docs/review/README.md`.

## Steps
- [x] Step 1 — Collect all findings from the five theme docs (30 actionable + 9 positive verdicts).
- [x] Step 2 — Order by `(safetyRank, valueDensity desc, effort asc)`, clinical-safety strictly first
  (NFR-3, FR-1.4); stamp roadmap phase per finding. *(→ FR-D.1, NFR-3, ADR-RVW-006)*
- [x] Step 3 — Author `BACKLOG.md` (ordered table + rationale + positive verdicts + materialization note).
- [x] Step 4 — Author `README.md` navigation index with a solo-maintainer reading guide (NFR-4; ReviewIndex
  folded into U7 per unit-of-work decomposition note). *(→ US-7…US-9 nav)*
- [x] Step 5 — Write plan + summary.

## Story-to-step traceability
| Step | Anchor |
|---|---|
| 2 | FR-D.1 (every area represented, area+severity, evidence-linked), NFR-3 (value-density, safety first) |
| 3 | US-7, ADR-RVW-006 (single phase authority) |
| 4 | NFR-4 (single-maintainer readable), C8 ReviewIndex |

## Coverage
Every theme represented (clinical/data/security/tech-debt/modernization); 0 Critical, 5 High; positive
verdicts listed separately; queued-issue projection deferred to U10. ✓
