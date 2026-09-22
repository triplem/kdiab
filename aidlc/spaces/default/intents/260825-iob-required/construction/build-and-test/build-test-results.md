# Build & Test Results — #1563 activeIob required (kdiab-calc)

Consumes `../iob-required/code-generation/code-summary.md`,
`../iob-required/code-generation/code-generation-plan.md`.

## Backend — `cd kdiab-calc && ./gradlew check` (executed 2026-08-25)

First run **FAILED** on one Detekt issue:
`DoseCalculationService.kt:86 — MaxLineLength` (the new zero-IOB warning string > 120 chars).
Fix: wrapped the string literal across two lines (identical runtime value). Re-run:

```
> Task :detekt            (clean)
> Task :test              PASSED
> Task :integrationTest   PASSED
> Task :e2eTest           PASSED
> Task :koverVerify       (>= 80% line coverage)
> Task :check
BUILD SUCCESSFUL
```

Notable passing tests confirming the fix end-to-end:
- `CalcRoutesIntegrationTest > returns 400 with clinical message when activeIob omitted` — PASSED
  (full HTTP pipeline: missing `activeIob` → `BusinessValidationException` → StatusPages → 400 with the
  "activeIob is required" body).
- `CalcRoutesIntegrationTest > returns 400 when activeIob is negative` — PASSED.
- `DoseCalculationServiceTest` FR-3 matrix — PASSED (IOB=0+correction→warning; IOB=0 at-target→none;
  IOB=0 hypo→none; IOB>0→none).
- `CalcMapperTest` FR-2 — PASSED (omitted→400, explicit null→400, negative→400, exactly-zero accepted).

## Frontend — kdiab-ui

```
npm run build   → tsc -b clean; vite built (2979 modules) ✓   (required activeIob type-checks)
npm run lint    → eslint clean ✓
npm run test    → vitest: Test Files 48 passed (48), Tests 684 passed (684) ✓
```

`DoseCalculator.test.tsx` passes unchanged — it asserts the API is called with `activeIob:
expect.any(Number)` (the component always sends a computed value) and passes `activeIob` as an optional
component prop, both unaffected by the required `DoseRequestBody` type.

## Test-type coverage (test strategy = Minimal, bugfix)

| Type | Status |
|---|---|
| Unit | ✅ service (FR-3 + regression) + mapper (FR-2) + frontend component |
| Integration | ✅ HTTP-layer 400s with clinical message (`CalcRoutesIntegrationTest`) |
| e2e | ✅ Kotest embedded (happy path `activeIob:1.0`, hypo `activeIob:0.0`) |
| Performance | N/A — see `performance-test-instructions.md` (stateless, no perf dimension changed) |
| Security | ✅ see `security-test-instructions.md` (input hardening; no new secret/authz surface) |
| Coverage (Kover ≥80%) | ✅ `koverVerify` passed |

## Result

**PASS.** Backend and frontend gates green; coverage floor met. Ready for delivery / PR.
