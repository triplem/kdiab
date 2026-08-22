# Build Instructions — U1 Jackson-free JWT (#1606)

Consumes `../jackson-free-jwt/code-generation/code-generation-plan.md` and
`../jackson-free-jwt/code-generation/code-summary.md`. Branch: `feature/1606-jackson-free-jwt`.

## Prerequisites

- JDK 21 (Gradle toolchain pins `jvmToolchain(21)`).
- The Gradle wrapper (`./gradlew`, Gradle 9.5.1) — do not use a system Gradle.
- No network services needed to build; unit/integration/e2e tests use in-process Ktor + H2 (no external Postgres/Keycloak).

## Change surface built by this stage

| Area | File(s) |
|---|---|
| Core auth (main) | `kdiab-common/.../plugins/Security.kt` (Nimbus provider — core slice) |
| Main (dead-import) | `kdiab-profiles/.../adapters/inbound/web/ProfileRoutes.kt` |
| Build config | `gradle/libs.versions.toml` (bundle − `ktor-server-auth-jwt`; + `nimbus-jose-jwt`), `build-logic/.../kdiab.kotlin-base.gradle.kts` (− 2 jackson pins), `build-logic/.../kdiab.ktor-service.gradle.kts` (+ `testImplementation(nimbus)`) |
| Tests | 25 service test files migrated to Nimbus + 7 startup/config tests secret-lengthened |
| Docs | `docs/adr/ADR-023-jackson-free-jwt-verification.adoc` |

## Build commands

This is a Gradle **composite build** (`includeBuild` per service). Run per module from the
service directory (per the root `CLAUDE.md`), not a single root aggregate.

```bash
# Compile everything (main + generated API stubs) for one service
cd kdiab-<service> && ./gradlew compileKotlin

# Compile all three test source sets for a service
cd kdiab-<service> && ./gradlew compileTestKotlin compileIntegrationTestKotlin compileE2eTestKotlin

# Shared library (all services depend on it via includeBuild substitution)
cd kdiab-common && ./gradlew compileKotlin test
```

## Build verification

- All 9 modules (`kdiab-common` + 8 services) compile main + `test`/`integrationTest`/`e2eTest`.
- After the change, `com.auth0:java-jwt`, `com.auth0.jwk:jwks-rsa`, `jackson-databind`, and
  `jackson-core` are absent from every service's `runtimeClasspath`; `handlebars` stays pinned at 4.5.2.

  ```bash
  cd kdiab-<service> && ./gradlew dependencyInsight --dependency jackson-databind --configuration runtimeClasspath
  # expect: "No dependencies matching given input were found in configuration ':runtimeClasspath'"
  ```

## Troubleshooting

- **`generateProfilesModels` / nightscout upstream models resolve to the wrong service spec** — a
  PRE-EXISTING flaky composite-build `apiSpec` resolution race. `gradle clean` does NOT clear the
  Gradle build cache, so a poisoned entry re-fails deterministically. Recover with:
  ```bash
  cd kdiab-nightscout && ./gradlew clean compileKotlin --no-build-cache --rerun-tasks
  ```
  This is unrelated to #1606 (nightscout builds green with a clean cache) — flagged as a follow-up.
- **`jwt.secret must be at least 32 bytes for HS256`** at test startup — Nimbus HS256 enforces the
  RFC-7518 256-bit minimum. Every test secret was lengthened to ≥32 bytes; if a new test sets a
  shorter `jwt.secret` with `jwt.test=true`, it will fail fast by design.
