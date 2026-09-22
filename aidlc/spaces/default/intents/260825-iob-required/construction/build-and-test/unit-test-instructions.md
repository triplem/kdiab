# Unit Test Instructions — #1563 activeIob required

Consumes `../iob-required/code-generation/code-summary.md`,
`../iob-required/code-generation/code-generation-plan.md`.

## Backend unit tests

```bash
cd kdiab-calc && ./gradlew test
```

- `CalcMapperTest` (FR-2): `toDomain()` throws `BusinessValidationException` for omitted, explicit-null,
  and negative `activeIob`; accepts exactly-zero; valid-trend mapping tests now pass `activeIob`.
- `DoseCalculationServiceTest` (FR-3 + regression): zero-IOB warning present when a correction is
  recommended; absent at-target and during hypo; absent when IOB > 0; existing dose-math / hypo /
  high-dose / cap tests updated with explicit `activeIob = 0.0` and otherwise unchanged (NFR-2).

## Frontend unit/component tests

```bash
cd kdiab-ui && npm run test
```

`DoseCalculator.test.tsx` verifies the component always sends a numeric `activeIob` and renders the
warnings list (the new `doseCalc.warning.iobZero` maps via the `WARNING_KEYS` substring `IOB is zero`).
