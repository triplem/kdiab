# API Documentation — kdiab (T1D Management Platform)

## API Surface Overview

kdiab exposes **8 internal REST APIs** (one per backend service) plus a **Nightscout external
compatibility facade**. Every API is **spec-first**: an OpenAPI 3.x document at
`<svc>/api/openapi.yaml` is the single source of truth from which server stubs and typed
clients are generated. In total the platform declares **60 paths / 82 operations**. Each
service ships a Swagger UI at `/swagger`, and specs are linted centrally by Spectral
(`.spectral.yaml` at repo root).

## Operation Inventory

| Service | Port | Paths | Operations | Surface |
|---|---|---|---|---|
| kdiab-measures | 8080 | 4 | 6 | CGM / BGM / BP / weight / pulse measurement store |
| kdiab-profiles | 8082 | 9 | 15 | Basal profiles (richest DB service); copy-on-write, ACTIVE/ARCHIVED |
| kdiab-treatments | 8083 | 7 | 8 | Bolus / basal / carb / correction events + device status |
| kdiab-analyze | 8084 | 12 | 12 | BFF read surface: timeline, AGP, HbA1c, profiles, device usage |
| kdiab-carbs | 8085 | 3 | 5 | Food / carb DB + entry tracking |
| kdiab-calc | 8086 | 1 | 1 | Bolus dose recommendation (single endpoint) |
| kdiab-nightscout | 8087 | 10 | 14 | Nightscout v1 + v3 external compatibility facade |
| kdiab-users | 8088 | 14 | 21 | Users, settings, doctor-patient, invitations, API keys (largest) |
| **Total** | — | **60** | **82** | 8 internal REST APIs + Nightscout external facade |

## Internal APIs (service-to-service and UI-to-service)

The five domain services expose CRUD-shaped REST APIs over their owned data. The three
composition services expose read/compute APIs and are themselves clients of the domain services:

- **kdiab-analyze (BFF)** — read-only composition surface. Its 12 operations (timeline, AGP,
  HbA1c, profiles, device usage) are computed by fanning out to measures, treatments, and
  profiles over HTTP.
- **kdiab-calc** — a single `POST` compute endpoint; calls profiles for the active profile.
- **kdiab-nightscout** — translation facade; fans out to measures, treatments, carbs,
  profiles, and users.

### JWT Forwarding and Multi-Audience Tokens

analyze and calc forward the caller's `Authorization: Bearer <token>` **unchanged** to
upstream services. A single Keycloak token is accepted by every backend because the
`kdiab-analyze-frontend` Keycloak client carries **audience mappers for all 8 backends**.
This avoids token exchange or separate service credentials on the read path. Each service
independently validates the JWT via JWKS (forced HTTPS for non-local issuers) and derives a
`UserPrincipal`; authorization is enforced by `UserPrincipal.canAccess`.

## External API — Nightscout Compatibility Facade

kdiab-nightscout implements the **Nightscout API v1 and v3** so that established community
tools (AAPS, xDrip+, Juggluco) interoperate without change:

- **v1:** `/api/v1/entries.json`, `/api/v1/treatments.json`, `/api/v1/status.json`
- **v3:** `/api/v3/{collection}`, `/api/v3/{collection}/history`, `/api/v3/version`,
  `/api/v3/status`, `/api/v3/lastModified`

The facade translates between the Nightscout wire schema and kdiab domain objects in both
directions (see the interaction diagram in `architecture.md`). **Known gap:** the v3
`/history` endpoints are stubs (TODO #894–#898), so the v3 surface is partially incomplete.

## Spec-First Code Generation

- **Backend:** `openApiGenerate` (openapi-generator 7.21.0, `kotlin-server`/ktor generator,
  `kotlinx_serialization`) produces Ktor server stubs + model classes into
  `build/generated/api/` and runs before `compileKotlin` in every service. Shared mustache
  templates live at `config/openapi-templates`. `MeasurePayload` and `TreatmentPayload` are
  mapped to `JsonObject` for schema flexibility.
- **Frontend:** `npm run api:generate` produces typescript-axios clients into the UI, running
  before `npm run build`. **Gap:** only 4 of 8 specs (measures, profiles, treatments, analyze)
  are wired into generation; carbs, calc, nightscout, and users have specs but hand-written
  Axios clients — a divergence from the spec-first contract that this review flags for closure.

## API Design Conventions

- **Type-safe routing** — generated `Paths` from the OpenAPI spec drive routing in most
  services; kdiab-analyze uses manual routing (no generated Paths).
- **Consistent error body** — the shared `ErrorResponse` (`{ code, message, ... }`) is emitted
  by the `StatusPages` plugin, which maps domain exceptions to HTTP status codes.
- **Access control at route entry** — `checkReadAccess` / `checkWriteAccess` helpers enforce
  ABAC before the handler runs.
- **Correlation** — every request carries `X-Correlation-ID`, propagated across the fan-out and
  bound to the logging MDC.
- **Linting** — Spectral lints every spec via `.spectral.yaml`; specs are validated in CI.
