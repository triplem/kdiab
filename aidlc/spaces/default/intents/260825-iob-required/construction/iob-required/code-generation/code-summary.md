# Code Summary — #1563 / FIND-CLIN-001: `activeIob` required in kdiab-calc

Branch: `fix/1563-activeiob-required`. All 10 plan steps completed.

## Files changed

### Backend — kdiab-calc (production)
| File | Change |
|---|---|
| `api/openapi.yaml` | `DoseRequest`: `activeIob` added to `required`; `default: 0.0` removed; `minimum: 0`; description documents required + ≥0 + the v1 breaking change (#1563). |
| `src/main/.../domain/model/DoseCalculation.kt` | `DoseRequest.activeIob: Double` — removed the `= 0.0` default (required at the domain layer, defense-in-depth). |
| `src/main/.../adapters/inbound/web/CalcMapper.kt` | `DoseRequestDto.activeIob: Double? = null`; `toDomain()` now throws `BusinessValidationException` on null/omitted ("activeIob is required…") and on negative ("activeIob must be zero or positive"). Import added. |
| `src/main/.../application/service/DoseCalculationService.kt` | Added FR-3 zero-IOB transparency warning ("IOB is zero — …"), fired only when `activeIob == 0.0 && correctionDose > 0.0`. Mutually exclusive with `iobCoversFullCorrection`. No dose-math change. |

### Backend — kdiab-calc (tests)
| File | Change |
|---|---|
| `src/test/.../DoseCalculationServiceTest.kt` | Added explicit `activeIob = 0.0` to all constructors that relied on the removed default; updated the mgdL-breakdown warning assertion (now expects exactly the zero-IOB warning); added 4 FR-3 tests (IOB=0+correction→warning; IOB=0 at-target→no warning; IOB=0 hypo→no warning; IOB>0→no warning). |
| `src/test/.../CalcMapperTest.kt` | Supplied `activeIob` to valid-trend mapping tests; replaced the inverted "defaults to 0.0" test; added FR-2 tests (omitted→400, explicit null→400, negative→400, exactly-zero accepted). |
| `src/integration-test/.../CalcRoutesIntegrationTest.kt` | Added `activeIob` to the 200-path body; added 2 HTTP-layer tests (omitted→400 with clinical message; negative→400). 401/403/invalid-trend tests unchanged (auth/access/trend-parse precede the IOB guard). |
| `src/e2e-test/.../e2e/CalcE2ETest.kt` | Valid-dose test now sends `activeIob:1.0` (keeps the clean no-warnings happy path); hypo test sends `activeIob:0.0`. |

### Frontend — kdiab-ui
| File | Change |
|---|---|
| `src/api/calcApi.ts` | `DoseRequestBody.activeIob: number` (was optional). |
| `src/features/calc/DoseCalculator.tsx` | Added `'IOB is zero' → 'doseCalc.warning.iobZero'` to `WARNING_KEYS`. Existing call site already always sends `activeIob` (computed), so it type-checks under the required field. |
| `src/i18n/locales/en.json`, `de.json` | Added `doseCalc.warning.iobZero` (en + de). |

### Docs
| File | Change |
|---|---|
| `kdiab-calc/CLAUDE.md` | Documented `activeIob` now required (>=0), the 400 rejection path, the zero-IOB warning, and the v1 breaking change. |

## Key implementation decisions

- **DTO nullable-with-null-default** (`Double? = null`) rather than non-nullable-no-default: routes both an
  omitted field and an explicit JSON `null` through the mapper guard, so the caller gets the *clinical*
  400 message rather than kotlinx's generic "Invalid request body" (`SerializationException`). Both map to
  400 via the existing StatusPages handlers.
- **Negative-IOB rejection** added (reviewer finding #3): a negative IOB inflates the correction dose —
  the same stacking direction the fix prevents — so it is rejected, never silently clamped.
- **Zero-IOB warning gated on `correctionDose > 0`** (FR-3): high-signal — the warning only appears when
  unaccounted IOB could actually have stacked.
- **No dose arithmetic changed** (NFR-2): existing regression tests for the correction/carb/trend math and
  the hypo/high-dose/cap guards are preserved (with explicit `activeIob = 0.0`).

## Test coverage (NFR-3 six-case matrix)

Omitted IOB → 400 (mapper unit + integration HTTP) · explicit null → 400 (mapper unit) · negative → 400
(mapper unit + integration HTTP) · IOB=0 with correction → warning present (service unit) · IOB=0 no
correction / hypo → warning absent (service unit ×2) · positive IOB → unchanged math, no zero-warning
(service unit). Actual coverage % verified in the Build and Test stage via Kover (`./gradlew check`).

## Deviations from plan

- None material. Additionally updated the integration + e2e suites (not separately listed in the plan's
  step titles but implied by NFR-3 and the removed default) so the whole three-tier suite compiles and
  passes.
