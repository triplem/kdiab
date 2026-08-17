# Code-Generation Plan — U9 roadmap

**Unit:** U9 · **Story:** US-9 · **Priority:** Should · **Bolt:** B10. **Deliverable:** `docs/review/ROADMAP.md`.

## Steps
- [x] Step 1 — Read the complete backlog (30 actionable findings + phases). *(→ depends on U7)*
- [x] Step 2 — Apply the `bandOf` authority (ADR-RVW-006): Near = quick-wins + Must clinical-safety;
  Mid = Should security + tech-debt; Long = Could modernization + structural data-model. *(→ FR-D.3, Q7=A)*
- [x] Step 3 — Author `docs/review/ROADMAP.md`: three bands, each with items, rough per-phase effort, and a
  rationale; note the one cross-item dependency (CLIN-014→CLIN-001); NFR-5 practice-conformance note. *(→ US-9, NFR-5)*
- [x] Step 4 — Write plan + summary.

## Story-to-step traceability
| Step | Anchor |
|---|---|
| 2 | FR-D.3 (near/mid/long + rationale + rough effort), ADR-RVW-006 (single phase authority) |
| 3 | US-9, NFR-2 (independently shippable), NFR-5 (practice conformance) |

## Coverage
All 30 actionable findings banded; per-phase effort estimated; phase == the backlog's roadmap-phase tag
(no drift, ADR-RVW-006). ✓
