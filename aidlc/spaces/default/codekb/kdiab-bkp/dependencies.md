# Dependencies — kdiab (T1D Management Platform)

## Dependency Model

kdiab keeps two dependency planes strictly separated:

- **Internal coupling** is **spec-only at build time** (a service generates a typed client from
  another service's OpenAPI spec) and **HTTP-only at runtime**. No two services share domain
  code or a database.
- **External dependencies** are centralised in a single Gradle version catalog
  (`gradle/libs.versions.toml`) for the backend and `package.json` for the frontend, with
  security-critical transitives pinned by force.

## Internal Cross-Package Dependencies

### Build-time (Gradle)

- **Every backend service** declares `implementation(kdiab-common)` — the one universal
  internal library dependency.
- **Spec fan-out** (typed-client generation via `registerUpstreamSpec`):
  - **kdiab-analyze** → measures, treatments, profiles specs.
  - **kdiab-calc** → profiles spec.
  - **kdiab-nightscout** → measures, treatments, carbs, profiles, users specs (adds a Ktor client).
- **`dependencySubstitution`** (in root `settings.gradle.kts`) swaps the published
  `kdiab-*-spec` coordinates for local project dependencies so that, during development, the
  fan-out services resolve typed clients from the in-repo specs rather than GitHub Packages.

### Runtime (HTTP)

| Consumer | Upstreams called (JWT forwarded where noted) |
|---|---|
| kdiab-analyze | measures, treatments, profiles (JWT forwarded unchanged) |
| kdiab-calc | profiles (JWT forwarded unchanged) |
| kdiab-nightscout | measures, treatments, carbs, profiles, users |
| kdiab-users | Keycloak Admin API |
| kdiab-ui | analyze, calc, measures, profiles, treatments, carbs, users; Keycloak (OIDC) |

The domain services (measures, profiles, treatments, carbs) have **no outbound internal
dependencies** at runtime — they own their data and are pure providers.

## External Dependencies — Backend (via version catalog)

| Group | Key artifacts | Version |
|---|---|---|
| Web | Ktor CIO (server + client) | 3.5.0 |
| Persistence | Exposed (core/jdbc/kotlin-datetime/json) | 1.2.0 |
| Persistence | HikariCP | 7.0.2 |
| Persistence | PostgreSQL JDBC | 42.7.10 |
| Migrations | Liquibase | 5.0.2 |
| Kotlin ecosystem | kotlinx-serialization / datetime / coroutines | 1.10.0 / 0.7.1 / 1.10.2 |
| Logging | kotlin-logging / Logback / logback-contrib JSON | 8.0.01 / 1.5.32 / 0.1.5 |
| Tracing | OpenTelemetry SDK / semconv / Ktor instrumentation | 1.51.0 / 1.30.1 / 2.27.0-alpha |
| Auth | Nimbus JOSE + JWT (JWKS prod / HMAC test) | 10.0.1 |

### Security-Pinned Transitives (forced)

`kdiab.kotlin-base` forces these across every service to remediate CVEs:

- **Handlebars 4.5.2** — CVE-2026-55760 (pulled transitively by openapi-generator tooling).

> Jackson force-pin retired (#1606 / #1608): Jackson is no longer on the runtime classpath — JWT
> auth migrated to Nimbus (#1606) and Swagger to a static UI (#1607), completing epic #1603. The
> jackson `libs.versions.toml` catalog entries and the force-pin were dropped (handlebars pin kept).

One residual CVE is documented as a false positive in `.trivyignore` with an NVD justification,
and `docs/security/accepted-risks.md` tracks accepted risks.

## External Dependencies — Frontend (kdiab-ui)

| Concern | Artifacts | Version |
|---|---|---|
| Framework | react / react-dom | 19.2.8 |
| Build | Vite | 8.2.1 |
| Data fetching | @tanstack/react-query | 5.101.4 |
| Forms / validation | react-hook-form / zod / @hookform/resolvers | 7.85.0 / 4.3.6 |
| Auth | react-oidc-context / oidc-client-ts | 3.3.1 / 3.5.0 |
| Charts | recharts | 3.10.1 |
| i18n | i18next / react-i18next | 26 |
| HTTP | axios | 1.17.0 |
| Dates | date-fns | 4.4.0 |
| Test | Vitest / Playwright | 4.1.10 / 1.62.1 |

## Dependency Management and Supply Chain

- **Central version catalog** — backend versions live only in `gradle/libs.versions.toml`;
  library versions are consistent across services (only service *module* versions drift).
- **Dependabot** — configured for every ecosystem (gradle ×8, npm, docker ×10, GitHub Actions),
  with grouped updates.
- **SBOM** — CycloneDX (Gradle) and cyclonedx-npm (UI) produce SBOMs uploaded in CI.
- **Vulnerability scanning** — Trivy runs in CI and exits non-zero on CRITICAL/HIGH image findings.
- **GitHub Actions pinned to commit SHAs** — reducing supply-chain exposure from mutable tags.

## Dependency Risk / Improvement Notes

- **`registerUpstreamSpec` + `dependencySubstitution`** is bespoke build-logic that the three
  fan-out services (analyze, calc, nightscout) must keep in sync on every dependency/version
  change — a coordination cost flagged for simplification.
- **UI client-generation gap** — carbs, calc, nightscout, and users features depend on
  hand-written Axios clients rather than generated ones, weakening the spec-first contract for
  half the internal API surface.
- **OpenTelemetry Ktor instrumentation is on an `-alpha`** — intentional and documented, but a
  standing upgrade watch item.
