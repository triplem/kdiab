# Modernization & Architecture Review

> **Theme: modernization** (area code `MOD`, non-safety → severity caps at High).
> Findings follow [`CONVENTIONS.md`](./CONVENTIONS.md). Assessment per FR-4.1: stack currency &
> deprecations, the nine-service boundary tension, CI/CD & release health, observability. **Per C-1,
> every rewrite proposal is paired with an incremental alternative.** Evidence: live
> `gradle/libs.versions.toml`, `.github/workflows/`, RE codekb `technology-stack.md`.

## Verdicts (FR-4.1)

| Dimension | Verdict | Finding |
|---|---|---|
| Stack currency & deprecations | Excellent | FIND-MOD-001 |
| Nine-service boundary | Over-decomposed for scale | FIND-MOD-002 |
| CI/CD & release health | Mature; version drift | FIND-MOD-005, FIND-MOD-003 |
| Observability | Trace-centric | FIND-MOD-004 |

### Findings

#### FIND-MOD-001 — Stack currency is excellent (verdict, no concern)
- Severity: Low · Effort: S · Confidence: High · Phase: Near · Area: modernization · Patient-safety impact: n/a
- Evidence: `gradle/libs.versions.toml` (Kotlin 2.3.20, Ktor 3.5.0, Exposed 1.2.0, JVM 21, Gradle 9.5.1; React 19.2.8, Vite 8.2.1, TypeScript 6, Node 26)
- Finding: all major dependencies are current; versions are centralized in one catalog; security-sensitive transitives are explicitly pinned to patched releases (Jackson 2.21.4 for CVE-2026-54512/54513, Handlebars 4.5.2 for CVE-2026-55760); the `-alpha` OTel instrumentation is a documented, intentional upstream-policy choice. There is essentially **no framework deprecation debt** — a rare, strong position.
- Recommendation: no change. Keep the catalog-centralization + CVE-pinning discipline; a scheduled dependency-update job (Renovate/Dependabot) would preserve it with less manual effort.

#### FIND-MOD-002 — Nine services is over-decomposed for a solo-maintained, self-hosted, single-tenant app
- Severity: Medium · Effort: L · Confidence: Medium · Phase: Long · Area: modernization
- Evidence: root `CLAUDE.md` service map (9 modules); several are thin/stateless — `kdiab-calc` (stateless compute), `kdiab-analyze` (stateless BFF), `kdiab-nightscout` (compat layer); RE codekb `technology-stack.md` (9 Dockerfiles, per-service CI)
- Patient-safety impact: n/a (operational/maintainability).
- Finding: the microservice count multiplies operational surface for one maintainer — 9 Dockerfiles, 9 CI pipelines, inter-service HTTP hops (calc→profiles, analyze→measures/profiles/treatments), and the Keycloak audience-mapper juggling that JWT-forwarding requires. For a single-tenant, self-hosted deployment that never needs independent per-service scaling, the boundaries buy little and cost real overhead.
- Recommendation (rewrite): consolidate into a **modular monolith** — one deployable, module boundaries preserved as packages — removing the network hops and the multi-audience token dance.
- **Incremental alternative (C-1, preferred first step):** keep the services but cut the operational tax without a rewrite — collapse the stateless trio (`calc`, `analyze`, `nightscout`) into the services they front, share more via `kdiab-common`, and unify deployment (one compose/Helm release). Measure whether the remaining boundaries still earn their keep before committing to a monolith.

#### FIND-MOD-003 — No unified platform version; module versions drift
- Severity: Low · Effort: S · Confidence: High · Phase: Mid · Area: modernization
- Evidence: RE codekb `technology-stack.md` § "Version Health Note" (kdiab-measures 0.0.1, seven services at 0.1.0, kdiab-common 0.0.0-SNAPSHOT)
- Patient-safety impact: n/a.
- Finding: services carry independent, drifted versions with no single platform version — semantic-release bumps each in isolation, so "which platform version is deployed" is unanswerable.
- Recommendation: adopt a single platform version (or a BOM) stamped across all modules at release.
- Incremental alternative: n/a (a release-config change, not a rewrite).

#### FIND-MOD-004 — Observability is trace-centric; metrics/alerting/log-aggregation are lighter
- Severity: Medium · Effort: M · Confidence: Medium · Phase: Long · Area: modernization
- Evidence: root `CLAUDE.md` § Observability (OTEL traces → collector → Jaeger); `libs.versions.toml` (`ktor-server-metrics`, Logback JSON present); no metrics-backend/alerting in the compose stack
- Patient-safety impact: n/a (operability — but a silent CGM-ingest outage on a T1D platform has real downstream impact, so operability is not cosmetic here).
- Finding: distributed tracing is well set up (ADR-014), but there is no metrics dashboard/alerting (Prometheus/Grafana) or log aggregation (Loki/ELK) wired in the stack. A solo maintainer would not be paged if, say, `kdiab-measures` ingest stalled.
- Recommendation: add a metrics + alerting layer.
- **Incremental alternative (C-1):** ship an opt-in `docker-compose.observability.yml` profile (Prometheus scraping the existing Ktor metrics endpoint + Grafana + a couple of ingest/error alerts, and Loki for the already-JSON logs) — additive, no code rewrite; the OTel collector can fan out to it.

#### FIND-MOD-005 — CI/CD & release health is mature (verdict, no concern)
- Severity: Low · Effort: S · Confidence: High · Phase: Near · Area: modernization · Patient-safety impact: n/a
- Evidence: `.github/workflows/` (per-service `ci-*-backend.yml` + `backend-ci-reusable.yml`, `codeql-backend/frontend.yml`, `docker-publish.yml`, `release.yml` semantic-release, `e2e.yml`, `ci-common-publish.yml`)
- Finding: the pipeline is genuinely mature — reusable per-service CI, CodeQL on both stacks, Trivy image scanning, SBOM (CycloneDX), semantic-release, e2e, GitHub-Pages docs. Quality gates (Kover 80%, Detekt SARIF, SonarCloud) are enforced pre-merge.
- Recommendation: no change; the only minor cost is that 9× per-service pipelines add solo maintenance load — the reusable workflow already mitigates most of it. (Ties to FIND-MOD-002: fewer services → fewer pipelines.)

## Section coverage (FR-4.1)

Stack currency (FIND-MOD-001), nine-service boundary (FIND-MOD-002, rewrite + incremental alt),
CI/CD & release health (FIND-MOD-005 + version drift FIND-MOD-003), observability (FIND-MOD-004,
incremental alt). ✓
