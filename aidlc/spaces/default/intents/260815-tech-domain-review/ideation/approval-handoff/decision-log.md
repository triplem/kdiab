# Decision Log — Ideation Phase

**Intent:** review technology and domain and suggest improvements
**Date:** 2026-08-15

> Every decision below was made and approved at its stage gate. Recorded here so Inception (and future
> sessions) do not re-litigate them. Format: DECIDED — [decision] (source, rationale).

| # | Decision | Source | Rationale |
|---|---|---|---|
| D1 | **Scope = enterprise**, but treated as **recommendations-only** with a park point at end of Inception | scope confirm + intent-capture (Q9=A) | User confirmed enterprise knowingly; output is analysis, not a build |
| D2 | **Priority order:** clinical-safety → security/compliance → tech-debt → modernization; perf & interop **out** | intent-capture (Q2, Q6) | Safety-first for a T1D tool; interop/perf deprioritized by user |
| D3 | **Backlog organized hybrid** (priority theme → area/service) | scope-definition (Q1=D) | Value-density ranking fits burst capacity |
| D4 | **MVP-critical area = dose-calc clinical safety** (`kdiab-calc`) | scope-definition (Q2=A) | Highest stakes; non-negotiable |
| D5 | **Build-vs-buy: keep `kdiab-calc`, align to AAPS/Loop as reference oracles; do NOT embed an AID engine** | market-research | AID engines are safety/scope/regulatory-heavy; bolus formula is public spec |
| D6 | **No hard tech constraints; rewrites permitted** | feasibility (Q7=E, Q4=C) | User open to anything; pragmatism advisory only |
| D7 | **Compliance elevated:** flag **MDR/SaMD** for the dose calculator + **GDPR special-category** handling | feasibility (Q2=C) | User aiming for formal compliance eventually |
| D8 | **Self-hosted only; no AWS/cloud/multi-tenant** | feasibility + project rule | kdiab is personal self-hosted; AWS removed from project |
| D9 | **Team = solo;** clinical-domain gap → recommend external clinical validation for dosing | team-formation | One person can't self-validate therapy safety |
| D10 | **No UI work** — output is issues/roadmap docs | rough-mockups | Review, not feature |
| D11 | **Deviation:** skipped §12a reviewer on rough-mockups (N/A artifact) | rough-mockups diary | No value reviewing a not-applicable note; avoid needless agent spawn |

## Traceability check (Ideation → Inception)

- Every proto-unit in `intent-backlog.md` traces to a priority theme in `scope-document.md`, which traces to
  a priority in `intent-statement.md`. ✓
- Every RAID assumption (A1–A6) has a verification target queued for Reverse-Engineering. ✓
- No unresolved contradictions carried forward. ✓
- Out-of-scope items (interop, perf, multi-tenant, implementation) explicitly recorded. ✓

**Ideation is complete and internally consistent. Cleared for Inception.**
