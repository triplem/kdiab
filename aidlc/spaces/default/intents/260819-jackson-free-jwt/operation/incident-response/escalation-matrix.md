# Escalation Matrix — U1 Jackson-free JWT (#1606)

Consumes `../observability-setup/alarms.md`, `../observability-setup/dashboards.md`. (`reliability-design`,
`security-design`, `deployment-architecture` from 3.3/3.4 are **N/A — skipped**.)

## Authority: solo maintainer (Q1 = C)

Per the stage decision, the **maintainer is the single sign-off authority for all severities,
including P0**. No external advisor and no on-call rotation exist. This is a deliberate solo-developer
posture; the residual risk is stated below and accepted.

## Severity → response

Reuses the platform's existing **P0–P4** labels. Severity assignment follows `incident-plan.md`
("uncertain → up", T1D-safety default).

| Severity | Example (#1606) | Target response | Sign-off | First action |
|---|---|---|---|---|
| **P0** | Fleet lockout (wrong-reject) or wrong-accept security hole | Immediate — drop everything | Maintainer | **Roll back** (`runbooks.md` RB-1/RB-2) |
| **P1** | Provider `5xx` on auth path | Same day | Maintainer | Roll back (RB-3) |
| **P2** | Parity drift / supply-chain regression | Next working session | Maintainer | Investigate / forward-fix (RB-5) |
| **P3/P4** | Config-drift reasons (`wrong-audience` step) without lockout | Backlog | Maintainer | Diagnose config |

## Domain review on re-attempt (Q2 = C — recommended, not gating)

After a rollback, before re-attempting the jackson-free swap, `/doctor-t1d-review` (clinical safety)
and `/patient-t1d-review` (data-access UX) are **recommended tools, not merge gates**. The maintainer
decides per incident whether to run them. They do not block the re-merge; the hard gate remains the
full CI suite + the ADR-023 manual security sign-off.

## Notification

Alerts (when a running env arms them — `alarms.md`) route to the maintainer's own channel. No paging
tier, no secondary escalation.

## Accepted residual risk (explicit)

A solo, non-gating posture means: (1) no independent second opinion on a P0 auth sign-off, and
(2) no mandatory clinical/UX review before an auth change is re-attempted. This is accepted for the
project's scale. Mitigating factors that make it tolerable for #1606 specifically:

- The change is **behaviour-preserving by design** (ADR-023 parity) and proven by the CI negative-path
  matrix before it can merge.
- Rollback is **fast and single-command** (`git revert -m 1`), so an incident is quickly reversible.
- The **ADR-023 manual security sign-off** is still a hard pre-merge gate on the auth path — the one
  human checkpoint that is not waived.

If the platform's user base or operational footprint grows, revisit Q1/Q2 — establishing an external
security/clinical advisor and making the domain-review gate mandatory are the natural hardening steps
(recorded as a forward action, consistent with ADR-023's own recommendation).
