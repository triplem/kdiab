# Code Generation Plan — #1563 / FIND-CLIN-001: `activeIob` required in kdiab-calc

Unit: single (bugfix scope — no units-generation). Authoritative input:
`../../../inception/requirements-analysis/requirements.md` (FR-1..FR-4, NFR-1..NFR-4).

Test strategy: **Minimal** (bugfix scope) — requirement-driven unit tests. Backend tests are the
enforcement surface; the UI change is a type-tighten with no reachable new runtime path.

## Traceability: requirement → plan step

| Requirement | Plan step(s) |
|---|---|
| FR-1 remove default at all layers | Step 1 (OpenAPI), Step 2 (domain), Step 3 (DTO) |
| FR-2 missing/null/negative → clinical 400 | Step 3 (mapper guard), Step 6 (mapper tests) |
| FR-3 zero-IOB transparency warning (only when correction>0), mutually exclusive w/ iobCoversFullCorrection | Step 4 (service), Step 5 (service tests) |
| FR-4 client contract | Step 7 (calcApi.ts), Step 8 (DoseCalculator warning i18n), Step 9 (en/de locales) |
| NFR-1 v1, documented breaking change | Step 1 (OpenAPI description + note) |
| NFR-2 no dose-math change | Steps 2–4 keep math identical; Step 5 regression tests |
| NFR-3 ≥80% coverage, 6-case matrix | Steps 5–6 |

## Steps

- [ ] **Step 1 — OpenAPI contract** (`kdiab-calc/api/openapi.yaml`): add `activeIob` to
  `DoseRequest.required`; remove `default: 0.0`; expand the field `description` to state it is required to
  prevent insulin stacking, must be ≥ 0, and note the v1 breaking-change (NFR-1).
- [ ] **Step 2 — Domain model** (`domain/model/DoseCalculation.kt`): `DoseRequest.activeIob: Double`
  (remove `= 0.0`). Required constructor param at every layer.
- [ ] **Step 3 — DTO + mapper guard** (`adapters/inbound/web/CalcMapper.kt`): `DoseRequestDto.activeIob:
  Double? = null` (so omitted **and** explicit-null both deserialize to `null` and route through the
  guard, not the generic `SerializationException`). In `toDomain()`: `val iob = activeIob ?: throw
  BusinessValidationException("activeIob is required to prevent insulin stacking — supply the patient's
  current insulin-on-board")`; then `if (iob < 0.0) throw BusinessValidationException("activeIob must be
  zero or positive")`; pass `activeIob = iob`. Import `BusinessValidationException`.
- [ ] **Step 4 — Service warning** (`application/service/DoseCalculationService.kt`): in the `buildList`
  warnings block, add — when `request.activeIob == 0.0 && correctionDose > 0.0` — the warning
  `"IOB is zero — this correction assumes no active insulin on board; confirm no recent bolus before
  dosing"`. Mutually exclusive by construction with `iobCoversFullCorrection` (which requires
  `activeIob > 0.0`). No change to any dose arithmetic (NFR-2).
- [ ] **Step 5 — Service unit tests** (`src/test/.../DoseCalculationServiceTest.kt`): add explicit
  `activeIob = 0.0` to every `DoseRequest(...)` that omitted it (domain default removed); update the one
  `warnings.isEmpty()` assertion (mgdL breakdown test) — with IOB=0 + correction>0 it now carries exactly
  the zero-IOB warning. Add new tests: (a) IOB=0 with correction>0 → zero-IOB warning present;
  (b) IOB=0 at/below target (no correction) → warning absent; (c) IOB=0 during hypo → warning absent;
  (d) positive IOB → no zero-IOB warning (regression).
- [ ] **Step 6 — Mapper unit tests** (`src/test/.../CalcMapperTest.kt`): add explicit `activeIob` to every
  `DoseRequestDto(...)` that omitted it; replace the "applies default values for optional fields" test —
  its premise (omitted activeIob → 0.0) is inverted by the fix. New tests: omitted activeIob → 400
  (`BusinessValidationException`); explicit `null` → 400 same message; negative activeIob → 400;
  carbsGrams/useProfileTime still default correctly when supplied without activeIob.
- [ ] **Step 7 — Frontend client** (`kdiab-ui/src/api/calcApi.ts`): `DoseRequestBody.activeIob: number`
  (remove `?`). (No generated calc client exists; this hand-written client is the only one.)
- [ ] **Step 8 — Frontend warning display** (`kdiab-ui/src/features/calc/DoseCalculator.tsx`): add a
  `WARNING_KEYS` entry `'IOB is zero' → 'doseCalc.warning.iobZero'` so the new warning renders translated.
  Verify the always-sends-activeIob call site (line ~133) still type-checks under the required field.
- [ ] **Step 9 — i18n** (`kdiab-ui/src/i18n/locales/en.json`, `de.json`): add
  `doseCalc.warning.iobZero` (en + de).
- [ ] **Step 10 — Documentation**: update `kdiab-calc/CLAUDE.md` DoseRequest field note to reflect
  `activeIob` now required + the zero-IOB warning; note the v1 breaking change.

## Explicitly NOT in this plan (out of scope, per requirements)

- Server-side IOB computation from kdiab-treatments.
- Any change to the UI IOB decay model (`calcIOB`).
- API v2 / deprecation window.
- Changes to dose arithmetic, hypo guard, high-dose/cap warnings.
