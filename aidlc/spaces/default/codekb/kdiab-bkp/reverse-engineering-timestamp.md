# Reverse Engineering — Freshness Marker

## Analysis Metadata

| Field | Value |
|---|---|
| Full scan performed | 2026-08-16 (commit d6c8866b) — enterprise scope, whole monorepo |
| Last freshness refresh | 2026-08-23 (commit 88428807) — security-patch intent #1588 |
| Repository | kdiab-bkp (single Git repo) |
| Branch | main |
| Project type | Brownfield |
| Refresh intent | "Guard the test-mode HMAC JWT toggle out of production" (slug: jwt-test-guard, FIND-SEC-001 / #1588) |
| Refresh performer | AI-DLC reverse-engineering stage — freshness refresh (existing codekb reused + JWT-detail delta applied) |

## Refresh Note (2026-08-23, intent #1588 — jwt-test-guard)

This pass is a **freshness refresh**, not a re-scan. Source changes since the 2026-08-22 refresh
(commit 209cd817 → 88428807) are non-structural: `.claude/rules/logging.md` (docs),
`backend-ci-reusable.yml` (CI), and 5 dead jackson lines removed from `gradle/libs.versions.toml`
(#1608 / #1620). **No service module added/removed; no `settings.gradle.kts`/`build.gradle.kts`
structural change; the JWT/security path is behaviourally unchanged.** The 8 content artifacts are
reused as-is.

**Applied delta (in-scope, this intent works the JWT path):** the JWT-library detail flagged stale by
the #1617 note is now refreshed — `dependencies.md` and `technology-stack.md` updated from
`auth0 jwk/jwt` + jackson force-pin to **Nimbus JOSE+JWT 10.0.1** (JWKS prod / HMAC test) with the
jackson force-pin retirement recorded (#1606 / #1608).

**Verified for #1588 (FIND-SEC-001):** `kdiab-common/.../plugins/Security.kt` reads `jwt.test` config
(line 68), and selects `HmacTokenVerifier` when `isTest` else `JwksTokenVerifier` (line 242). A guard
exists requiring `jwt.secret` when test-mode is on, but **no guard prevents test-mode being enabled in
a production environment** — the finding stands.

### Known residual delta (out of scope for #1588)

- **#1605 logback**: `dependencies.md`/`technology-stack.md` still list `logback-contrib JSON 0.1.5`;
  #1605 switched backends to Logback's built-in `JsonEncoder` and removed logback-contrib. Not
  refreshed here (unrelated to the JWT guard). Refresh on the next logging-adjacent RE pass.

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
