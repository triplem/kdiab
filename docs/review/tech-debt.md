# Tech-Debt & Code-Health Review

> **Theme: tech-debt** (area code `DEBT`, non-safety → severity caps at High).
> Findings follow [`CONVENTIONS.md`](./CONVENTIONS.md). Assessment per FR-3.1 (test pyramid + real
> Kover picture) and FR-3.2 (Detekt baseline debt + duplication). **The US-5 live-verification guard
> was applied to every codekb-tracked anchor** — see the box below. Evidence is live-repo, not the
> codekb snapshot.

## Live-verification results (US-5 currency guard — applied before reporting)

| Codekb anchor | Codekb said | Live-repo status (verified 2026-08-16) | Reported as |
|---|---|---|---|
| Issue #1082 (UI coverage < 80%) | open debt | **CLOSED** (`gh issue view 1082`) | **RESOLVED — not open debt** (FIND-DEBT-002) |
| `kdiab-ui/vite.config.ts` `lines:72` | coverage gap | **Intentional ADR-015 floor** (line 104, exclusion-based) | context, **not** an unmet 80% gap |
| Issues #894–#898 (v3) | track HISTORY TODO | **ALL CLOSED** — they are the v3 *collection-CRUD* features, done | reference is **stale** (FIND-DEBT-007) |
| v3 HISTORY endpoints | incomplete | **Still stubbed** (`NightscoutV3Routes.kt#L77`) | real, **untracked** debt (FIND-DEBT-007) |
| nightscout Detekt baseline | 26 (19 UnreachableCode) | **Confirmed** — 26 entries, 19 UnreachableCode | real debt (FIND-DEBT-004) |
| nightscout e2e tests | 0 | **Confirmed** — no `e2e-test` source set | real debt (FIND-DEBT-001) |

No resolved gap is reported as open. This guard directly caught two stale codekb claims (#1082 and the
#894–#898↔HISTORY conflation).

## Findings

#### FIND-DEBT-001 — `kdiab-nightscout` ships 0 e2e tests despite being the interop-critical service
- Severity: Medium · Effort: M · Confidence: High · Phase: Mid · Area: tech-debt · Patient-safety impact: n/a
- Evidence: no `kdiab-nightscout/src/e2e-test` source set (verified); RE codekb test-count table (nightscout e2e = 0; unit 15 / integration 4)
- Finding: the Nightscout compatibility layer is the platform's contract surface for AAPS / xDrip+ / Juggluco — precisely the service where end-to-end/contract tests matter most — yet it has none. A protocol regression would not be caught before release.
- Recommendation: add an e2e/contract suite for the Nightscout v1/v3 API shapes the ecosystem clients rely on.
- Incremental alternative: start with contract tests for the highest-traffic endpoints (entries, treatments) before full parity coverage.

#### FIND-DEBT-002 — UI coverage "gap" is RESOLVED; the 80% Kover floor is genuinely enforced (verdict)
- Severity: Low · Effort: S · Confidence: High · Phase: Near · Area: tech-debt · Patient-safety impact: n/a
- Evidence: `gh issue view 1082` → **CLOSED**; `kdiab-ui/vite.config.ts#L104` (`lines: 72`, comment → `docs/adr/ADR-015-coverage-exclusions.adoc`); backend `koverVerify minValue = 80`
- Finding: the codekb flagged UI coverage as below the 80% gate (issue #1082). Live-verified: #1082 is **closed**, and the UI's `lines: 72` threshold is an **intentional, ADR-015-documented exclusion-based floor** (the composition root and generated code are excluded), not an unmet 80% target. Backends enforce an 80% line floor per service via `koverVerify`. This is a **positive verdict**, recorded so the resolved item is not mistaken for open debt (the exact failure the US-5 guard prevents).
- Recommendation: no change — keep ADR-015 as the record of intent.

#### FIND-DEBT-003 — Coverage-exclusion breadth means the 80% line number overstates real coverage
- Severity: Medium · Effort: M · Confidence: High · Phase: Mid · Area: tech-debt · Patient-safety impact: n/a
- Evidence: RE codekb kover config (`ApplicationKt`, `*RoutesKt`, Exposed tables, generated `api` excluded; `kdiab-users` additionally excludes entire `adapters.inbound.web`, `infrastructure.persistence`, `infrastructure.keycloak`); combined with FIND-DEBT-001 (nightscout 0 e2e)
- Finding: the 80% floor is real but measured against a reduced surface. Route and persistence layers are excluded from unit coverage and covered only via integration/e2e — and nightscout has no e2e — so the effective coverage of the HTTP/persistence layers (where most defects live) is lower than "80%" implies, especially for `kdiab-users`.
- Recommendation: narrow the exclusion set (or add route/persistence unit tests) so the coverage number reflects the layers that carry risk; prioritize `kdiab-users` (broadest exclusions) and nightscout (no e2e).
- Incremental alternative: add integration coverage for the excluded layers before removing exclusions, so the floor rises without a sudden gate failure.

#### FIND-DEBT-004 — `kdiab-nightscout` Detekt baseline is the outlier (19 UnreachableCode false positives)
- Severity: Medium · Effort: M · Confidence: High · Phase: Mid · Area: tech-debt · Patient-safety impact: n/a
- Evidence: `kdiab-nightscout/config/detekt/baseline.xml` (26 suppressions; 19 `UnreachableCode`, verified) vs near-zero baselines elsewhere (profiles/users 0; most services ≤3)
- Finding: nightscout carries 26 baselined Detekt findings, 19 of them the well-known `UnreachableCode` false positive from Elvis-`return@label` guards in lambdas — the exact pattern the global `CLAUDE.md` documents an idiomatic rewrite for. It is cosmetic (not genuine unreachable code) but is baseline debt that hides any *real* future finding of the same rule.
- Recommendation: apply the documented `?.let` / expression-body rewrites to clear the 19 false positives, then delete them from the baseline so the rule protects the module again.
- Incremental alternative: burn the baseline down incrementally (a few per PR) rather than one large refactor.

#### FIND-DEBT-005 — UI generates typed clients for only 4 of 8 backends
- Severity: High · Effort: M · Confidence: High · Phase: Near · Area: tech-debt · Patient-safety impact: n/a
- Evidence: `kdiab-ui/package.json` `api:generate` (measures, profiles, treatments, analyze only — verified); carbs, calc, nightscout, users use hand-written Axios
- Finding: the spec-first contract (OpenAPI → generated client) — a core project convention — holds for only half the internal surface. The four hand-written clients (including `kdiab-calc`, the dose calculator, and `kdiab-users`) can silently drift from their `openapi.yaml`, defeating the single-source-of-truth guarantee for exactly the safety- and identity-critical services.
- Recommendation: wire carbs, calc, nightscout, and users specs into `api:generate` and replace the hand-written Axios clients.
- Incremental alternative: prioritize `kdiab-calc` (dose) and `kdiab-users` (identity) first — the highest-impact drift risk — before carbs/nightscout.

#### FIND-DEBT-006 — Cross-service boilerplate and glucose-conversion duplication
- Severity: Low · Effort: M · Confidence: Medium · Phase: Long · Area: tech-debt · Patient-safety impact: n/a
- Evidence: RE codekb debt #8 (per-service `openApiGenerate`/`kover` blocks, per-module Detekt config, Dockerfiles); `kdiab-calc/.../DoseCalculationService.kt` (`MMOL_TO_MGDL_FACTOR = 18.0`) vs `kdiab-analyze` (`GLUCOSE_CONVERSION_FACTOR`) vs treatments mapper unit-normalization
- Finding: convention plugins absorb most shared build config, but per-service boilerplate and, more notably, the **mmol/L↔mg/dL conversion constant is re-implemented in at least three services** — a correctness-sensitive value that should live once in `kdiab-common` (which already defines `GLUCOSE_CONVERSION_FACTOR`).
- Recommendation: consolidate the glucose-conversion factor/util into `kdiab-common` and remove the per-service copies (calc's `MMOL_TO_MGDL_FACTOR`).
- Incremental alternative: unify the conversion constant first (highest correctness value), then tackle build-boilerplate duplication (see FIND-MOD-002 boundary consolidation).

#### FIND-DEBT-007 — v3 HISTORY endpoints stubbed with stale issue references
- Severity: Medium · Effort: M · Confidence: High · Phase: Mid · Area: tech-debt · Patient-safety impact: n/a
- Evidence: `kdiab-nightscout/.../adapters/inbound/web/NightscoutV3Routes.kt#L77` (`// TODO(#894-#898): stub HISTORY endpoints ...`, returns an empty `Ns3HistoryResult`); `gh issue view 894..898` → **all CLOSED** (they are the v3 collection-CRUD features, now implemented)
- Finding: the `/api/v3/{collection}/history` endpoints are still stubs, **but** the `TODO(#894-#898)` reference is stale — those issues are closed and were about the collection CRUD, not HISTORY. So the HISTORY gap is real *and untracked* (no open issue owns it).
- Recommendation: open a fresh tracking issue for the v3 HISTORY endpoints and update the TODO reference; decide whether HISTORY parity is actually needed by the target clients (it may be a deliberate no-op).
- Incremental alternative: if no client needs HISTORY, replace the stub with an explicit documented `404/not-supported` and close the debt as won't-do rather than implement.

#### FIND-DEBT-008 — `kdiab-analyze` suppresses all compiler warnings
- Severity: Medium · Effort: S · Confidence: High · Phase: Near · Area: tech-debt · Patient-safety impact: n/a
- Evidence: `kdiab-analyze/build.gradle.kts#L25` (`suppressWarnings.set(true)`)
- Finding: the platform's largest/most-complex service globally suppresses Kotlin compiler warnings, masking genuine ones (deprecations, unchecked casts) in exactly the code that computes clinical metrics.
- Recommendation: remove `suppressWarnings.set(true)`, fix or `@Suppress` the genuine warnings individually.
- Incremental alternative: flip it off, triage the surfaced warnings, and address them over a few PRs.

#### FIND-DEBT-009 — No performance/load-testing tier across the nine services
- Severity: Medium · Effort: M · Confidence: High · Phase: Mid · Area: tech-debt · Patient-safety impact: indirect (an undetected latency/throughput regression on the dose path could delay a bolus recommendation)
- Evidence: service test source sets are `test` / `integration-test` / `e2e-test` only (root `CLAUDE.md#Test-Suites-Backend`) — no `performance-test` source set or load harness in any of the nine services; `.claude/rules/test-pyramid.md` defines a performance tier the suite omits; the latency-sensitive paths `kdiab-calc/.../application/service/DoseCalculationService.kt#calculateDose` and the `kdiab-analyze` fan-out BFF (aggregates every backend) have no load coverage
- Finding: the platform has a mature unit/integration/e2e pyramid but **no performance or load-testing tier at all**. For a nine-service system whose BFF (`kdiab-analyze`) fans out to every backend and whose dose calculator sits on the clinical hot path, a latency or throughput regression (a slow query, an N+1 fan-out, connection-pool exhaustion) would reach production uncaught — there is no baseline, no budget, and no gate for response time under concurrent load.
- Recommendation: add a performance/load-testing tier — establish latency/throughput budgets for the hot paths and a load harness in CI (nightly or pre-release), starting with the dose endpoint (`kdiab-calc`) and the aggregation BFF (`kdiab-analyze`).
- Incremental alternative: begin with a lightweight k6/Gatling smoke (p95-latency assertion) against `kdiab-calc` and `kdiab-analyze` only, wired to the existing OTEL observability so budgets are measured, before extending to all services.

## Positive context

The codekb rates kdiab a "mature, high-discipline brownfield codebase," and the live review agrees:
uniform hexagonal architecture, spec-first APIs, an enforced coverage floor, per-module Detekt with
SARIF upload, Trivy CRITICAL/HIGH gates, CodeQL, SonarCloud, SBOMs, Dependabot, and SHA-pinned Actions.
Most debt is small, contained, and (where it was tracked) already resolved. The findings above are the
*residual* debt, not a troubled codebase.

## Section coverage (FR-3.1 / FR-3.2)

- **FR-3.1** test pyramid (FIND-DEBT-001, FIND-DEBT-003, FIND-DEBT-009 — missing performance tier), real
  Kover picture (FIND-DEBT-002 — UI resolved, 80% enforced). ✓
- **FR-3.2** Detekt baseline (FIND-DEBT-004), duplication (FIND-DEBT-006); plus DEBT-005/007/008. ✓
- **US-5 currency guard** applied to every codekb anchor; two stale claims caught. ✓
