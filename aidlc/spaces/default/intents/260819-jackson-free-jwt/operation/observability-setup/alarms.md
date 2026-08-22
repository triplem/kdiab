# Alarms — U1 Jackson-free JWT (#1606)

Consumes (all N/A — stages 3.3/3.4 skipped): `nfr-design/reliability-design.md`,
`nfr-design/security-design.md`, `nfr-design/performance-design.md`,
`infrastructure-design/monitoring-design.md`, `infrastructure-design/infrastructure-services.md`.
Sourced from the auth signal (`log-queries.md`) + Prometheus/Micrometer.

Design-ready alert rules (no running prod to arm them today). Alert on what a human must act on for a
safety-sensitive auth change; everything else is a dashboard, not a page.

## Alert rules (auth-specific — the #1606 additions)

| Alert | Condition | Severity | Rationale |
|---|---|---|---|
| `AuthProviderErrors` | any `5xx` on an authed route > 0 for 5m | **P1** | The Nimbus provider must return `401`, never `500`. A 5xx = provider bug (parity break) → candidate rollback (`rollback-runbook.md`). |
| `InvalidClaimsSpike` | `invalid-claims` rate > baseline×3 (or > N/min absent a baseline) for 10m | **P1** | Parity canary — the exception-guarded claim mapping diverging from java-jwt semantics. |
| `BadSignatureSpike` | `bad-signature` rate > small threshold, sustained 10m | **P2/security** | Possible forgery or JWKS/key-rotation failure. |
| `AudienceIssuerSpike` | `wrong-audience`+`wrong-issuer` rate step-change for 10m | **P3** | Audience-mapper / realm misconfig or cross-service token misuse. |
| `FleetWide401Surge` | 401 rate across ≥ half the services jumps together | **P1** | Fleet-wide wrong-reject = every user locked out (the deployment-strategy blast radius). |

Thresholds are **relative to the java-jwt baseline** where possible — #1606 is a parity change, so the
right alarm is "diverged from prior behaviour," not an absolute rate. `expired`/`no-token` are
deliberately **not** alerted (routine churn) to avoid alert fatigue (operations Key Principle 1).

## Notification / on-call

Solo-maintainer footprint — no on-call rotation. Alerts route to the maintainer's channel; ADR-023
recommends an external security/clinical advisor for P0/P1 auth sign-off. Consistent with
`incident-response` (4.5), which owns the response side.

## Arming status

**Design-ready, not armed** — no running Prometheus/Alertmanager in a prod to load these rules into.
They arm when a running environment exists (forward hook). Until then the standing "alarm" is the CI
gate: a parity break is caught by the negative-path test matrix before merge, not by a live alert.
