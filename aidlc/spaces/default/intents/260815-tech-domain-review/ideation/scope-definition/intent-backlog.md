# Intent Backlog — Technology & Domain Review (proto-units)

**Intent:** review technology and domain and suggest improvements
**Organization:** Hybrid — priority theme → area/service (Q1=D)
**Date:** 2026-08-15

> These are **proto-units** (review work-items), not yet the formal Units of Work (that decomposition happens
> in Units Generation, 2.7). Each becomes one or more findings → GitHub issues. `[MVP]` = non-negotiable
> (Q2=A). Severity/effort are provisional until Reverse-Engineering supplies evidence.

## THEME 1 — Clinical Safety `[MVP-CRITICAL]`

| ID | Proto-unit | Area / service | Prov. severity |
|---|---|---|---|
| CS-1 `[MVP]` | Verify `kdiab-calc` subtracts **IOB** (insulin-on-board) from correction dosing — no stacking | backend / `kdiab-calc` | **Critical** |
| CS-2 `[MVP]` | Verify **dosing guardrails**: max-bolus clamp, negative-correction (BG < target), CGM-trend adjustment cap | backend / `kdiab-calc` | **Critical** |
| CS-3 `[MVP]` | Verify **unit handling** (mg/dL ↔ mmol/L) throughout ISF/target/bolus math | backend / `kdiab-calc`, `kdiab-common` | **Critical** |
| CS-4 | Validate the bolus **formula** vs. the standard bolus-wizard spec + reference cases (AAPS/Loop/OpenAPS docs) | domain / `kdiab-calc` | High |
| CS-5 | Verify **TIR / AGP / HbA1c** computations match consensus clinical definitions | domain / `kdiab-analyze` | High |
| CS-6 | Review **safety guardrails** in treatment recording (bolus/correction bounds, plausibility) | domain / `kdiab-treatments` | Medium |

## THEME 2 — Security & Compliance

| ID | Proto-unit | Area / service | Prov. severity |
|---|---|---|---|
| SC-1 | GDPR **special-category** data review: encryption at rest/in transit, access logging, erasure/subject-access | cross-cutting | High |
| SC-2 | **Auth hardening**: enable/verify Keycloak **passkeys + OIDC**; JWT expiry/rotation posture | cross-cutting / Keycloak | High |
| SC-3 | **MDR / SaMD posture**: document "advisory-only" stance for `kdiab-calc`; ISO 14971 risk-management note | compliance / docs | High |
| SC-4 | Secret hygiene + SAST review (gitleaks/semgrep, dependency CVEs) | cross-cutting / CI | Medium |

## THEME 3 — Tech Debt / Code Health

| ID | Proto-unit | Area / service | Prov. severity |
|---|---|---|---|
| TD-1 | **Test-pyramid** assessment: unit/integration/e2e balance; is Kover ≥80% real or gamed? | cross-cutting | Medium |
| TD-2 | **Detekt baseline** debt audit: how many suppressed findings hide in `config/detekt/baseline.xml` per service | cross-cutting | Medium |
| TD-3 | **Code-duplication** scan: cross-service patterns that belong in `kdiab-common` | backend / all services | Medium |
| TD-4 | Consistency of hexagonal layering + error handling across the 9 services | backend | Low-Med |

## THEME 4 — Modernization / Architecture

| ID | Proto-unit | Area / service | Prov. severity |
|---|---|---|---|
| MA-1 | **Stack-currency** audit: Kotlin/Ktor/Exposed/React/Vite versions + deprecations | cross-cutting | Medium |
| MA-2 | **Architecture-boundary** review: are 9 microservices right for a solo self-hosted tool? (consolidation vs. keep) | backend / architecture | Medium |
| MA-3 | **CI/CD & release** review: GitHub Actions, semantic-release, Docker publish, path filters | cross-cutting / CI | Low-Med |
| MA-4 | **Observability** review: OTEL coverage, tracing completeness, logging hygiene, runbooks | cross-cutting | Low-Med |
| MA-5 | **Frontend** review: React 19 patterns, kdiab-ui structure, bundle/build health | frontend / `kdiab-ui` | Low-Med |

## Coverage check

All five review areas (Q1) and all four priority themes are represented. **20 proto-units**; 3 are
MVP-critical (CS-1/2/3). Interoperability, performance, and multi-tenant are intentionally absent (out of
scope). This backlog feeds Requirements Analysis (2.3) and Units Generation (2.7).
