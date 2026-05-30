# kdiab-analyze — Agent Context

Port **8084**. Stateless BFF: aggregates data from measures, profiles, and treatments. No database.
See root `CLAUDE.md` for shared conventions.

Root package: `org.javafreedom.kdiab.analyze`

## Package Structure

```
adapters/inbound/web/
  AnalyzeRoutes.kt       # 4 endpoints, manual routing (no generated Paths — see below)
  AnalyzeMapper.kt       # Domain models → API response DTOs
adapters/outbound/http/
  MeasuresClient.kt      # Ktor CIO HttpClient → kdiab-measures
  ProfilesClient.kt      # Ktor CIO HttpClient → kdiab-profiles
  TreatmentsClient.kt    # Ktor CIO HttpClient → kdiab-treatments
application/service/
  TimelineService.kt     # Fetches measures + treatments in parallel (coroutineScope/async)
  AnalyticsService.kt    # HbA1c (DCCT formula), TIR zone counts, AGP percentile calc
  ProfilesService.kt     # Returns ACTIVE + ARCHIVED profiles
domain/model/
  Timeline.kt            # TimelineMeasure, TimelineTreatment, Timeline
  Analytics.kt           # TirBreakdown, Hba1cResult, AgpHourlyData, AgpResult, ProfilesResult
domain/exception/
  UpstreamException.kt   # Wraps upstream HTTP errors → 502 Bad Gateway
```

## API Endpoints

All under `/api/v1/users/{userId}/`, require Bearer JWT, accept `from`/`to` (ISO-8601, required).

| Method | Path | Description |
|---|---|---|
| GET | `/users/{userId}/timeline` | Combined measures + treatments |
| GET | `/users/{userId}/analytics/hba1c` | HbA1c estimate + TIR |
| GET | `/users/{userId}/analytics/agp` | AGP hourly percentiles (24 buckets) |
| GET | `/users/{userId}/profiles/active` | Active/archived profiles |

## Analytics Formulas

**HbA1c** — DCCT formula, CGM readings only:
```
mean_glucose_mg_dL = average(sgv values)
HbA1c (%) = (mean_glucose_mg_dL + 46.7) / 28.7
```
If `glucose_unit` JWT claim is `mmol/L`, multiply each value by 18.0 before averaging. Returns `null` if no CGM readings in timeframe.

**Time In Range (mg/dL thresholds):**

| Zone | Range | Field |
|---|---|---|
| Very Low | < 54 mg/dL | `veryLowCount` |
| Below | 54–70 mg/dL | `belowCount` |
| Target | 70–180 mg/dL | `inRangeCount` |
| Above | 180–250 mg/dL | `aboveCount` |
| High | > 250 mg/dL | `highCount` |

**AGP** — group CGM readings by UTC hour (0–23), compute p10/p25/p50/p75/p90 per bucket using sort-based linear interpolation. Returns 24 `AgpHourlyData` objects; buckets with no readings have `null` percentile values.

## Key Design Decisions

- **No database** — purely a read-time aggregator; safe to scale horizontally.
- **No generated Paths** — uses manual Ktor routing; aggregation logic doesn't map to generated controller stubs.
- **Parallel upstream calls** — `TimelineService` uses `coroutineScope { async {} }` to minimise latency.
- **mmol/L conversion** happens in `AnalyticsService` before HbA1c; AGP and TIR thresholds are always mg/dL.

## JWT Forwarding

The service forwards the user's `Authorization: Bearer <token>` unchanged to all upstream services. The root compose `kdiab-analyze-frontend` Keycloak client has audience mappers for all backends — a single token is accepted everywhere. The standalone compose client only has the `analyze` audience; upstream services need a shared realm for standalone dev.

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `JWKS_URL` | — | Keycloak JWKS endpoint |
| `JWT_DOMAIN` | — | JWT issuer |
| `JWT_AUDIENCE` | `analyze` | Expected `aud` claim |
| `JWT_REALM` | `kdiab-analyze` | Keycloak realm name |
| `MEASURES_URL` | `http://localhost:8080` | kdiab-measures base URL |
| `PROFILES_URL` | `http://localhost:8082` | kdiab-profiles base URL |
| `TREATMENTS_URL` | `http://localhost:8083` | kdiab-treatments base URL |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3003` | Allowed CORS origins |
