# kdiab-calc — Agent Context

Port **8086**. Stateless dose calculator: bolus recommendation from profile + CGM trend. No database.
See root `CLAUDE.md` for shared conventions.

Root package: `org.javafreedom.kdiab.calc`

## Package Structure

```
adapters/inbound/web/
  CalcRoutes.kt            # POST /api/v1/users/{userId}/dose
  CalcMapper.kt            # API models ↔ domain models
adapters/outbound/http/
  ProfilesClient.kt        # Fetches the user's active profile from kdiab-profiles
application/service/
  DoseCalculationService.kt
domain/model/
  DoseRequest.kt           # currentBg, glucoseUnit, trend, carbsGrams, activeIob (REQUIRED, >=0), useProfileTime
  DoseResult.kt            # correctionDose, carbDose, trendAdjustment, totalRecommended, warnings
  DoseBreakdown.kt         # Inputs used for calculation (for UI transparency)
  CgmTrend.kt              # DOUBLE_UP | SINGLE_UP | FORTY_FIVE_UP | FLAT | FORTY_FIVE_DOWN | ...
domain/repository/
  ProfilesPort.kt          # Port interface for kdiab-profiles HTTP call
```

## Calculation Logic

`DoseCalculationService` fetches the user's active profile from kdiab-profiles, selects the
ISF/ICR/target segment for the current time of day (or `useProfileTime` override), then:

```
correctionDose  = (bgMgDl - targetBgMgDl) / ISF
carbDose        = carbsGrams / ICR
trendAdjustment = CgmTrend offset (±10/20/30 mg/dL equivalent converted via ISF)
totalRecommended = correctionDose + carbDose + trendAdjustment - activeIob
```

Units: if `glucoseUnit = "mmol/L"`, bg is multiplied by 18.0 before calculation.

## Safety Guardrails

| Constant | Value | Behaviour |
|---|---|---|
| `HYPOGLYCEMIA_THRESHOLD` | 70 mg/dL | No correction recommended if BG < 70 (hypo guard) |
| `HIGH_DOSE_THRESHOLD` | 20 U | Warning added to `DoseResult.warnings` |
| `MAX_ABSOLUTE_DOSE` | 30 U | Hard cap — dose is clamped; warning always added |

**`activeIob` is a REQUIRED input (#1563).** The service is stateless and does not compute IOB itself,
so the caller must supply it. An omitted, `null`, or negative `activeIob` is rejected with **400** at the
inbound mapper (`CalcMapper.toDomain` → `BusinessValidationException`) rather than being silently treated
as `0.0` — a silent zero would let repeated corrections stack into delayed hypoglycemia. When `activeIob`
is genuinely `0.0` **and** a correction dose is recommended, `DoseResult.warnings` carries a transparency
notice ("IOB is zero — ...", i18n key `doseCalc.warning.iobZero`); it is mutually exclusive with the
"IOB covers the full correction" warning. This is a breaking change to `DoseRequest` on `/api/v1`, kept
un-versioned because the sole consumer (kdiab-ui) already sends the field.

Results are labelled as **recommended dose, not a prescription**. The UI must display the `doseCalc.disclaimer` i18n key alongside every result.

## Key Design Decisions

- **Stateless** — no database; fetches profile on every request. Safe to scale horizontally.
- **Profile forwarding** — the user's JWT is forwarded to kdiab-profiles unchanged (same pattern as kdiab-analyze).
- `useProfileTime` allows the UI to override "now" for "what would the dose be at 08:00?" queries.

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `JWKS_URL` | — | Keycloak JWKS endpoint |
| `JWT_DOMAIN` | — | JWT issuer |
| `JWT_AUDIENCE` | `calc` | Expected `aud` claim |
| `PROFILES_URL` | `http://localhost:8082` | kdiab-profiles base URL |
| `PORT` | `8080` | HTTP listen port (remapped to 8086 externally) |
