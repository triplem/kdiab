# Code-Generation Plan — U8 quick-wins

**Unit:** U8 · **Story:** US-8 · **Priority:** Must · **Bolt:** B6. **Deliverable:** `docs/review/QUICK-WINS.md`.

## Steps
- [x] Step 1 — Filter the backlog by the predicate: effort=S AND high value AND independently shippable
  (NFR-2, FR-D.2). *(→ US-8)*
- [x] Step 2 — Author `docs/review/QUICK-WINS.md`: top 5 high-value S items + a separate "also quick,
  lower value" group + an explicit "not quick" exclusion list. *(depends on theme findings directly, not
  on the assembled backlog — honours US-8 INVEST independence from US-7)*
- [x] Step 3 — Write plan + summary.

## Story-to-step traceability
| Step | Anchor |
|---|---|
| 1 | US-8, FR-D.2 (short actionable list), NFR-2 (independently shippable in a burst) |
| 2 | ADR-RVW-006 (projection, no re-authoring) |

## Note on dependency
Per `unit-of-work.md`, U8 depends on the theme findings (U1–U6) **directly**, not on the assembled
backlog (U7) — honouring US-8's INVEST independence. The engine's topo order happened to build U7 first,
but the quick-wins predicate is applied to the findings themselves.

## Coverage
Effort=S high-value subset selected (5 top + 4 lower-value); large-effort items explicitly excluded. ✓
