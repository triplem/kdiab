# Code-Generation Plan — U1 clinical-dose-review (MVR floor)

**Unit:** U1 · **Story:** US-1 · **Priority:** Must (non-trimmable floor, FR-1.4) · **Bolt:** B2.
**Deliverable:** the dose-calculation section of `docs/review/clinical-safety.md`.

> Depth: flag-with-evidence only (FR-1.1, RA-Q2=A). No corrected formulas or tests authored.

## Steps

- [x] Step 1 — Read `kdiab-calc` dose logic: `DoseCalculationService`, `DoseCalculation.kt` models,
  `Profile.kt`, `ProfilesClient`, service `CLAUDE.md`.
- [x] Step 2 — Verify the cross-unit hypothesis (BG converted but ISF/target not) against
  `kdiab-profiles/domain/model/Profile.kt` → ruled out (profiles canonicalizes to mg/dL). *(→ FIND-CLIN-009)*
- [x] Step 3 — Assess all six required dimensions (bolus formula, IOB, ISF/correction, carb-ratio, unit
  handling, rounding/guardrails), each with an evidence-linked finding or explicit no-concern. *(→ FR-1.1 pass/fail)*
- [x] Step 4 — Author `docs/review/clinical-safety.md` § dose calculation with 9 findings
  (FIND-CLIN-001..009), each carrying severity/effort/confidence/phase + patient-safety impact. *(→ US-1, FR-1.3)*
- [x] Step 5 — Write this plan + `code-summary.md`.

## Story-to-step traceability

| Step | Anchor |
|---|---|
| 1–3 | US-1, FR-1.1 (six-dimension coverage), FR-1.3 (evidence + safety impact) |
| 2 | FR-1.5 cross-check (profiles unit storage), FIND-CLIN-009 |
| 4 | US-1, FR-1.4 (this is the non-trimmable deliverable), NFR-1 (100% evidence-linked) |

## Coverage check (FR-1.1)

All six dimensions covered: bolus formula (no concern), IOB (FIND-CLIN-001), ISF/correction
(FIND-CLIN-007), carb-ratio (FIND-CLIN-008), unit handling (FIND-CLIN-002 + -009), rounding/guardrails
(FIND-CLIN-003/-004/-005/-006). ✓
