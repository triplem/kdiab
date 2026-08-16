# Scope Document — Technology & Domain Review

**Intent:** review technology and domain and suggest improvements
**Date:** 2026-08-15
**Type:** Assessment → prioritized recommendations (implementation deferred, Q9=A)

> Consulted upstream: `intent-statement.md`, `feasibility-assessment.md`, `constraint-register.md`.

## In Scope

The review covers **all five areas** (Q1), assessed against the priority order below. "Review" here means:
inspect the codebase + domain, produce **evidence-linked findings**, and rank them.

| Priority | Theme | What the review examines |
|---|---|---|
| **1 (non-negotiable)** | **Clinical safety** | `kdiab-calc` dose correctness (IOB, guardrails, units, formula vs. reference); safety guardrails in treatments; TIR/AGP/HbA1c definition correctness in `kdiab-analyze` |
| 2 | **Security & compliance** | GDPR special-category handling; auth hardening (Keycloak passkeys/OIDC); MDR/SaMD posture |
| 3 | **Tech debt / code health** | Test pyramid (unit/integration/e2e balance, Kover reality); Detekt baseline debt; code duplication across services |
| 4 | **Modernization / architecture** | Stack currency (versions/deprecations); the 9-service boundary tension; CI/CD & release; observability |

## Minimum Viable Review (if capacity forces a cut)

**Theme 1 — Clinical safety, centered on `kdiab-calc` — is non-negotiable (Q2=A).** Everything else is
trimmable before it. If only one thing ships from this review, it is a verified, safe dose calculator.

## Out of Scope

- **Implementation** — this workflow produces recommendations; building is deferred (Q9=A). Natural park
  point: end of Inception.
- **Interoperability breadth** (Dexcom/Glooko/LibreLinkUp connectors, deep Nightscout/AAPS parity) — Q6.
- **Performance & scalability** tuning — Q2 deprioritized (E).
- **Multi-tenant / cloud-native / Kubernetes** posture — contradicts personal self-hosted (Q2=A).
- **Formal certification work** (actual MDR submission, GDPR DPA) — the review *flags* obligations; it does
  not execute certification.

## Prioritization Framework

Ranked by **value-density** (clinical/safety impact + risk reduction per hour), reflecting **occasional-burst
capacity** (Q1=C) — every backlog item must be independently shippable in a burst. Rewrites are permitted
where justified (Q4=C), always paired with an incremental alternative.

## Backlog Organization

**Hybrid: priority theme → area/service within** (Q1=D). See `intent-backlog.md`.

## Success Criteria (from intent-statement)

- Prioritized GitHub-issue backlog covering all four themes ✓
- Explicit quick-wins list ✓
- Phased roadmap (near/mid/long) ✓
- Every finding evidence-linked ✓
