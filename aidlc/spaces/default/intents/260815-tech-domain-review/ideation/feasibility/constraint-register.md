# Constraint Register — Technology & Domain Review

**Intent:** review technology and domain and suggest improvements
**Date:** 2026-08-15

> Constraints that bound the *recommendations* this review will produce. "Hard" = must respect; "Soft" =
> strong preference, overridable with justification (Q7 = no hard tech constraints, so most are soft/pragmatic).

## Technical constraints

| # | Constraint | Type | Source |
|---|---|---|---|
| T1 | Solutions must remain **self-hostable** (Docker/Podman, no mandatory managed cloud / no AWS/Bedrock) | Soft (strong) | Q2 positioning; project rule `project.md` |
| T2 | No hard "must stay on X" stack fence — rewrites permitted | — (freedom) | Q7=E, Q4=C |
| T3 | Recommendations should preserve the existing quality gates (Kover ≥80%, Detekt, CI green before merge) | Hard | Project rules `quality-gates.md` |
| T4 | Changes must fit the trunk-based, PR-per-change, no-direct-to-main workflow | Hard | `branching-strategy.md`, commit-guard hook |
| T5 | Keep Nightscout v1 API compatibility unless explicitly retiring it (external clients: AAPS/xDrip+/Juggluco) | Soft (strong) | Domain interop; not a review priority (Q6) but a live constraint |

## Organizational / capacity constraints

| # | Constraint | Type | Source |
|---|---|---|---|
| O1 | **Solo maintainer** — one person designs, implements, operates | Hard | Q3=A |
| O2 | **Occasional-burst** capacity — work must be self-contained and independently shippable | Hard | Q1=C |
| O3 | No external deadline / low-pressure (health-check trigger) | Soft | Q4 (intent-capture) |

## Regulatory / compliance constraints

| # | Constraint | Type | Source |
|---|---|---|---|
| R1 | Health data is **GDPR special-category** (Art. 9) — data-minimization, encryption, access control, erasure | Hard (if EU/real users) | Q2=C |
| R2 | `kdiab-calc` may be **Software as a Medical Device (EU MDR)** — advisory-only posture must be explicit; clinical validation + ISO 14971 risk mgmt flagged | Hard (aspirational) | Q2=C + dose-calc |
| R3 | Auth/security must be defensible for health data (see security priority Q2=D) | Hard | Q2=D |

## Explicitly NOT constraints (freedoms this review has)

- May recommend **rewrites** of individual components (Q4=C).
- May recommend **dropping** features/services if they don't earn their keep (Q7=E).
- Not bound to chase interoperability breadth or performance (deprioritized Q6/Q2).
