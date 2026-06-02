# Requirements: Quality Reports on GitHub Pages

**Status**: Draft — awaiting approval
**Date**: 2026-06-02
**Author**: RequirementsAgent

---

## 1. Problem Statement

Test results, code coverage, static analysis (Detekt, ESLint), container vulnerability scans (Trivy), and OpenAPI specs are generated on every CI run but are only accessible by downloading ZIP artifacts from individual workflow runs. There is no persistent, browsable, linked view of these reports. Developers and reviewers must dig through CI artifacts to answer basic quality questions ("did tests pass?", "what's the coverage?", "are there container vulnerabilities?", "what does this endpoint expect?"). This adds friction to code reviews and makes quality trends invisible.

---

## 2. Stakeholders

| Role | Who |
|---|---|
| Developer / maintainer | triplem |
| Services covered | kdiab-measures, kdiab-profiles, kdiab-treatments, kdiab-analyze, kdiab-carbs, kdiab-calc, kdiab-nightscout, kdiab-users, kdiab-common, kdiab-ui |

---

## 3. Functional Requirements

### FR-1 — OpenAPI Spec Pages (Redoc)

For each of the 8 backend services that have an `api/openapi.yaml`:
- measures, profiles, treatments, analyze, carbs, calc, nightscout, users

**Behaviour**: Render each service's `api/openapi.yaml` as a static HTML page using `npx @redocly/cli@2 build-docs api/openapi.yaml --output openapi.html`. The page loads Redoc JS from `cdn.redocly.com` — acceptable since GitHub Pages already requires internet access.

**Output path on GitHub Pages**: `<service>/openapi.html`

**Acceptance test**: Navigating to `<pages-url>/measures/openapi.html` renders a Redoc page showing the kdiab-measures API with a working endpoint navigation sidebar and requires no live backend server to display.

---

### FR-2 — Detekt HTML Reports

For all 9 Kotlin modules (8 services + kdiab-common):

**Behaviour**: The Gradle `check` task already generates `build/reports/detekt/detekt.html`. This file is uploaded as an artifact and assembled into the Pages site.

**Output path on GitHub Pages**: `<service>/reports/detekt/detekt.html` (kdiab-common → `common/reports/detekt/detekt.html`)

**Acceptance test**: Navigating to `<pages-url>/measures/reports/detekt/detekt.html` renders the Detekt HTML report for kdiab-measures showing rule violations (or "No issues found").

---

### FR-3 — Kover Coverage HTML Reports

For all 9 Kotlin modules:

**Behaviour**: Run `koverHtmlReport` (in addition to the existing `check`) to generate the HTML coverage report. The `koverHtmlReport` Gradle task exists in all service builds but is not currently invoked. Output lands at `build/reports/kover/html/` (confirmed locally).

**Output path on GitHub Pages**: `<service>/reports/coverage/` (kdiab-common → `common/reports/coverage/`)

**Acceptance test**: Navigating to `<pages-url>/measures/reports/coverage/index.html` renders a Kover HTML coverage report showing per-package line coverage percentages.

---

### FR-4 — Gradle Test HTML Reports

For all 9 Kotlin modules:

**Behaviour**: Gradle generates per-suite HTML reports under `build/reports/tests/{test,integrationTest,e2eTest}/`. All three suites are uploaded where they exist; missing suites are silently skipped (no broken links, no build failure). All test suites use H2 in-memory databases — no Docker or external services required.

**Output paths on GitHub Pages**:
- `<service>/reports/tests/unit/` ← `build/reports/tests/test/`
- `<service>/reports/tests/integration/` ← `build/reports/tests/integrationTest/`
- `<service>/reports/tests/e2e/` ← `build/reports/tests/e2eTest/`

**Acceptance test**: Navigating to `<pages-url>/measures/reports/tests/unit/index.html` renders the Gradle JUnit test report for kdiab-measures showing test count, pass/fail, and individual test names.

---

### FR-5 — kdiab-ui Vitest Coverage Report

**Behaviour**: Run `npm run test:coverage` (configured with `@vitest/coverage-v8`, outputs `coverage/index.html`). Upload the entire `coverage/` directory.

**Output path on GitHub Pages**: `ui/reports/coverage/`

**Acceptance test**: Navigating to `<pages-url>/ui/reports/coverage/index.html` renders the Vitest HTML coverage report showing per-file line coverage for kdiab-ui.

---

### FR-6 — ESLint HTML Report

For kdiab-ui only:

**Behaviour**: Run `npx eslint . --format html --output-file eslint-report.html`. ESLint's built-in HTML formatter produces a self-contained 41 KB HTML file (confirmed locally). The command runs regardless of whether lint passes — exit code must not fail the docs build so the report is always published even when violations exist (`|| true` or `continue-on-error: true`).

**Output path on GitHub Pages**: `ui/reports/eslint.html`

**Acceptance test**: Navigating to `<pages-url>/ui/reports/eslint.html` renders the ESLint HTML report showing files, rule violations (or "No problems found"). The docs workflow does not fail if ESLint reports violations.

---

### FR-7 — Trivy Container Scan HTML Reports

For 8 backend services (measures, profiles, treatments, analyze, carbs, calc, nightscout, users) and kdiab-ui. **Not** for kdiab-common (no Docker image).

**Behaviour**: In the `docs-pages.yml` `build-service` job (and `build-ui-reports` job), after running the Gradle/npm build:
1. Build the Docker image locally (same `Dockerfile` as used by `backend-ci-reusable.yml`, using GHA Docker layer cache)
2. Run `trivy-action` with `format: template`, `template: '@/contrib/html.tpl'`, `exit-code: 0` (does not fail the docs build), `severity: CRITICAL,HIGH,MEDIUM`

This is separate from the security-gate Trivy scan in `backend-ci-reusable.yml`, which uses `format: sarif` + `exit-code: 1` and remains unchanged.

**Output path on GitHub Pages**: `<service>/reports/trivy.html` (kdiab-ui → `ui/reports/trivy.html`)

**Acceptance test**: Navigating to `<pages-url>/measures/reports/trivy.html` renders the Trivy HTML report showing the vulnerability table for the kdiab-measures Docker image. The docs workflow does not fail if vulnerabilities are found.

---

### FR-8 — Links in the AsciiDoc Documentation

**FR-8a — Root docs index** (`docs/index.adoc`): Add a "Quality Reports" section as a table. Columns: service, OpenAPI, Detekt, Coverage, Tests, Trivy. kdiab-common row omits OpenAPI and Trivy. kdiab-ui row omits OpenAPI and Detekt but adds ESLint.

**FR-8b — Per-service docs index** (each `<service>/docs/index.adoc`): Add a "Quality Reports" section with individual links to each applicable report for that service:
- OpenAPI Spec (Redoc) — 8 backend services only
- Detekt Report — 9 Kotlin modules
- Coverage Report (Kover) — 9 Kotlin modules
- Test Results: Unit, Integration (where applicable), E2E (where applicable)
- Trivy Scan — 8 backend services + kdiab-ui
- ESLint Report — kdiab-ui only

All links use relative paths that resolve correctly within the GitHub Pages site tree.

**Acceptance test**: On the published root `index.html`, clicking any report link navigates to the correct page without a 404.

---

## 4. Non-Functional Requirements

| ID | Requirement | Rationale |
|---|---|---|
| NFR-1 | Reports on GitHub Pages always reflect the last **successful** `docs-pages.yml` run for `main`. A failed docs build leaving the previous successful reports in place is acceptable. A passing build whose reports are more than one main-push cycle behind the latest successful run is not acceptable. | Developer trust — an outdated report is worse than no report; but a transient infra failure should not wipe reports. |
| NFR-2 | Report URLs are stable across runs. The URL for a given report type must not change between deployments. | Bookmarks and documentation links must not break. |
| NFR-3 | The docs + reports workflow must complete within **30 minutes**. | GitHub Pages timeout risk; developer iteration speed. |
| NFR-4 | Missing report directories (e.g. a service has no e2e suite) do not cause the workflow to fail. | New services or partial test suites must not block the publish. |
| NFR-5 | Trivy and ESLint scans in the docs workflow do not fail the build on findings — they always publish the report. The security-gate Trivy scan in `backend-ci-reusable.yml` continues to fail on CRITICAL/HIGH and is not affected. | Docs publish must be decoupled from security gate decisions. |
| NFR-6 | `docs-pages.yml` triggers on every push to `main` (not only on doc file changes). | Reports change with every code push; path-restricted triggers leave reports stale. |
| NFR-7 | Each report-generating step in `build-service` runs with `continue-on-error: true`; artifact upload uses `if: always()`. The `assemble` job uses `if: always()` + `continue-on-error: true` on each artifact download step. A single transient step failure must not prevent other reports from being published. | Partial publish is always better than no publish. |
| NFR-8 | The `build-service` matrix job and `build-ui-reports` job specify `timeout-minutes: 25`. | Caps cold-cache Docker builds; keeps total workflow within the 30-minute budget. |

---

## 5. Architecture Decision (derived)

**Integration approach**: Extend the existing `docs-pages.yml` workflow. All report generation happens inside the docs workflow itself — no cross-workflow artifact coordination required.

**Kotlin services (`build-service` matrix job)**:
- Extend Gradle command: `./gradlew check koverHtmlReport asciidoctor`
- Add Redocly step: `npx @redocly/cli@2 build-docs api/openapi.yaml --output openapi.html`
- Add Docker Buildx + `docker/build-push-action` (same config as `backend-ci-reusable.yml`, using GHA layer cache)
- Add `trivy-action` with HTML template, `exit-code: 0`
- Upload all outputs (docs + reports + openapi.html + trivy.html) as a single artifact per service

**kdiab-common (`build-common` new job)**:
- Extends `build-service` minus OpenAPI and Trivy steps (no Dockerfile)

**kdiab-ui (`build-ui-reports` new job)**:
- `npm ci`, `npm run test:coverage`, ESLint HTML, Docker build, Trivy HTML
- Upload `coverage/`, `eslint-report.html`, `trivy-report.html`

**`assemble` job**: downloads all artifacts, merges into `pages/` tree, sets up and uploads GitHub Pages artifact (as today).

**Page tree structure**:
```
pages/
  index.html                               ← redirect to root/index.html (existing)
  root/                                    ← AsciiDoc root docs (existing)
  <service>/                               ← AsciiDoc service docs (existing) + new:
    openapi.html                           ← FR-1 (8 backend services)
    reports/
      detekt/detekt.html                   ← FR-2
      coverage/index.html (+assets)        ← FR-3 (Kover)
      tests/unit/index.html (+assets)      ← FR-4
      tests/integration/index.html         ← FR-4 (where applicable)
      tests/e2e/index.html                 ← FR-4 (where applicable)
      trivy.html                           ← FR-7 (8 backend services)
  common/                                  ← kdiab-common (no OpenAPI, no Trivy)
    reports/
      detekt/detekt.html
      coverage/index.html
      tests/unit/index.html
  ui/                                      ← kdiab-ui
    reports/
      coverage/index.html (+assets)        ← FR-5 (Vitest)
      eslint.html                          ← FR-6
      trivy.html                           ← FR-7
```

---

## 6. Out of Scope

- Per-PR or per-branch report publishing (only `main` branch).
- Historical report archiving (latest build only; GitHub Pages overwrites on each deploy).
- SBOM HTML pages — CycloneDX `.json` SBOMs have no standard self-contained HTML renderer. SBOMs remain available as CI workflow artifacts as today.
- OWASP Dependency Check (not configured in this project).
- Trivy filesystem scan (FR-7 uses container image scan, which covers OS + app dependencies).

---

## 7. Acceptance Criteria (Summary)

- [ ] GitHub Pages site contains `openapi.html` for all 8 backend services (Redoc).
- [ ] GitHub Pages site contains `reports/detekt/detekt.html` for all 9 Kotlin modules.
- [ ] GitHub Pages site contains `reports/coverage/index.html` for all 9 Kotlin modules (Kover HTML).
- [ ] GitHub Pages site contains `reports/tests/unit/index.html` for all 9 Kotlin modules.
- [ ] GitHub Pages site contains `reports/tests/integration/` and `e2e/` where those suites exist.
- [ ] GitHub Pages site contains `ui/reports/coverage/index.html` (Vitest).
- [ ] GitHub Pages site contains `ui/reports/eslint.html` (ESLint HTML, always published).
- [ ] GitHub Pages site contains `<service>/reports/trivy.html` for all 8 backend services and kdiab-ui.
- [ ] Root `docs/index.adoc` has a "Quality Reports" section linking to all services' reports.
- [ ] Each service's `docs/index.adoc` has a "Quality Reports" section with links to its own reports.
- [ ] All links in the assembled HTML resolve without 404 errors.
- [ ] `docs-pages.yml` triggers on every push to `main` (not path-restricted).
- [ ] Full docs + reports workflow completes within 30 minutes.
- [ ] Trivy findings in the docs workflow do not block the Pages deploy.
- [ ] ESLint violations in the docs workflow do not block the Pages deploy.
- [ ] The security-gate Trivy scan in `backend-ci-reusable.yml` is unchanged and still fails on CRITICAL/HIGH.

---

## 8. Open Questions

None. All design decisions have been derived from the codebase or confirmed by the author.

---

## 9. Top Risks

1. **Workflow runtime**: Running `check koverHtmlReport` + Docker build + Trivy for 8+ services adds significant time. Mitigation: matrix jobs run in parallel; Gradle remote cache and Docker layer cache (`type=gha`) mean repeated builds are fast on warm runners.

2. **Trivy HTML template path**: The `@/contrib/html.tpl` template path is relative to the Trivy installation inside the `trivy-action` container. Path may vary by `trivy-action` version. Mitigation: verify the correct template path in a dry-run step, or download the template file explicitly from the Trivy GitHub repo.

3. **Redocly CLI version drift**: Pinning `@redocly/cli@2` rather than `@latest` avoids unexpected breakage when a new major version changes the `build-docs` subcommand interface (confirmed working at v2.31.5).

4. **30-minute budget**: Docker builds are the most variable step. If the GHA cache is cold (e.g. after a base image update), builds may be slower. Mitigation: set `timeout-minutes: 25` on `build-service` jobs to catch runaway builds early and leave headroom for the assemble + deploy jobs.
