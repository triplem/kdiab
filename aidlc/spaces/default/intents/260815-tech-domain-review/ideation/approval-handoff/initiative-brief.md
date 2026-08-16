# Initiative Brief — Technology & Domain Review (Ideation → Inception handoff)

**Intent:** review technology and domain and suggest improvements
**Scope:** enterprise (comprehensive) · **Type:** assessment → recommendations (implementation deferred)
**Date:** 2026-08-15 · **Owner:** solo maintainer

## One-paragraph summary

Review the **kdiab** T1D platform's technology and T1D domain, and produce a **prioritized, evidence-linked
improvement plan** (GitHub-issue backlog + quick wins + phased roadmap). Priorities, in order: **clinical
correctness & safety → security & compliance → tech debt → modernization**. Output is recommendations only;
the value lands by end of Inception.

## Consolidated from Ideation

| Dimension | Outcome | Source stage |
|---|---|---|
| **Problem / goal** | Systematic, prioritized assessment of tech health + T1D domain correctness | intent-capture |
| **Priorities** | Clinical-safety (1) → security/compliance (2) → tech-debt (3) → modernization (4); perf & interop **out** | intent-capture |
| **Landscape** | vs. Nightscout / **Nocturne** (.NET rewrite) / Tidepool; `kdiab-calc` is a differentiator | market-research |
| **Build-vs-buy** | **Keep `kdiab-calc`, validate vs. AAPS/Loop as oracles — do NOT embed an AID engine** | market-research |
| **Feasibility** | PROCEED; **compliance elevated** → MDR/SaMD + GDPR special-category | feasibility |
| **Constraints** | Solo + occasional bursts; self-hostable; **no hard tech fences** (rewrites OK) | feasibility |
| **Scope / backlog** | 5 areas in; **20 proto-units** across 4 themes; **MVP = dose-calc safety** | scope-definition |
| **Team** | Solo; **clinical-domain gap** → external validation advised for dosing | team-formation |
| **UI** | N/A — output is documents, not screens | rough-mockups |

## The single most important thread

**Dose-calculator clinical correctness (`kdiab-calc`).** It is the MVP-critical area (Q2=A), the top RAID
risk (RK1, Critical/safety), the build-vs-buy focus, and — given the compliance aim — a potential
**Software-as-a-Medical-Device** concern. If this review does one thing, it makes `kdiab-calc` provably safe.

## Handoff to Inception

Inception begins with **Reverse-Engineering (2.1)** — a real code scan of the monorepo — which will gather
the evidence to turn the 20 proto-units into concrete, verified findings. Priority verification targets
(from `raid-log.md` assumptions A1–A6):

1. `kdiab-calc` IOB subtraction + dosing guardrails + unit handling `[safety]`
2. Kover-coverage reality + test-pyramid balance
3. Detekt baseline debt (suppressed findings)
4. Code duplication across services
5. Keycloak passkeys/OIDC support
6. TIR/AGP/HbA1c definition correctness

## Recommended checkpoint

**End of Inception is the natural finish/park point** for a recommendations-only review. Construction/Operation
carry no build mandate unless the maintainer later chooses to implement.
