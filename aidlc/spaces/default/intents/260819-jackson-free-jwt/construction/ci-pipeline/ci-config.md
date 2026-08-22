# CI Configuration — U1 Jackson-free JWT (#1606)

Consumes `../jackson-free-jwt/code-generation/code-summary.md`,
`../build-and-test/build-and-test-summary.md`, `../build-and-test/build-test-results.md`.

## Decision: use the existing pipeline unchanged

#1606 is a code + build-config change (Nimbus JWT provider, `ktor-server-auth-jwt` shed, jackson
force-pin removed, per-service test-minter migration). The platform's GitHub Actions pipeline already
covers every affected artifact. **No workflow file is added or modified for #1606.**

## How #1606 flows through the existing CI

| Workflow | Role for #1606 |
|---|---|
| `ci-<service>-backend.yml` (×8) + `ci-common-publish.yml` | Trigger `backend-ci-reusable.yml` on PR/push for each of the 9 affected modules. |
| `backend-ci-reusable.yml` | `./gradlew :check :koverVerify :buildFatJar :cyclonedxBom` → tests + Detekt + Kover ≥80% + fat jar + SBOM; Detekt SARIF → Security tab; SonarCloud; Docker build; **Trivy CRITICAL/HIGH (`exit-code 1`)** → SARIF; SBOM upload. |
| `codeql-backend.yml` | CodeQL SAST over the changed Kotlin (incl. the new `Security.kt` auth wiring). |
| `e2e.yml` | Cross-service e2e (auth flows across services with one forwarded token). |
| `docker-publish.yml` | On merge to `main`: builds + publishes all images to GHCR (jackson-free images). |
| `release.yml` | semantic-release computes the version bump from the Conventional Commit. |

## CI-relevant impact of #1606

- **Trivy should improve.** Removing `jackson-databind`/`jackson-core` + `java-jwt` + `jwks-rsa` from the
  runtime image removes their CVE surface (this is the point of epic #1603). Expect equal-or-fewer
  CRITICAL/HIGH findings. `handlebars` stays pinned at 4.5.2 (CVE-2026-55760), so no regression there.
- **Detekt / Kover / SonarCloud** — verified green locally on all 9 modules; the new `Security.kt` is
  Detekt-clean and covered ≥80%.
- **CodeQL** — the custom auth provider is new SAST surface; the security review (see
  `../build-and-test/security-test-instructions.md` § A) found no injection/none-alg/logging issues.

## CI caveat (tracked as #1614 — NOT a #1606 change)

The pre-existing flaky composite-build `apiSpec` generation race can intermittently break
kdiab-analyze / kdiab-nightscout `:check` (unresolved `upstream-*` client references). CI runs on a
fresh checkout so it usually generates correctly, but `gradle/actions/setup-gradle` restores a Gradle
cache that could, in principle, carry a poisoned generation. If analyze/nightscout CI fails on
`upstream-*` client references, **re-run the job** (or clear the Actions Gradle cache); it is not a
#1606 regression. Deterministic fix tracked in #1614.

## Pre-existing observation (not modified here)

`backend-ci-reusable.yml` uploads the Detekt/Trivy SARIF with `if: …` guards. Per the platform's own
CI-hygiene note, a post-merge branch deletion can hard-fail an `upload-sarif` step lacking
`continue-on-error: true`. This is unchanged by #1606; flagged for awareness only.
