# Initiative Brief — Jackson-free JWT Verification (#1606)

One-page Ideation summary and go/no-go, compiled by the delivery-agent with product-agent validation.
Refs #1603. Sources: `../intent-capture/intent-statement.md`, `../scope-definition/scope-document.md`,
`../scope-definition/intent-backlog.md`, `../market-research/competitive-analysis.md`,
`../feasibility/feasibility-assessment.md`, `../feasibility/constraint-register.md`.
(`team-assessment` and `wireframes` were **skipped** — solo maintainer + AI agents, and no UI.)

## Summary

- **Problem** (`intent-statement.md`): `ktor-server-auth-jwt` transitively pulls `java-jwt` + `jwks-rsa` → jackson, the last jackson consumer epic #1603 targets (logback #1605 and Swagger #1607 already removed). Jackson is recurring CVE surface, currently held back by a force-pin.
- **Market/library validation** (`competitive-analysis.md`): a two-horse race — adopt **Nimbus** vs build a custom verifier. Both are jackson-free.
- **Feasibility** (`feasibility-assessment.md`): **HIGH / GO**, evidence-backed. `dependencyInsight` on kdiab-common + kdiab-measures proves jackson enters **only** via `ktor-server-auth-jwt`. DoD is provably reachable.
- **Scope** (`scope-document.md`): drop `ktor-server-auth-jwt`; keep `ktor-server-auth` + its **`bearer("auth-jwt")`** provider + **Nimbus** verification; preserve `buildPrincipal`/JWKS-hardening/challenge exactly; risk-first parity tests; realm config in scope if needed; remove the force-pin in the same PR gated on a platform-wide sweep. One atomic PR.
- **Backlog** (`intent-backlog.md`): 6 proto-Units (PU-1 parity tests → PU-2 build deps → PU-3 Nimbus verifier → PU-4 realm/config → PU-5 sweep+pin removal → PU-6 ADR).
- **Team/UI**: `team-assessment` skipped (solo + AI-DLC agents; external security reviewer recommended for the mandated review); `wireframes` skipped (backend-only, no user-visible change).

## Key Risks & Mitigations (from `constraint-register.md` + RAID)

| Risk | Mitigation |
|---|---|
| Behaviour drift in claim extraction / JWKS handling (auth bug) | Risk-first characterization tests pin current behaviour; full auth e2e is a merge gate |
| We own more crypto (custom) — rejected | Chose **Nimbus** (audited) to minimize owned crypto on a safety-sensitive path |
| json-smart (added by Nimbus) CVE | Trivy/CodeQL + security review; still jackson-free so DoD holds |
| Removing force-pin downgrades a surviving jackson into a CVE | Pin removal gated on a clean platform-wide `dependencyInsight` sweep; else keep pin |

## Go / No-Go Recommendation

**GO.** The DoD is proven reachable with a bounded, single-file (+ build) change; the approach keeps
Ktor's auth framework central (honouring the maintainer's preference) while eliminating jackson; the
safety bar (parity tests + security review + dependency proof + full CI) is defined. No blockers, no
compliance constraint beyond the mandated security review. Recommend proceeding to **Inception**.
