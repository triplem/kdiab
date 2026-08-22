# Incident Plan — U1 Jackson-free JWT (#1606)

Consumes `../observability-setup/dashboards.md`, `../observability-setup/alarms.md`. (`reliability-design`,
`security-design`, `deployment-architecture` from 3.3/3.4 are **N/A — skipped**; sourced from ADR-023 +
`alarms.md` + `../deployment-pipeline/rollback-runbook.md`.)

## Top incident class: auth regression from the jackson-free swap

The Nimbus custom provider is fleet-wide (`kdiab-common` `configureSecurity()`). The incidents #1606
can cause:

| Incident | Symptom | Impact | Severity |
|---|---|---|---|
| **Wrong-reject** | valid Keycloak tokens rejected (`401`) | Every user locked out of every service — **patients cannot reach glucose/insulin/dosing data** | **P0** (T1D safety) |
| **Wrong-accept** | invalid/tampered tokens accepted | Cross-platform security hole — unauthorized access to patient data | **P0** (security) |
| **Provider 5xx** | auth path returns `500` not `401` | Broken error contract; possible partial outage | **P1** |
| **Parity drift** | `invalid-claims`/`bad-signature` rate diverges from java-jwt baseline | Subtle behavioural change; early warning of the above | **P2** (investigate) |
| Supply-chain regression | jackson/java-jwt reappears or downgrades to 2.21.3 | CVE re-exposure (the whole point of #1603 undone) | **P2** |

On a T1D platform, "uncertain severity → treat as P0" — a patient unable to access dosing data under
an auth outage is a safety event, not a mere availability blip.

## Response doctrine: ROLLBACK-FIRST

**Never forward-fix an auth path under incident pressure.** For any P0/P1 auth incident the first
action is to roll back via `../deployment-pipeline/rollback-runbook.md` (source-level `git revert -m 1`
→ CI republish). Diagnose only after service/security is restored. Rationale: an auth defect that is
misdiagnosed under pressure can convert a lockout into a security hole (or vice-versa).

## Detection

- **Today (no running prod)**: detection is **discovery-based** — a defect found in CI, the ADR-023
  manual security review, CodeQL, or manual testing before/at merge. This is why the merge gate is the
  real safety control for #1606.
- **When a running env exists**: the `alarms.md` rules (`AuthProviderErrors`, `InvalidClaimsSpike`,
  `BadSignatureSpike`, `FleetWide401Surge`) driven by the `dashboards.md`/`log-queries.md` signals
  provide live detection.

## Lifecycle

Detect → declare severity (default up on uncertainty) → **roll back** (P0/P1) → verify restore (CI green
on the revert; the deferred auth smoke if a running env exists) → **close the loop**: reopen #1606
(reuse, don't duplicate), annotate ADR-023 with the failure mode, add a preventive check/convention so
the class can't recur, and — recommended — run `/doctor-t1d-review` + `/patient-t1d-review` before the
re-attempt (not gating; see `escalation-matrix.md`).

## Not-in-scope incidents

Generic platform incidents (DB down, Keycloak outage, node failure) are pre-existing and unchanged by
#1606 — they follow the platform's existing operational practice, not this plan. This plan covers only
the auth-verification-swap incident class.
