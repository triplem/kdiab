# Feasibility Assessment — Technology & Domain Review

**Intent:** review technology and domain and suggest improvements (see `intent-statement.md`)
**Lead:** Architect · **Support:** AWS-Platform (N/A here), Compliance
**Date:** 2026-08-15

> **Upstream inputs consulted:** `intent-statement.md` (assessment initiative, priorities C/D then A/B),
> `competitive-analysis.md` (kdiab vs. Nightscout/Nocturne/Tidepool), `market-trends.md` (auth + TIR/AGP +
> dosing signals), `build-vs-buy.md` (keep `kdiab-calc`, prove correctness).

## 1. Is the review feasible? — Yes (high confidence)

The initiative is an **assessment producing recommendations** (Q9=A), against an existing, well-structured
brownfield codebase (9-service hexagonal monorepo with CI, tests, docs). There is no technical blocker to
*reviewing* it. The real feasibility questions concern the **improvements** the review will recommend, and
the **capacity** to act on them.

## 2. Feasibility of the contemplated improvements

| Factor | Reading | Implication |
|---|---|---|
| **Capacity** (Q1 = occasional bursts) | Work must be packageable into **self-contained, burst-sized chunks** | Roadmap sequenced by *independent shippable units*, not calendar; favor high-leverage quick wins |
| **Change appetite** (Q4 = open to anything) | Large rewrites are *permitted* | Big-swing options stay on the table — but paired with **honest cost/risk** given solo capacity |
| **Constraints** (Q7 = none) | No hard tech fences | Recommendations unconstrained; pragmatism (self-hostable, solo) is advisory not mandatory |
| **Existing quality bar** | Kover ≥80%, Detekt, CI gates already enforced | High baseline — improvements build on solid ground, not rescue a mess |

**Net:** improvements are feasible; the binding constraint is **solo maintainer bandwidth**, so *value
density* (impact per hour) is the ranking criterion for the eventual backlog.

## 3. Compliance feasibility — the elevated finding

**Q2 = aiming toward formal compliance (GDPR / medical-device).** This changes the risk posture:

- **GDPR (EU):** kdiab stores T1D health data — **special-category personal data** under GDPR Art. 9.
  Formal compliance implies data-minimization, encryption at rest/in transit, access logging, subject-access
  / erasure support, and a lawful basis. `[compliance — verify current state during RE]`
- **Medical Device (MDR / SaMD) — `[safety][high]`:** `kdiab-calc` recommends **insulin doses**. Software
  that informs dosing decisions can fall under the EU Medical Device Regulation as *Software as a Medical
  Device*. "Aiming for formal compliance eventually" means the review must **flag** this now (clinical
  validation, risk management per ISO 14971, traceability, an "advisory-only, not a medical device" posture
  decision) even though certification is not this workflow's job. This makes the **dose-calculator
  correctness thread the highest-stakes item** — reinforcing `build-vs-buy.md`.

## 4. AWS / infrastructure feasibility

**Not applicable.** kdiab is **self-hosted** (Docker/Podman compose, PostgreSQL, Keycloak) with no AWS
dependency; this project deliberately uses no cloud/Bedrock. The AWS-Platform support perspective reduces to:
*keep the self-hosted posture; any "cloud-native" ideas from the Nocturne comparison are explicitly out of
scope* (Q2 positioning = personal self-hosted).

## 5. Technical viability of the review method

The review will proceed through **Reverse-Engineering (2.1)** — a real code scan of the monorepo — feeding
Requirements/Application-Design. This is the correct vehicle to verify the open safety/correctness questions
(kdiab-calc IOB & guardrails, TIR/AGP definitions, passkeys/OIDC, test-pyramid health, Detekt debt,
duplication). All are inspectable from the repo; no external access or vendor cooperation needed. **Feasible.**

## 6. Verdict

**PROCEED.** The review is low-risk and high-value. The improvements are feasible within a burst-capacity,
rewrite-tolerant solo context. The compliance answer (Q2=C) **elevates** two threads to top priority:
dose-calculator clinical correctness (potential SaMD) and GDPR handling of special-category health data.
