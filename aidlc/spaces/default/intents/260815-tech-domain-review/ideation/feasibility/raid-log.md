# RAID Log — Technology & Domain Review

**Intent:** review technology and domain and suggest improvements
**Date:** 2026-08-15
**RAID = Risks · Assumptions · Issues · Dependencies**

> Q3 = "nothing specific" — so the **Issues** list starts empty and is populated by review findings
> (Reverse-Engineering onward). Risks/Assumptions below are the architect + compliance reading of the intent.

## Risks (things that could undermine the review's value or the platform)

| # | Risk | Sev | Likelihood | Mitigation |
|---|---|---|---|---|
| RK1 | `kdiab-calc` recommends unsafe insulin doses (missing IOB, no max-bolus clamp, unit bug mg/dL↔mmol/L) | **Critical** `[safety]` | Unknown until RE | Make dose-calc correctness the #1 verification thread; reference AAPS/Loop algorithms as test oracle |
| RK2 | Health data (special-category) inadequately protected (encryption, access logging, erasure) vs. GDPR aim | High | Unknown | Compliance sweep during RE/NFR; treat as security priority (Q2=D) |
| RK3 | MDR/SaMD exposure — a dose calculator informing therapy may be a regulated medical device | High `[regulatory]` | Latent | Explicit "advisory-only" posture decision + document; do not claim clinical use without validation |
| RK4 | 9 microservices for a single-user tool → operational overhead disproportionate to a solo maintainer | Medium | Confirmed tension | Architecture-boundary review (Application Design); consider consolidation vs. keep-as-is with rationale |
| RK5 | Review produces too many findings to action at burst capacity → analysis paralysis | Medium | Moderate | Ruthless prioritization by value-density; explicit quick-wins list (Q8=D) |
| RK6 | Recommendations bias toward "rewrite everything" given Q4=C freedom | Low-Med | Moderate | Pair every big-swing option with honest cost/risk vs. incremental alternative |

## Assumptions (to verify — mostly during Reverse-Engineering)

| # | Assumption | Verify how |
|---|---|---|
| A1 | `kdiab-calc` currently computes bolus from ICR/ISF/target and CGM trend | Read `kdiab-calc` source (RE 2.1) |
| A2 | Existing test suite is green and Kover ≥80% is actually enforced in CI | Run `./gradlew check`; inspect CI workflows |
| A3 | Detekt baseline hides accumulated debt (suppressed findings) | Inspect `config/detekt/baseline.xml` per service |
| A4 | Code duplication exists across the 9 services (shared patterns not extracted to `kdiab-common`) | Duplication scan during RE |
| A5 | Keycloak realm can provide passkeys/OIDC (auth-hardening option) | Inspect `config/keycloak-realm.json` |
| A6 | TIR/AGP/HbA1c in `kdiab-analyze` follow consensus clinical definitions | Cross-check formulas vs. 2019 TIR consensus |

## Issues (known problems — starts empty per Q3=A)

_None supplied by the maintainer. Will be populated by review findings (RE → Requirements → the backlog)._

## Dependencies (external things the platform/review relies on)

| # | Dependency | Note |
|---|---|---|
| D1 | Keycloak (auth/JWT/JWKS) | Security posture hinges on realm config |
| D2 | PostgreSQL + Exposed + Liquibase | Data-model review anchors here |
| D3 | Nightscout v1 API contract | External clients (AAPS/xDrip+/Juggluco) depend on it — constrains changes |
| D4 | Kotlin/Ktor + React/Vite + library ecosystem | Stack-currency review checks versions/deprecations |
| D5 | OpenTelemetry collector / Jaeger | Observability review anchors here |
