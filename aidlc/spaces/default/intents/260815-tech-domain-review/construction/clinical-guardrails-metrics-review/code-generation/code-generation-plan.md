# Code-Generation Plan — U2 clinical-guardrails-metrics-review

**Unit:** U2 · **Story:** US-2 · **Priority:** Must · **Bolt:** B3.
**Deliverable:** § 2 of `docs/review/clinical-safety.md` (appended to U1's dose section).

## Steps

- [x] Step 1 — Read `kdiab-treatments` guardrail surface: `TreatmentService#addTreatment`, `TreatmentMapper#toDomain`. *(→ FR-1.2a)*
- [x] Step 2 — Read `kdiab-analyze` metric math: `AnalyticsService` HbA1c (DCCT constants), `computeTir`, `getAgp`/`percentile`. *(→ FR-1.2b)*
- [x] Step 3 — Compare HbA1c estimator vs GMI (Bergenstal 2018); TIR bands vs Battelino 2019; AGP percentile method. *(→ FR-1.2b definitional correctness)*
- [x] Step 4 — Append § 2 (2a guardrails, 2b metrics) with FIND-CLIN-010..014, each evidence-linked + safety impact. *(→ US-2, FR-1.3)*
- [x] Step 5 — Write plan + summary.

## Story-to-step traceability

| Step | Anchor |
|---|---|
| 1 | FR-1.2a (implausible-dose, stacking) |
| 2–3 | FR-1.2b (TIR, AGP, HbA1c/GMI) |
| 4 | US-2, FR-1.3 (evidence + safety impact), NFR-1 |

## Coverage (FR-1.2a/1.2b)

FR-1.2a: implausible-dose (FIND-CLIN-013), stacking (FIND-CLIN-014). FR-1.2b: TIR (011), AGP (012),
HbA1c/GMI (010). All five checks carry an evidence-linked verdict. ✓
