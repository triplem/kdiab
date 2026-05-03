# CLAUDE.md — kdiab-analyze

This file provides service-specific guidance. Common commands, architecture patterns, and agent personas are in the parent `CLAUDE.md`.

## Project Overview

**kdiab-analyze** is a stateless analytics aggregator that aggregates data from all three kdiab services and provides a unified analytics dashboard. It has **no database** — all state comes from upstream HTTP calls.

Features:
- **Timeline** — combined CGM + treatment events for a timeframe
- **HbA1c** — estimated HbA1c (DCCT formula) + Time In Range breakdown
- **AGP** — Ambulatory Glucose Profile (hourly percentile bands)
- **Profiles** — active insulin pump profiles for a timeframe

## Ports

| Component | Standalone compose | Root compose |
|---|---|---|
| analyze-backend | 8084 | 8084 |
| analyze-frontend | 3003 | 3003 |
| keycloak (standalone only) | 8085 | — (shared at 8081) |

## Commands

```bash
# Backend
./gradlew :backend:build             # Compile + package
./gradlew :backend:check             # Tests + Detekt + Kover
./gradlew :backend:run               # Run locally (requires upstream services + Keycloak)

# Frontend
cd frontend
npm install
npm run api:generate                 # Regenerate TypeScript client from api/openapi.yaml
npm run dev                          # Dev server at http://localhost:3003
npm run build                        # Full production build
npm run test                         # Vitest unit tests
```

## Environment Variables

| Variable | Default (application.conf) | Description |
|---|---|---|
| `MEASURES_URL` | `http://localhost:8080` | kdiab-measures backend base URL |
| `PROFILES_URL` | `http://localhost:8082` | kdiab-profiles backend base URL |
| `TREATMENTS_URL` | `http://localhost:8083` | kdiab-treatments backend base URL |
| `JWT_DOMAIN` | `http://localhost:8085/realms/kdiab-analyze` | Keycloak issuer URL (standalone; root compose uses 8081) |
| `JWKS_URL` | `http://localhost:8085/realms/kdiab-analyze/...` | JWKS endpoint |
| `JWT_AUDIENCE` | `analyze` | Expected JWT audience |
| `JWT_REALM` | `kdiab-analyze` | Keycloak realm name |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3003` | Allowed CORS origins |

In the root compose the upstream URLs are internal Docker service names (`http://measures-backend:8080` etc.).

## API Endpoints

All endpoints are under `/api/v1/users/{userId}/` and require a Bearer JWT.

| Method | Path | Description |
|---|---|---|
| GET | `/users/{userId}/timeline` | Combined measures + treatments |
| GET | `/users/{userId}/analytics/hba1c` | HbA1c estimate + TIR |
| GET | `/users/{userId}/analytics/agp` | AGP hourly percentiles (24 buckets) |
| GET | `/users/{userId}/profiles/active` | Active/archived profiles |

All endpoints accept `from` and `to` query parameters (ISO-8601 datetime, required).

Full schema: `api/openapi.yaml`. Swagger UI at http://localhost:8084/swagger (both standalone and root compose).

## JWT Forwarding

The service forwards the user's `Authorization: Bearer <token>` header unchanged to all upstream services. For this to work, the JWT must carry all four audiences: `analyze`, `measure`, `profile`, `treatment`.

- **Root compose**: the `kdiab-analyze-frontend` Keycloak client in `config/keycloak-realm.json` has four audience mappers — tokens issued at login are accepted by all four backends automatically.
- **Standalone compose**: the `kdiab-analyze-frontend` client in `kdiab-analyze/config/keycloak-realm.json` only has the `analyze` audience. Upstream services must be configured to trust the same realm, or use a shared Keycloak instance.

## Backend Package Structure

Root package: `org.javafreedom.kdiab.analyze`

```
adapters/inbound/web/
  BffRoutes.kt           # Route handlers — 4 endpoints, manual routing (no generated Paths)
  BffMapper.kt           # Domain models → serializable API response DTOs

adapters/outbound/http/
  MeasuresClient.kt      # Ktor CIO HttpClient → kdiab-measures /api/v1
  ProfilesClient.kt      # Ktor CIO HttpClient → kdiab-profiles /api/v1
  TreatmentsClient.kt    # Ktor CIO HttpClient → kdiab-treatments /api/v1

application/service/
  TimelineService.kt     # Fetches measures + treatments in parallel (coroutineScope/async),
                         # filters by timeframe, merges into Timeline domain object
  AnalyticsService.kt    # HbA1c (DCCT formula), TIR zone counts, AGP percentile calc
  ProfilesService.kt     # Returns ACTIVE + ARCHIVED profiles from kdiab-profiles

domain/model/
  Timeline.kt            # TimelineMeasure (with status), TimelineTreatment (no status), Timeline
  Analytics.kt           # TirBreakdown, Hba1cResult, AgpHourlyData, AgpResult,
                         # ProfileSummary (id, status, name, createdAt, previousProfileId),
                         # ProfilesResult
domain/exception/
  DomainExceptions.kt    # AuthenticationException, AuthorizationException,
                         # ResourceNotFoundException, BusinessValidationException, ConflictException
  UpstreamException.kt   # Wraps upstream service HTTP errors → 502 Bad Gateway

plugins/
  Security.kt            # JWT/JWKS auth (same pattern as other services)
  StatusPages.kt         # Exception → HTTP status mapping (includes UpstreamException → 502)
  Logging.kt             # X-Correlation-ID forwarding (sent to upstream services too)
  ErrorResponse.kt       # Serializable error response body
```

## Analytics Formulas

**HbA1c** — DCCT formula, CGM (`type=CGM`) readings only:
```
mean_glucose_mg_dL = average(sgv values)
HbA1c (%) = (mean_glucose_mg_dL + 46.7) / 28.7
```
If the `glucose_unit` JWT claim is `mmol/L`, multiply each value by 18.0 before averaging.
Returns `null` if there are no CGM readings in the timeframe.

**Time In Range (mg/dL thresholds)**:
| Zone | Range | Field |
|---|---|---|
| Below | < 70 mg/dL | `belowCount` |
| Target | 70–180 mg/dL | `inRangeCount` |
| Above | 180–250 mg/dL | `aboveCount` |
| High | > 250 mg/dL | `highCount` |

Percentages are computed in the frontend from the raw counts.

**AGP** — group CGM readings by UTC hour (0–23), compute p10/p25/p50/p75/p90 per bucket using sort-based linear interpolation. Returns 24 `AgpHourlyData` objects; buckets with no readings have `null` percentile values.

## Frontend Structure

```
src/
├── main.tsx                          # OIDC setup; reads VITE_OIDC_AUTHORITY / VITE_OIDC_CLIENT_ID
├── App.tsx                           # Tab navigation: Timeline | Analytics
├── api/
│   ├── client.ts                     # Axios instance + generated BffApi
│   └── tokenProvider.ts             # Extracts Bearer token from OIDC user object
├── context/TimeFormatContext.tsx     # 12h/24h display preference
├── i18n/locales/en.json, de.json
└── features/
    ├── timeframe/
    │   └── TimeframePicker.tsx       # Preset buttons (1d/7d/14d/30d) + custom date range
    ├── timeline/
    │   ├── TimelineView.tsx          # Container: TimeframePicker + TimelineChart
    │   └── TimelineChart.tsx        # Recharts ComposedChart:
    │                                #   <Line> for CGM (continuous, no dots for dense data)
    │                                #   <Scatter> for BGM (individual dots)
    │                                #   <Scatter> for treatments (bolus ▼, carbs ▲, correction ◆)
    │                                #   Reference lines at 70 + 180 mg/dL; shaded target band
    └── analytics/
        ├── AnalyticsView.tsx         # Container: HbA1cCard + TimeInRangeBar + AgpChart + ProfilesTable
        ├── HbA1cCard.tsx            # Large HbA1c % + mean glucose value
        ├── TimeInRangeBar.tsx       # CSS flex stacked bar — no Recharts (4 colour zones)
        ├── AgpChart.tsx             # Recharts AreaChart: 5 overlapping Area layers
        │                            #   p10–p90 (outer, low opacity)
        │                            #   p25–p75 (inner, medium opacity)
        │                            #   median (solid line)
        └── ProfilesTable.tsx        # Table: name, status, createdAt, id columns
```

## Key Design Decisions

- **No database**: this service is purely a read-time aggregator. Stateless — safe to scale horizontally.
- **No generated Paths**: `api/openapi.yaml` is used only for TypeScript client generation and Swagger UI. The backend uses manual Ktor routing (not the OpenAPI server stubs) because the aggregation logic doesn't map cleanly to generated controller stubs.
- **Parallel upstream calls**: `TimelineService` fetches measures and treatments concurrently via `coroutineScope { async {} }` to minimise latency.
- **mmol/L handling**: conversion happens in `AnalyticsService` before computing HbA1c; the AGP and TIR thresholds are always in mg/dL regardless of the user's preferred unit.
- **ProfileSummary has no activation timestamps**: the kdiab-profiles API does not expose `activatedAt`/`archivedAt` — only `createdAt` and `status`. `ProfilesService` filters by status (ACTIVE/ARCHIVED) rather than timeframe overlap.
