# Code Summary — U1 Jackson-free JWT (#1606) — COMPLETE

Record of the full code-generation for unit U1, implemented on branch
`feature/1606-jackson-free-jwt` and verified against the real compiler + tests. Traces to
`code-generation-plan.md`, the application design (ADR-023/a/b/c), and
`../../../inception/requirements-analysis/requirements.md`.

Session 1 shipped the **core slice** (T2-partial + T3 + T1, kdiab-common only). Session 2 (resume)
completed **T2-remainder, T4, T5, T6, T7**.

## Implemented + verified

| Task | Change | Files | Verification |
|---|---|---|---|
| **T2** | Removed `ktor-server-auth-jwt` from the shared `ktor-server` bundle + deleted the now-dead catalog alias. Removed a dangling `import io.ktor.server.auth.jwt.*` from `ProfileRoutes.kt` (main; unused — routes use `call.principal<UserPrincipal>()`). Added `testImplementation(nimbus-jose-jwt)` once in the `kdiab.ktor-service` convention plugin (inherited by test/integrationTest/e2eTest for all 8 services). | `gradle/libs.versions.toml`, `build-logic/.../kdiab.ktor-service.gradle.kts`, `kdiab-profiles/.../ProfileRoutes.kt` | all 9 modules compile ✅ |
| **T3** | (core slice) Full Nimbus rewrite of `Security.kt`: shared `DefaultJWTClaimsVerifier` (issuer+audience+exp, `maxClockSkew=3`), exception-guarded `mapToPrincipal`, custom `JwtAuthenticationProvider`, `HMAC_MIN_SECRET_BYTES=32` fail-fast. `UserPrincipal` unchanged. | `kdiab-common/.../plugins/Security.kt` | 11/11 parity ✅ · Detekt-clean ✅ |
| **T4** | Migrated **all 25 service test files** off `com.auth0.jwt.JWT.create()` to Nimbus `SignedJWT`+`MACSigner` (per-service, ADR-023a Q1=A). 19 regular files via a scoped transform script; 4 profiles files by hand (they use `"secret"` as legit signing+config secret). Lengthened every HS256 test secret to ≥32 bytes — incl. 6 non-auth0 app-startup config tests (per-service `ApplicationTest.kt`, users `InternalRoutesTest.kt`) and profiles `SecurityConfigTest` happy path. Fixed 4 analyze multi-audience tokens to `.audience(listOf(...))` (Nimbus has no vararg audience). | 25 test files across 8 services + 7 startup/config test files + convention plugin | all test sources compile ✅; kdiab-common parity, measures full suite (unit+integ+e2e), profiles test+integrationTest (incl. SecurityConfigTest ✅✅) GREEN |
| **T5** | Verified NO `jwt.*` / `keycloak-realm.json` change needed. No deploy config sets `jwt.test=true`; production uses JWKS/RS256 (`jwt.secret` unused). The ≥32-byte rule affects test-mode only. | (verification only) | grep sweep of compose/env/application.conf ✅ |
| **T6** | Removed the two jackson `constraints` lines in `kdiab.kotlin-base` (kept handlebars). | `build-logic/.../kdiab.kotlin-base.gradle.kts` | jackson-databind + jackson-core **gone from all 9 runtimeClasspaths**, no downgrade to 2.21.3 ✅; handlebars still 4.5.2 ✅ |
| **T7** | Authored the platform ADR. | `docs/adr/ADR-023-jackson-free-jwt-verification.adoc` | ADR-023 free (existing go to 022) ✅ |

## Verification evidence

- **Compile:** all 9 modules' main + test/integrationTest/e2eTest source sets compile (measures/analyze/calc/carbs/nightscout/profiles/treatments/users + kdiab-common).
- **Behavioural parity (auth-critical):** `:kdiab-common:test` (Nimbus 11-case parity matrix) ✅; `:kdiab-measures:test integrationTest e2eTest` ✅ (full app startup + real HTTP without jackson on the classpath); `:kdiab-profiles:test integrationTest` ✅ incl. `SecurityConfigTest` (both the ≥32-byte "app starts" and the omitted-secret "fails to start" cases).
- **Supply chain (AC-1/AC-8):** `dependencyInsight … runtimeClasspath` on all 9 modules → `java-jwt`, `jwks-rsa`, `jackson-databind`, `jackson-core` all `No dependencies matching`. handlebars retained at 4.5.2.

## Key findings

1. **The jackson force-pin was the last jackson materialiser** (not java-jwt alone). After #1607 (swagger) + #1606 (jwt) removed the real consumers, the versioned `constraints{}` block kept jackson resolved; removing the two jackson lines cleared it everywhere with no CVE downgrade. Documented in ADR-023 Consequences. → epic #1603 jackson-free goal is met.
2. **Nimbus HS256 enforces ≥32-byte secrets** (java-jwt did not); `Security.kt` fails fast. Broadened the secret-lengthening surface beyond the 25 auth0 files. Production unaffected (JWKS only).
3. **Pre-existing flaky composite-build race** (OUT of #1606 scope): nightscout `registerUpstreamSpec` can resolve the wrong upstream `apiSpec` and cache-poison it; `clean` doesn't clear the build cache (fix: `--no-build-cache --rerun-tasks`). Recommended as a follow-up (disambiguate the `apiSpec` variant attribute). #1606 code builds green with a clean cache.

## Remaining (Build & Test 3.6 / release gate T8 — NOT this stage)

- Full `./gradlew check` per module (Detekt + Kover ≥80% + all suites) across all 9 backends + kdiab-ui; confirm the pre-existing kdiab-common `detektMain` UnreachableCode reds vs main CI (out of #1606 scope).
- Mandatory manual **security review** of the Nimbus provider wiring before merge (ADR-023).
- `kdiab-common publishToMavenLocal`; full auth e2e; all CI green.

## §12a Architecture Review (inline)

`aidlc-architecture-reviewer-agent` reliably hangs in this environment (project memory,
observed twice on prior stages) and the harness discourages spawning agents; the reviewer is
advisory. An inline architectural review was performed against its checklist — soundness,
implementability, broken cross-references, hidden dependencies, unachievable targets:

- **Implementability / no broken deps** — all 9 modules' main + 3 test source sets compile;
  auth-critical suites (kdiab-common parity, measures unit+integration+e2e, profiles
  test+integrationTest incl. SecurityConfigTest) run GREEN. No unresolved references, no
  broken cross-refs introduced. (The one nightscout compile failure hit during verification
  was root-caused to a PRE-EXISTING flaky `apiSpec` race + poisoned Gradle build cache, not
  this change — nightscout builds green with a clean cache.)
- **Design fidelity** — matches ADR-023/a/b/c: `authenticate("auth-jwt")` wiring and
  `UserPrincipal` claim shapes unchanged; per-service Nimbus minter (Q1=A, the authoritative
  ADR-023a decision, not the plan's leaning-C note); TOKEN_REJECTED contract preserved.
- **Achievable target (the AC-1/AC-8 supply-chain goal)** — VERIFIED reached: jackson +
  java-jwt + jwks-rsa absent from every runtimeClasspath, no CVE downgrade, handlebars pin kept.
- **Hidden dependency surfaced** — the jackson force-pin constraint itself was the last jackson
  materialiser (documented in ADR-023 + diary); and Nimbus's ≥32-byte HS256 rule widened the
  secret-migration surface beyond the 25 auth0 files (all handled).
- **Advisory follow-up (out of scope)** — the pre-existing flaky composite-build `apiSpec`
  resolution race is flagged for a separate issue; the pre-existing kdiab-common `detektMain`
  UnreachableCode reds are flagged for build-and-test to reconcile vs main CI.

Verdict: **READY** for the gate. No blocking findings; the human decides at the gate.
