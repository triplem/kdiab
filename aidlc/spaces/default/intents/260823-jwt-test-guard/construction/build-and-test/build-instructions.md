# Build Instructions — jwt-test-guard (#1588 / FIND-SEC-001)

## Scope of change
One runtime file (`kdiab-common/.../plugins/Security.kt` — the `jwt.allowTestMode` guard) plus ~35
test-fixture edits across all 9 backend Gradle modules. `kdiab-ui` (frontend) is **untouched** — no
`npm run build` needed for this change.

## Composite build note
kdiab is a composite build via `includeBuild` — **each module is its own Gradle root**. Detekt and most
tasks run per-module, not at the repo root (see global CLAUDE.md).

## Build commands

```bash
# Per affected module (all 9 backends), from the module dir:
cd kdiab-common     && ./gradlew build          # shared library — build first
cd kdiab-measures   && ./gradlew build
cd kdiab-profiles   && ./gradlew build
cd kdiab-treatments && ./gradlew build
cd kdiab-analyze    && ./gradlew build
cd kdiab-carbs      && ./gradlew build
cd kdiab-calc       && ./gradlew build
cd kdiab-nightscout && ./gradlew build
cd kdiab-users      && ./gradlew build

# Publish check (kdiab-common changed → docker-publish.yml runs `publish` on main):
cd kdiab-common && ./gradlew publishToMavenLocal
```

## Local Detekt caveat (pre-existing, NOT this change)
Local `detektMain` currently reports pre-existing `UnreachableCode` false-positives on `kdiab-common`
(`RateLimit.kt`/`AuditRoutes.kt`/`Tracing.kt`) and `kdiab-profiles` — a local Detekt-version discrepancy
absent from the CI-passing baseline. `Security.kt` (the only main file changed) is Detekt-clean. Verify
by grepping the detekt report for changed files; CI Detekt (with committed baseline) is authoritative.

## Authoritative gate
The full quality gate (all 9 backends: tests + Detekt SARIF + Kover ≥80% + Trivy + CodeQL + SonarCloud +
SBOM) runs in **GitHub Actions CI** on the deployment-execution PR. Per team rule, wait for every check
green before merge.
