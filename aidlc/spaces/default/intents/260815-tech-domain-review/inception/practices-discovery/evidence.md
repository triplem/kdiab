# Practices Discovery — Evidence

Per-perspective finding summary. This stage synthesised the four discovery
perspectives **inline** rather than via a fresh 4-agent scan, because the evidence was
already fully captured by the reverse-engineering codekb produced minutes earlier in
this session (same commit `d6c8866b`) plus the repo's committed practice docs. Sources
are cited per perspective for the freshness trail.

## Delivery / Branching (pipeline-deploy perspective)

- **Scanned:** git history (`git log --merges`), branch names, `.claude/rules/branching-strategy.md`, `.github/workflows/*`, `org.md` Way of Working.
- **Found:** Trunk-based on `main`; short-lived feature branches `<type>/<issue>-<description>`; PR-per-change. Every recent merge is a **merge-commit** (`Merge pull request #NNNN from triplem/<type>/<issue>-<desc>`) — NOT squash. Direct commits to `main` are hook-blocked. 18 GitHub Actions workflows; deploy-on-merge via `docker-publish.yml`; semantic-release for versioning.
- **Inferred:** Merge-commit strategy overrides org.md's squash default (rationale: preserve `Closes #N`).
- **Asked:** Merge strategy affirmation (org override) → user affirmed **merge-commits**.

## Testing (quality perspective)

- **Scanned:** per-service `build.gradle.kts` Kover config, `src/{test,integration-test,e2e-test}`, `.claude/rules/{quality-gates,test-pyramid}.md`, RE `code-quality-assessment.md`.
- **Found:** Three-tier suites (unit JUnit5+MockK+H2 / integration JUnit5 / e2e Kotest) wired as `JvmTestSuite`s; **Kover 80% line floor enforced per service** via `koverVerify` (NOT JaCoCo); frontend Vitest + Playwright. CI blocks the PR on gate failure. UI coverage currently below the 80% floor (issue #1082 / ADR-015).
- **Inferred:** Classic test-after/alongside methodology (no TDD red-green signal in history).
- **Asked:** none (evidence conclusive); 80% floor already affirmed to team.md at the reverse-engineering §13 gate.

## Code Style / Architecture (developer perspective)

- **Scanned:** `.claude/rules/{kotlin-style,typescript-style,solid-principles,logging}.md`, RE `code-structure.md` + `architecture.md`, Detekt config/baselines, representative source files.
- **Found:** Uniform hexagonal (ports & adapters) layout across all services; `kotlin.uuid.Uuid` / `kotlinx.datetime` domain types; Detekt per-module config+baseline (no ktlint applied); TypeScript strict + ESLint typescript-eslint strict, named exports; structured logging with correlation IDs. SOLID + DRY affirmed to team.md at the RE §13 gate.
- **Inferred:** DRY is realised via `kdiab-common` + `build-logic` convention plugins; some per-service boilerplate remains (a tech-debt signal).
- **Asked:** none (evidence conclusive).

## Security / Supply-chain (devsecops perspective)

- **Scanned:** `.claude/rules/security.md`, `backend-ci-reusable.yml`, `codeql-*.yml`, `.github/dependabot.yml`, `kdiab.kotlin-base` dependency constraints, `.trivyignore`, `docs/security/accepted-risks.md`.
- **Found:** JWT/JWKS via Keycloak (auth0 lib, HMAC256 test-mode with prod-misuse guard); ABAC via `UserPrincipal.canAccess`; shared SecurityHeaders/CORS/RateLimit plugins; CircuitBreaker for upstream resilience. CI runs Detekt SARIF, SonarCloud, CodeQL, and **Trivy CRITICAL/HIGH (exit-code 1)**; Dependabot across all ecosystems; Actions pinned to commit SHAs; CVE-pinned transitives (Jackson 2.21.4, Handlebars 4.5.2); CycloneDX SBOM.
- **Inferred:** Strong, mature supply-chain posture; no secrets in source (secret hygiene enforced).
- **Asked:** none (evidence conclusive).

## Gaps surfaced at interview

1. **Walking-skeleton stance** (not visible in code) → user chose **skip the ceremony** (brownfield incremental improvement).
2. **Merge strategy** (org squash vs evidenced merge-commits) → user affirmed **merge-commits**.
