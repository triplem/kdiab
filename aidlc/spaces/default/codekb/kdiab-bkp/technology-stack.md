# Technology Stack — kdiab (T1D Management Platform)

## Overview

kdiab is a **Kotlin/Ktor + React/TypeScript** platform. Backend dependency versions are
centralised in a single Gradle version catalog (`gradle/libs.versions.toml`); frontend
versions are pinned in `kdiab-ui/package.json`. Versions below are drawn from the scanned
catalog and lockfiles as of HEAD `d6c8866b` (2026-08-16).

## Languages and Runtimes

| Language / Runtime | Version | Scope |
|---|---|---|
| Kotlin | 2.3.20 | all backend services + shared library + build-logic |
| JVM toolchain | 21 | backend compile/run target |
| TypeScript | ~6.0.3 (strict) | kdiab-ui frontend |
| Node (build image) | node:26-alpine | UI Docker build stage |

## Backend Frameworks and Libraries

| Library | Version | Purpose |
|---|---|---|
| Ktor (CIO) | 3.5.0 | HTTP server + client engine |
| Exposed ORM | 1.2.0 | SQL DSL/DAO (core, jdbc, kotlin-datetime, json) |
| HikariCP | 7.0.2 | JDBC connection pool |
| PostgreSQL JDBC | 42.7.10 | database driver |
| Liquibase | 5.0.2 | schema migrations (run via dedicated superuser container) |
| kotlinx-serialization | 1.10.0 | JSON (ser)/deserialization |
| kotlinx-datetime | 0.7.1 | `Instant` / `LocalTime` in domain code |
| kotlinx-coroutines | 1.10.2 | structured concurrency / fan-out |
| kotlin-logging | 8.0.01 | logging facade |
| Logback | 1.5.32 | logging backend (+ logback-contrib JSON 0.1.5) |
| OpenTelemetry SDK | 1.51.0 | tracing (+ semconv 1.30.1) |
| OpenTelemetry Ktor instrumentation | 2.27.0-alpha | Ktor tracing (alpha intentional/documented) |
| auth0 jwk / jwt | (per catalog) | JWKS-based JWT validation |

## Build, Codegen, and Quality Tooling

| Tool | Version | Purpose |
|---|---|---|
| Gradle | 9.5.1 (Kotlin DSL) | composite build via `includeBuild` |
| openapi-generator | 7.21.0 | Ktor server stubs + models (kotlin-server, kotlinx_serialization) |
| Detekt | 1.23.8 | Kotlin static analysis (SARIF output) |
| Kover | 0.9.8 | code coverage (80% line floor; NOT JaCoCo) |
| SonarQube (Sonar plugin) | 7.2.3.7755 | SonarCloud quality analysis |
| Asciidoctor | 4.0.5 | AsciiDoc docs build |
| CycloneDX | 3.2.2 | SBOM generation |

### Security-Pinned Transitives (CVE remediation)

Forced via `kdiab.kotlin-base` constraints across all services:

| Dependency | Version | CVE |
|---|---|---|
| Jackson | 2.21.4 | CVE-2026-54512 / CVE-2026-54513 |
| Handlebars | 4.5.2 | CVE-2026-55760 |

## Test Stack (backend)

| Framework | Version | Tier |
|---|---|---|
| JUnit 5 (Jupiter) | (per catalog) | unit + integration |
| MockK | 1.14.9 | unit mocking |
| H2 | 2.4.240 | in-memory DB for integration tests |
| Kotest | 6.1.11 | e2e tests |
| ktor-server-test-host / ktor-client-mock | (per catalog) | Ktor test harness / client mocking |

## Frontend Stack (kdiab-ui)

| Library | Version | Purpose |
|---|---|---|
| React + react-dom | 19.2.8 | UI framework |
| Vite | 8.2.1 | build tool / dev server |
| Vitest (+ @vitest/coverage-v8) | 4.1.10 | unit testing + coverage |
| @tanstack/react-query | 5.101.4 | server-state / data fetching |
| react-hook-form | 7.85.0 | forms |
| zod (+ @hookform/resolvers) | 4.3.6 | schema validation |
| react-oidc-context | 3.3.1 | OIDC auth context |
| oidc-client-ts | 3.5.0 | OIDC client |
| recharts | 3.10.1 | charts (timeline, AGP) |
| i18next (+ react-i18next) | 26 | i18n (en + de) |
| axios | 1.17.0 | HTTP client |
| date-fns | 4.4.0 | date handling |
| lucide-react, sonner | (per lockfile) | icons, toasts |
| ESLint (+ typescript-eslint) | 9.39.5 (+ v8) | linting |
| Playwright | 1.62.1 | e2e testing |
| @openapitools/openapi-generator-cli, cyclonedx-npm | (per lockfile) | client codegen, SBOM |

## Infrastructure and Platform

- **Identity:** Keycloak (JWT/JWKS; realm + custom login theme in `config/`).
- **Database:** PostgreSQL, initialised via `config/postgres/01-init-databases.sh`, migrated by
  per-service Liquibase containers, seeded by `config/postgres/02-seed-data.sql`.
- **Packaging:** Ktor `buildFatJar`; per-service multi-stage Dockerfiles — backends on
  `eclipse-temurin:21-jre-alpine` (pinned by digest + `apk upgrade`), UI on `node:26-alpine` →
  `nginx:alpine`. All 9 modules have Dockerfiles.
- **Observability:** OpenTelemetry traces exported via gRPC to a collector; Jaeger UI.
- **Models / AI-DLC provider note:** the AI-DLC tooling for this project runs on the default
  Anthropic provider (no AWS Bedrock / Amazon-hosted models) — unrelated to the application
  runtime, recorded here for completeness of the platform's tooling context.

## Version Health Note

Library versions are centralised and consistent via the version catalog. **Service module
versions, however, drift** (kdiab-measures 0.0.1, seven services at 0.1.0, kdiab-common
0.0.0-SNAPSHOT) with no unified platform version — flagged as a debt signal in
`code-quality-assessment.md`.
