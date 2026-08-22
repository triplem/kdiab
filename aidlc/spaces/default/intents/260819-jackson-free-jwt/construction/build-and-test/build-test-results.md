# Build & Test Results — U1 Jackson-free JWT (#1606)

Executed 2026-08-20 on branch `feature/1606-jackson-free-jwt`. Full release gate
`./gradlew check` (test + integrationTest + e2eTest + Detekt + Kover ≥80% `koverVerify`) per module.

## Release gate — all 9 modules GREEN

| Module | `./gradlew check` | Notes |
|---|---|---|
| kdiab-common | ✅ BUILD SUCCESSFUL | Nimbus parity matrix + provider |
| kdiab-measures | ✅ BUILD SUCCESSFUL | full suite |
| kdiab-profiles | ✅ BUILD SUCCESSFUL | incl. `SecurityConfigTest` (≥32-byte secret) |
| kdiab-treatments | ✅ BUILD SUCCESSFUL | full suite |
| kdiab-carbs | ✅ BUILD SUCCESSFUL | full suite |
| kdiab-calc | ✅ BUILD SUCCESSFUL | full suite |
| kdiab-users | ✅ BUILD SUCCESSFUL | full suite |
| kdiab-analyze | ✅ BUILD SUCCESSFUL* | *green with fresh generation (`clean check --rerun-tasks`), 1m11s |
| kdiab-nightscout | ✅ BUILD SUCCESSFUL* | *green with fresh generation (`clean check --rerun-tasks`), 43s |

**Verdict: the #1606 change passes the full quality gate on every backend** — tests, Detekt, and
Kover ≥80% all green across all 9 modules.

## Caveat — pre-existing flaky `apiSpec` generation race (NOT #1606)

On the first batch run (`check --no-build-cache`, without `--rerun-tasks`), **analyze** and
**nightscout** FAILED to compile `TreatmentsClient.kt` — unresolved references to the generated
`upstream-treatments` client models (`listTreatments`, `createTreatment`, `status`, `body`, …).
Root cause = the pre-existing flaky composite-build `registerUpstreamSpec` race: an upstream
`apiSpec` can non-deterministically resolve/generate the wrong (or empty) spec, and the poisoned
output persists in `build/generated` (treated up-to-date; `--no-build-cache` disables only cache
*pull*, not re-run). Forcing regeneration (`clean … --rerun-tasks`) produced correct models and
both modules went green. This race is unrelated to #1606 (it also reproduced from my own
diagnostic build-state poisoning earlier) and matches the existing "flaky openApiGenerate vs
compileKotlin" memory note. **Follow-up (separate issue):** disambiguate the `apiSpec` variant
selection (e.g. a per-service Category attribute value) so upstream generation is deterministic.

> **CI implication:** on a clean CI checkout there is no poisoned `build/generated`, so the
> generation runs once; the race is probabilistic. If CI intermittently fails analyze/nightscout
> on `upstream-*` client references, it is this race — re-run, or track the follow-up. Not a
> #1606 regression.

## Supply-chain verification (AC-1 / AC-8) — PASS

`dependencyInsight … --configuration runtimeClasspath` across all 9 modules:
`jackson-databind`, `jackson-core`, `com.auth0:java-jwt`, `com.auth0.jwk:jwks-rsa` → **all absent**
(no downgrade to the CVE-vulnerable jackson 2.21.3). `handlebars` retained at 4.5.2.

## Security review — PASS (1 non-blocking note)

See `security-test-instructions.md` § A. Algorithm confusion / `alg=none` blocked (pinned key
selectors), issuer+audience+exp enforced, HMAC ≥32-byte enforced, JWKS HTTPS enforced, no
secret/PII logging, fail-closed on malformed claims. Non-blocking: required-claims uses `emptySet()`
(java-jwt parity — `exp` enforced when present); requiring `exp` presence is an optional platform-wide
hardening for a separate issue.

## Environment notes

- `SEVERE: Failed to export spans … UNIMPLEMENTED` in test logs is benign — no OTEL collector runs
  during tests (set `OTEL_TRACES_EXPORTER=none` to silence). Not a test failure.
- Gradle 9.6 deprecation warnings (`registering`/`getting` in build-logic) are pre-existing and
  unrelated to #1606.
