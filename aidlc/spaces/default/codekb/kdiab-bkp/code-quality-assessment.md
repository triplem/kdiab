# Code Quality Assessment — kdiab (T1D Management Platform)

## Summary

kdiab is a **mature, high-discipline brownfield codebase**. It has a uniform hexagonal
architecture, spec-first APIs, a three-tier test strategy with an enforced coverage floor,
per-module static analysis, an extensive CI/CD estate (18 workflows including Trivy, CodeQL,
and SonarCloud), and thorough documentation (23 platform ADRs). The technical debt that
exists is well-contained and, in most cases, already tracked by issues or ADRs. Because the
governing intent is *"review technology and domain and suggest improvements,"* the debt
signals below are made explicit and prioritised, and the improvement opportunities are called
out for action.

## Test Coverage

### Structure — three tiers per service

Backends use three source sets wired as `JvmTestSuites` by `kdiab.ktor-service`, each running
after the previous:

- `src/test/kotlin` — unit (MockK + JUnit 5 + H2).
- `src/integration-test/kotlin` — integration (JUnit 5, `shouldRunAfter test`, H2).
- `src/e2e-test/kotlin` — e2e (Kotest, `shouldRunAfter integrationTest`).

### Test file counts (unit / integration / e2e)

| Service | Unit | Integration | E2E |
|---|:---:|:---:|:---:|
| kdiab-measures | 6 | 3 | 1 |
| kdiab-profiles | 12 | 6 | 2 |
| kdiab-treatments | 9 | 4 | 1 |
| kdiab-analyze | 16 | 2 | 1 |
| kdiab-carbs | 4 | 3 | 1 |
| kdiab-calc | 3 | 1 | 1 |
| kdiab-nightscout | 15 | 4 | **0** |
| kdiab-users | 10 | 6 | 1 |
| kdiab-common | 3 | 0 | 0 |

**Frontend:** 48 Vitest files + 12 Playwright specs.

### Coverage enforcement

- **Kover** (not JaCoCo), **80% line floor** enforced per service via `koverVerify`
  (`minValue = 80`). `check`/`koverVerify` depend on all three suites.
- **Per-service exclusions:** `ApplicationKt`, Exposed table / `DatabaseFactory`, generated
  `api` packages, and `*RoutesKt` handlers. kdiab-users additionally excludes entire
  `adapters.inbound.web`, `infrastructure.persistence`, and `infrastructure.keycloak` packages.
- **Gap:** UI Vitest coverage is **below** the 80% gate (tracked as issue #1082 / ADR-015).

## Linting and Static Analysis

- **Detekt 1.23.8** (`buildUponDefaultConfig`, `src/main/kotlin` only). Because the composite
  `includeBuild` topology makes each module its own Gradle root, **each service carries its own
  `config/detekt/{detekt.yml,baseline.xml}`**. Detekt emits **SARIF**, uploaded to the GitHub
  Security tab.
- **Baseline health** — most baselines are near-zero (profiles/users 0; measures/treatments/
  carbs/calc 1; analyze 3; common 6). **kdiab-nightscout is the outlier at 26 suppressions**
  (19 UnreachableCode Elvis-return false positives, ReturnCount ×2, TooManyFunctions,
  TooGenericExceptionCaught, LongMethod, LongParameterList).
- **ktlint is not applied** despite a rule-doc mention.
- **Frontend:** ESLint 9 + typescript-eslint strict; TypeScript strict mode.

## CI/CD

**18 GitHub Actions workflows:**

- Per-service `ci-<svc>-backend.yml` delegating to `backend-ci-reusable.yml`:
  `check` + `koverVerify` + `buildFatJar` + `cyclonedxBom` + Detekt SARIF + SonarCloud +
  Docker build + **Trivy (CRITICAL/HIGH → exit 1)** + SBOM upload.
- `ci-kdiab-ui.yml`, `ci-common-publish.yml`, `codeql-backend.yml`, `codeql-frontend.yml`,
  `docker-publish.yml`, `release.yml` (semantic-release), `e2e.yml`, `docs-pages.yml`,
  `claude.yml`.
- **Dependabot** for all ecosystems (gradle ×8, npm, docker ×10, actions), grouped.
- **GitHub Actions pinned to commit SHAs.**

## Documentation

- Root README, AGENTS.md, CONTRIBUTING, SECURITY.md, LICENSE, CHANGELOG.
- **Hierarchical CLAUDE.md** (root + per-service) and per-service AsciiDoc.
- **23 platform ADRs** in `docs/adr/` plus per-service ADRs (users 4, analyze 1, nightscout 1).
- `docs/security/accepted-risks.md`, `docs/testing/`, CycloneDX SBOMs.
- High-quality inline domain comments (CVE pin rationale, alpha-dependency rationale,
  circuit-breaker documentation).

## Security Posture

- **Auth:** JWT/JWKS via Keycloak (auth0 lib); test mode uses HMAC256 with a prod-misuse guard;
  JWKS URL forced to HTTPS for non-local issuers.
- **Authorization:** ABAC via `UserPrincipal.canAccess` (self / admin / doctor-allowedPatients);
  no Users table (identity from JWT `sub`).
- **Hardening plugins (shared):** SecurityHeaders, CORS, RateLimit, and a CircuitBreaker
  (threshold 5, reset 30s, HALF_OPEN single-probe).
- **Supply chain:** Trivy gate, CodeQL, SBOMs, SHA-pinned actions, `.trivyignore` with one
  documented false-positive CVE (NVD justification).

## Technical Debt — the 9 signals

| # | Signal | Location / evidence | Severity |
|---|---|---|---|
| 1 | **UI API-client generation gap** — `api:generate` covers only 4 of 8 backends; carbs, calc, nightscout, users use hand-written Axios | kdiab-ui | High |
| 2 | **kdiab-nightscout Detekt baseline heavy** — 26 suppressions (19 UnreachableCode, ReturnCount ×2, TooManyFunctions, etc.); highest-debt module | kdiab-nightscout/config/detekt | Medium-High |
| 3 | **kdiab-analyze suppresses all compiler warnings** (`suppressWarnings.set(true)`) — masks genuine warnings | kdiab-analyze build | Medium |
| 4 | **Version drift** — measures 0.0.1, seven at 0.1.0, common 0.0.0-SNAPSHOT; no unified platform version (library versions are consistent) | all modules | Medium |
| 5 | **v3 HISTORY stubs** — `/api/v3/{collection}/history` incomplete | NightscoutV3Routes.kt:77, TODO(#894-#898) | Medium |
| 6 | **Coverage-exclusion breadth** — several services exclude all `*RoutesKt`; users excludes entire web/persistence/keycloak packages; route/persistence code measured only via integration/e2e, and nightscout ships **0 e2e tests** | kover config | Medium |
| 7 | **UI coverage below the 80% gate** | kdiab-ui, issue #1082 / ADR-015 | Medium |
| 8 | **Duplicated per-service boilerplate** — near-identical `openApiGenerate`/`kover` blocks, per-module Detekt config/baseline, Dockerfiles (convention plugins absorb most but not all) | all services | Low |
| 9 | **`registerUpstreamSpec`/`dependencySubstitution` complexity** — bespoke build-logic the 3 fan-out services must keep in sync | build-logic, analyze/calc/nightscout | Low-Medium |

## Prioritised Improvement Opportunities

For the review intent, in recommended order:

1. **[High] Close the UI client-generation gap** — wire carbs, calc, nightscout, users specs
   into `api:generate` and replace hand-written Axios clients. Restores the spec-first contract
   for the whole internal surface (debt #1).
2. **[High] Complete the Nightscout v3 HISTORY endpoints** — resolve TODO #894–#898 so the
   ecosystem-interop domain promise is whole (debt #5).
3. **[Medium] Burn down the nightscout Detekt baseline** — apply the idiomatic Elvis-return
   rewrites (documented in the global CLAUDE.md) to eliminate the 19 false-positive
   UnreachableCode suppressions, then tackle the genuine complexity findings (debt #2).
4. **[Medium] Raise UI coverage to the 80% floor** and bring it under the same gate as the
   backends (debt #7).
5. **[Medium] Unify service versioning** behind a single platform version scheme for release
   traceability (debt #4).
6. **[Medium] Remove the blanket `suppressWarnings` in kdiab-analyze** and address the surfaced
   warnings, or narrow the suppression to generated code only (debt #3).
7. **[Low] Reduce residual build/Docker boilerplate** and **simplify the upstream-spec wiring**
   into a single declarative manifest (debt #8, #9).
8. **[Low-Medium] Add e2e tests to kdiab-nightscout** (currently 0) and reconsider the breadth
   of coverage exclusions on route/persistence/keycloak adapters (debt #6).

None of these are structural risks. The architecture and domain model are sound; these are
refinements that raise contract safety, completeness, and maintainability.
