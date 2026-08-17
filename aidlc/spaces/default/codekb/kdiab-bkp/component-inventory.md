# Component Inventory — kdiab (T1D Management Platform)

## Overview

The platform comprises **9 backend Gradle modules** (8 runnable Ktor services + 1 shared
library), the **kdiab-ui** React SPA, and the **build-logic** included build. This inventory
lists each component's responsibility, type, and dependencies. Two distinct dependency kinds
are tracked:

- **Build-time (spec-only) coupling** — a service consumes another service's OpenAPI spec to
  generate a typed client; there is no shared domain code.
- **Runtime (HTTP) coupling** — a service calls another service over HTTP at request time.

## Backend Services

### kdiab-common (shared library)

- **Type:** shared Kotlin library, published to GitHub Packages; applies `kdiab.kotlin-base` + `maven-publish`.
- **Responsibility:** cross-cutting foundation. Domain types (`Role`, `GlucoseUnit`, `AuditLog`,
  `DomainExceptions`), `UserPrincipal`, and **18 shared Ktor plugins** (Security/JWT, StatusPages,
  Logging, Health, CircuitBreaker, RateLimit, SecurityHeaders, Metrics, Tracing, Cors,
  HttpClientDefaults, RouteUtils, AuditRoutes, and more).
- **Consumed by:** every backend service via `implementation(kdiab-common)`.
- **Size:** 23 main `.kt`.

### kdiab-measures (8080)

- **Type:** domain service (`kdiab.ktor-db-service`), PostgreSQL + Exposed.
- **Responsibility:** primary time-series store — CGM, BGM, blood pressure, weight, pulse.
- **Depends on:** kdiab-common (build). **Consumed by (runtime):** analyze, nightscout.
- **Size:** 8 main `.kt`.

### kdiab-profiles (8082)

- **Type:** domain service, PostgreSQL + Exposed.
- **Responsibility:** insulin-pump basal profiles; copy-on-write versioning; ACTIVE/ARCHIVED lifecycle.
- **Depends on:** kdiab-common (build). **Consumed by (runtime):** analyze, calc, nightscout.
- **Size:** 16 main `.kt`.

### kdiab-treatments (8083)

- **Type:** domain service, PostgreSQL + Exposed.
- **Responsibility:** discrete treatment events (bolus, basal, carbs, corrections) + device status.
- **Depends on:** kdiab-common (build). **Consumed by (runtime):** analyze, nightscout.
- **Size:** 15 main `.kt`.

### kdiab-carbs (8085)

- **Type:** domain service, PostgreSQL + Exposed.
- **Responsibility:** food / carbohydrate database and per-meal carb entries.
- **Depends on:** kdiab-common (build). **Consumed by (runtime):** nightscout.
- **Size:** 8 main `.kt`.

### kdiab-users (8088)

- **Type:** domain service, PostgreSQL + Exposed; **largest service**.
- **Responsibility:** user settings, doctor-patient relationships, invitations, API keys;
  Keycloak Admin API integration via `IdentityProviderPort` (`infrastructure/keycloak`).
- **Depends on:** kdiab-common (build), Keycloak Admin API (runtime). **Consumed by (runtime):** nightscout.
- **Size:** 33 main `.kt`.

### kdiab-analyze (8084)

- **Type:** stateless BFF (`kdiab.ktor-service`), **no DB**. Hexagonal outbound ports + HTTP clients.
- **Responsibility:** aggregates measures/treatments/profiles into timeline, AGP, HbA1c, and
  device-usage analytics for the UI.
- **Depends on:** kdiab-common (build); **specs of** measures, treatments, profiles (build);
  measures, treatments, profiles (runtime HTTP with forwarded JWT).
- **Size:** 21 main `.kt`.

### kdiab-calc (8086)

- **Type:** stateless calculator (`kdiab.ktor-service`), **no DB**. Single endpoint.
- **Responsibility:** bolus recommendation from active profile + CGM trend (decision aid).
- **Depends on:** kdiab-common (build); **spec of** profiles (build); profiles (runtime HTTP, forwarded JWT).
- **Size:** 9 main `.kt`.

### kdiab-nightscout (8087)

- **Type:** stateless compatibility facade (`kdiab.ktor-service`), **no DB**.
- **Responsibility:** Nightscout API v1 + v3 compatibility for AAPS/xDrip+/Juggluco; fans out to 5 upstreams.
- **Depends on:** kdiab-common (build); **specs of** measures, treatments, carbs, profiles, users
  (build) — adds a Ktor client; measures, treatments, carbs, profiles, users (runtime HTTP).
- **Size:** 16 main `.kt`. Highest-debt module (heavy Detekt baseline; v3 HISTORY stubs).

## Frontend

### kdiab-ui (3005 / 3000)

- **Type:** React 19 SPA, TypeScript, npm/Vite (not Gradle).
- **Responsibility:** the entire user-facing surface. Feature-based folders: analytics, calc,
  carbs, dashboard, measures, profiles, report, timeframe, timeline, treatments, users.
- **Depends on (runtime HTTP):** analyze, calc, measures, profiles, treatments, carbs, users;
  Keycloak (OIDC login).
- **Size:** 115 `.tsx`, 34 hand-written `.ts`, 20 generated `.ts` (typed clients for 4 of 8 backends).

## Build Tooling

### build-logic (Gradle included build)

- **Type:** Gradle included build carrying shared conventions.
- **Responsibility:** three precompiled script plugins (`kdiab.kotlin-base`,
  `kdiab.ktor-service`, `kdiab.ktor-db-service`) + `UpstreamSpecExtensions.kt`
  (`registerUpstreamSpec`) for the fan-out services.
- **Consumed by:** every backend module via its convention plugin.

## Dependency Summary Matrix (runtime HTTP fan-out)

| Consumer | measures | profiles | treatments | carbs | users | Keycloak |
|---|:---:|:---:|:---:|:---:|:---:|:---:|
| kdiab-analyze | X | X | X | | | |
| kdiab-calc | | X | | | | |
| kdiab-nightscout | X | X | X | X | X | |
| kdiab-users | | | | | | X (Admin API) |
| kdiab-ui | X | X | X | X | X | X (OIDC) |

Cross-service coupling is **spec-only at build time** (generated typed clients from OpenAPI)
and **HTTP-only at runtime** — no shared domain code and no shared database. This is the
central decoupling property of the platform.
