# Code-Generation Plan — U3 data-model-review

**Unit:** U3 · **Story:** US-3 · **Priority:** Must · **Bolt:** B4. **Deliverable:** `docs/review/data-model.md`.

## Steps
- [x] Step 1 — Read the four schemas: `Measure.kt` (MeasureType), `Treatment.kt` (TreatmentType),
  `FoodEntry.kt`, `profiles/Profile.kt` (ProfileSchedule). *(→ FR-1.5)*
- [x] Step 2 — Assess each vs real T1D workflows (extended/dual-wave bolus, temp basal, carb absorption,
  sensor calibration, exercise/illness). Cross-check `AnalyticsService#computeBasalTotalIe` (absolute-rate-only). *(→ FR-1.5)*
- [x] Step 3 — Author `docs/review/data-model.md`: one completeness verdict per schema + the cross-cutting
  JSONB-validation finding (FIND-DATA-001..005). *(→ US-3, FR-1.3)*
- [x] Step 4 — Write plan + summary.

## Story-to-step traceability
| Step | Anchor |
|---|---|
| 1–2 | FR-1.5 (four schemas, real-workflow completeness) |
| 3 | US-3, FR-1.3 (evidence + safety impact), NFR-1; cross-ref FIND-CLIN-013 |

## Coverage (FR-1.5)
measures → FIND-DATA-002; treatments → FIND-DATA-003; carbs → FIND-DATA-001; profiles → FIND-DATA-004;
cross-cutting → FIND-DATA-005. Each schema carries an explicit verdict. ✓
