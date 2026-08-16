# Scope Definition — Clarifying Questions

**Intent:** review technology and domain and suggest improvements
**Scope:** enterprise (comprehensive depth); **Mode:** guided (presented inline)

> The review's boundaries are largely set by prior stages (all 5 areas in; priorities clinical-safety >
> security/compliance > tech-debt > modernization; interoperability/performance out). These two questions
> settle the remaining scope choices: how to *organize* the improvement backlog, and the one area that is
> non-negotiable if capacity forces a cut.

---

## Q1. How should the improvement backlog (Intent Backlog / proto-units) be organized?
- A. By **review area** (backend/arch · frontend · data · domain-safety · cross-cutting)
- B. By **priority theme** (clinical-safety · security/compliance · tech-debt · modernization)
- C. By **service** (per the 9 kdiab-* services)
- D. **Hybrid** — priority theme first, then area/service within each theme
- X. Other (please specify)

[Answer]: D — hybrid (priority theme → area/service within)

## Q2. If capacity forces a cut, which ONE area is non-negotiable to cover deeply?
- A. Dose-calc clinical correctness & safety (`kdiab-calc` — the elevated MDR/safety finding)
- B. Security & compliance (GDPR / health-data protection)
- C. Code health (test pyramid + Detekt/static-analysis + duplication)
- D. Architecture boundaries (the 9-service-for-one-user tension)
- X. Other (please specify)

[Answer]: A — dose-calc clinical correctness & safety (non-negotiable)
