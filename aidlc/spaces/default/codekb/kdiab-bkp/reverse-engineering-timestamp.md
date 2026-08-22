# Reverse Engineering — Freshness Marker

## Analysis Metadata

| Field | Value |
|---|---|
| Full scan performed | 2026-08-16 (commit d6c8866b) — enterprise scope, whole monorepo |
| Last freshness refresh | 2026-08-22 (commit 209cd817) — refactor intent #1617 |
| Repository | kdiab-bkp (single Git repo) |
| Branch | main |
| Project type | Brownfield |
| Refresh intent | "fix the Monorepo Release workflow artifact-name mismatch" (slug: release-workflow-fix, #1617) |
| Refresh performer | AI-DLC reverse-engineering stage — freshness refresh (existing codekb reused) |

## Refresh Note (2026-08-22, intent #1617)

This pass is a **freshness refresh**, not a re-scan. The 8 codekb content artifacts
(business-overview, architecture, code-structure, api-documentation, component-inventory,
technology-stack, dependencies, code-quality-assessment) from the 2026-08-16 full-monorepo scan are
**reused as-is** — the monorepo module/service structure is unchanged and remains accurate. Rationale:
Minimal-depth refactor scope; the change under this intent is a ~16-line CI-workflow name fix
(`.github/workflows/release.yml`), so a full 9-module re-scan is disproportionate.

### Known deltas since the 2026-08-16 full scan (out of scope for #1617)

- **#1606 jackson-free JWT** merged 2026-08-21 (commit 209cd817): `kdiab-common` `Security.kt` now uses
  a custom Nimbus (`com.nimbusds:nimbus-jose-jwt`) `AuthenticationProvider`; `com.auth0:java-jwt`,
  `jwks-rsa`, and jackson removed from the runtime classpath; jackson force-pins removed (handlebars
  pin retained). ⇒ `dependencies.md` and `technology-stack.md` are slightly stale on the JWT-library
  detail only. Not regenerated here — irrelevant to the CI release-workflow fix. Refresh those on the
  next in-scope reverse-engineering pass.

## Scope of the codekb (from the 2026-08-16 full scan)

Covered the **entire monorepo**: 9 backend Gradle modules (kdiab-common shared library + 8 runnable
Ktor services), the React SPA (kdiab-ui), Liquibase migrations, Keycloak config, and the
`.github/workflows/` CI/CD pipeline set. Directly re-verified for #1617: the CI→release artifact flow
(`backend-ci-reusable.yml` uploads `kdiab-<service>-backend-{image,bom}`; `release.yml` downloads the
un-prefixed `<service>-backend-{image,bom}` — the bug).
