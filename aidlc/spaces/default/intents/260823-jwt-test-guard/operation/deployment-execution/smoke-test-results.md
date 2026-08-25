# Smoke Test Results — jwt-test-guard (#1588)

Publish-only pipeline ⇒ the "smoke test" is the **PR CI gate** (there is no environment to probe). The
authoritative post-publish signal is every GitHub Actions check on PR #1642 going green before merge.

## PR #1642 gate (at hand-off)

- **Mergeable:** MERGEABLE · **Merge state:** UNSTABLE (checks running)
- **Checks:** the full per-module `build / Build and Test` matrix (all 9 backends) + `Analyze (java-kotlin)`
  (CodeQL) queued/pending at hand-off. Trivy / SonarCloud / SBOM run as part of the gate.
- **Expectation:** green. Locally all 9 backend modules built + tested green with this change, and the
  guard's `SecurityConfigTest` (AC-1/AC-2/AC-4) passes. CI runs on a fresh checkout, so the local-only
  pre-existing conditions (Detekt `UnreachableCode` FPs; nightscout #1614 stale-cache) do not apply.

## Local pre-merge smoke (already performed — build-and-test stage)
- `SecurityConfigTest` AC-1 (new negative), AC-2, AC-4 → PASS.
- All 9 backend modules `test` (+ integration/e2e compile) → green (nightscout after clearing the #1614
  stale cache).
- `Security.kt` Detekt-clean; 36/36 `jwt.test=true` sites carry the opt-in (except the AC-1 guard-under-test).

## Handoff
Do not merge until **every** PR #1642 check is green (team rule). Merge is the maintainer's action
(merge-commit, not squash). Watch especially: all `build / Build and Test` matrix jobs, `Analyze
(java-kotlin)`, Trivy CRITICAL/HIGH, SonarCloud.
