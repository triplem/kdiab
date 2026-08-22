# CI Pipeline — Clarifying Questions (#1606)

The kdiab platform already has a mature, adequate CI. Per this stage's condition ("Skip if CI already
exists and is adequate"), #1606 requires **no new pipeline** — these questions are answered by the
existing `.github/workflows/` configuration, not re-decided.

## Questions & answers

| # | Question | Answer (from existing infrastructure) |
|---|---|---|
| Q1 | CI tool? | **GitHub Actions**. Per-service `ci-<service>-backend.yml` → reusable `backend-ci-reusable.yml`; plus `codeql-backend.yml`, `docker-publish.yml`, `release.yml`, `e2e.yml`, `ci-common-publish.yml`, `ci-kdiab-ui.yml`. |
| Q2 | Branch strategy? | Trunk-based on `main`; short-lived `<type>/<issue>-<desc>` feature branches; **merge-commit** (never squash) to preserve `Closes #N`; direct commits to `main` git-hook-blocked. |
| Q3 | Quality gates before merge? | `./gradlew :check :koverVerify` (tests + Detekt + Kover ≥80%), Detekt SARIF, SonarCloud, Trivy CRITICAL/HIGH (`exit-code 1`), CodeQL, CycloneDX SBOM, `:buildFatJar`. See `quality-gates.md`. |
| Q4 | Artifact repositories? | GitHub Packages (`kdiab-common` Maven via `ci-common-publish.yml`); GHCR Docker images via `docker-publish.yml` on push to `main`; SBOM + SARIF artifacts uploaded per run. |
| Q5 | Does #1606 change the pipeline? | **No.** #1606 is a code change (auth library swap + dependency shed). The existing gates already cover it. No workflow YAML is added or modified. |

## Decision

**No CI pipeline modification for #1606.** The change is validated by the existing gates. This stage
documents how #1606 flows through them (`ci-config.md`) and the gate expectations (`quality-gates.md`),
and flags the one CI-relevant caveat (the #1614 flaky `apiSpec` generation race).
