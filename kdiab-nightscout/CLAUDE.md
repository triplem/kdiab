# kdiab-nightscout — Agent Context

Port **8087**. Nightscout API v1 compatibility layer for AAPS, xDrip+, Juggluco. **Optional** service
(Docker Compose `profiles: [optional]`). No database — stateless reverse-proxy adapter.
See root `CLAUDE.md` for shared conventions.

Root package: `org.javafreedom.kdiab.nightscout`

## Package Structure

```
adapters/inbound/web/
  NightscoutRoutes.kt      # GET /api/v1/entries.json, POST /api/v1/entries.json
                           # GET /api/v1/treatments.json, POST /api/v1/treatments.json
                           # GET /api/v1/status.json
  NightscoutMapper.kt      # Nightscout format ↔ kdiab domain model
adapters/outbound/http/
  MeasuresClient.kt        # Ktor HTTP client → kdiab-measures
  TreatmentsClient.kt      # Ktor HTTP client → kdiab-treatments
  CarbsClient.kt           # Ktor HTTP client → kdiab-carbs
  ProfilesClient.kt        # Ktor HTTP client → kdiab-profiles
  CircuitBreaker.kt        # (from kdiab-common) — 4 independent breakers
application/service/
  NightscoutService.kt
domain/model/
  NightscoutModels.kt      # Nightscout v1 data models (entries, treatments)
domain/exception/
  UpstreamException.kt
```

## API Version

Implements **Nightscout API v1** (paths under `/api/v1/`). Any reference to "v3" in docs or
comments is incorrect — the live wire protocol is always v1.

## Circuit Breakers

Four independent `CircuitBreaker` instances (from `kdiab-common`):
`measures`, `treatments`, `carbs`, `profiles` — threshold: 5 failures, reset: 30 s.

Log signatures: `circuit_breaker service=<name> state=OPEN|HALF_OPEN|CLOSED`

## Starting the Optional Service

```bash
./podman-up.sh --optional --build
# or
COMPOSE_PROFILES=optional docker compose up --build
```

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `JWKS_URL` | — | Keycloak JWKS endpoint |
| `JWT_DOMAIN` | — | JWT issuer |
| `JWT_AUDIENCE` | `nightscout` | Expected `aud` claim |
| `MEASURES_URL` | `http://localhost:8080` | kdiab-measures base URL |
| `TREATMENTS_URL` | `http://localhost:8083` | kdiab-treatments base URL |
| `CARBS_URL` | `http://localhost:8085` | kdiab-carbs base URL |
| `PROFILES_URL` | `http://localhost:8082` | kdiab-profiles base URL |
| `PORT` | `8087` | HTTP listen port |
| `HTTP_CONNECT_TIMEOUT_MS` | `5000` | TCP connect timeout |
| `HTTP_REQUEST_TIMEOUT_MS` | `30000` | Total request timeout |
| `HTTP_RETRY_MAX_RETRIES` | `3` | Max retries on upstream 5xx |
