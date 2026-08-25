# Build & Test Results — jwt-test-guard (#1588 / FIND-SEC-001)

Run: 2026-08-25, local, against the uncommitted working tree on `main`.

## Per-module results (all 9 backend modules)

| Module | Task | Result | Notes |
|---|---|---|---|
| kdiab-common | `test` | ✅ PASS | Guard compiles; `JwtAuthenticationParityTest` green. `Security.kt` Detekt-clean. |
| kdiab-profiles | `test` (+`SecurityConfigTest`) | ✅ PASS | AC-1 (new negative), AC-2, AC-4 all PASS. |
| kdiab-analyze | `test` + compile int/e2e | ✅ PASS | EXIT 0 |
| kdiab-calc | `test` + compile int/e2e | ✅ PASS | EXIT 0 |
| kdiab-carbs | `test` + compile int/e2e | ✅ PASS | EXIT 0 |
| kdiab-measures | `test` + compile int/e2e | ✅ PASS | EXIT 0 |
| kdiab-treatments | `test` + compile int/e2e | ✅ PASS | EXIT 0 |
| kdiab-users | `test` + compile int/e2e | ✅ PASS | EXIT 0 |
| kdiab-nightscout | `test` + compile int/e2e | ✅ PASS* | *after clearing a pre-existing stale build-cache (see below); my test edits compile + unit tests pass |

`kdiab-ui` (frontend): **not built** — no TypeScript/frontend change in this patch.

## My-change verification (independent)

- **Guard:** `readJwtConfig()` reads `jwt.allowTestMode` (deny-by-default) and the opt-in `check` fires
  BEFORE the secret `check` (SR-7/TD-4). `git diff` confirms one runtime file changed.
- **Coverage:** 36 files set `jwt.test=true`; a scan for any such file missing `allowTestMode` returned
  empty (the single deliberate gap is the AC-1 guard-under-test line in `SecurityConfigTest.kt`).
- **New test:** `application fails to start when jwt test mode is enabled without allow test mode opt-in`
  PASSES (asserts `IllegalStateException` naming `jwt.allowTestMode`/`JWT_ALLOW_TEST_MODE`).

## Pre-existing conditions (NOT introduced by this change)

1. **Local Detekt `UnreachableCode` false-positives** — `detektMain` fails on `kdiab-common`
   (`RateLimit.kt`/`AuditRoutes.kt`/`Tracing.kt`, ~21) and `kdiab-profiles` (`Application.kt`,
   `ProfileMapper.kt`, 2). All in files this change never touches; `Security.kt` is Detekt-clean. Known
   local Detekt-version discrepancy vs the CI-passing baseline (global CLAUDE.md; #1579-adjacent).
2. **nightscout #1614 composite-build codegen race** — the first run failed `:compileKotlin` with
   `Unresolved reference 'Profile'/'CreateProfileRequest'` + `Failed to get the schema name: null` in
   untouched main files (`NightscoutV3Mapper.kt`, `ProfilesClient.kt`). Root cause: Gradle's build cache
   had served a **wrong** `upstream-profiles` generation (carbs/food model types instead of profile
   types) for the `registerUpstreamSpec("profiles", ...)` client — the exact flaky race tracked in
   **#1614**. Cleared by `rm -rf build/generated/upstream-profiles` + `clean compileKotlin --rerun-tasks
   --no-build-cache`; the correct types (`Profile.kt`, `CreateProfileRequest.kt`, …) then generate and
   nightscout builds green. Independent of this patch (nightscout's main compile never sees test-file
   edits, and this change touches only `kdiab-common` in main source).

## Authoritative gate

The full platform gate (all 9 backends: unit + integration + e2e + Detekt SARIF + Kover ≥80% + Trivy +
CodeQL + SonarCloud + SBOM) runs in **GitHub Actions CI** on the deployment-execution PR. Per the team
rule, the PR must not merge until every check is green. CI runs on a fresh checkout (no local stale
build cache), so the #1614 stale-cache condition does not apply there.
