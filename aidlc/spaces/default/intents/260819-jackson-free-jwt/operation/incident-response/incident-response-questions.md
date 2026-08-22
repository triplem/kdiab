# Incident Response — Clarifying Questions (#1606 jackson-free JWT)

Operation phase · Standard depth · lead aidlc-operations-agent.

The incident doctrine for #1606 is mostly fixed: top incident = the Nimbus provider regressing auth
(wrong-reject → T1D fleet lockout / wrong-accept → security hole), response = rollback-first via
`../deployment-pipeline/rollback-runbook.md`, detection = discovery today / `alarms.md` when a running
env exists, severities = the platform's P0–P4 labels. Two escalation parameters aren't yet pinned.

---

## Q1. Who signs off a P0 auth incident?

ADR-023 recommends establishing an external advisor for the safety-sensitive auth path (a solo
maintainer has no on-call).

- A. **Solo maintainer today; recommend establishing an external security/clinical advisor** for P0
     auth sign-off (documented as an open action). (Realistic default.)
- B. An external security/clinical advisor **already exists** — name them as the P0 sign-off in the
     escalation matrix.
- C. Solo maintainer only; no external advisor recommended.
- X. Other (please specify)

[Answer]: C — Solo maintainer only; no external advisor. The maintainer is the single sign-off authority for all severities incl. P0. (Guided, 2026-08-21)

---

## Q2. Is a domain-review gate mandatory before re-attempting a reverted auth change?

After an auth regression is rolled back, before the fix is re-attempted and re-merged.

- A. **Mandatory for P0/P1**: `/doctor-t1d-review` (clinical safety) **and** `/patient-t1d-review`
     (data-access UX) must pass before any re-attempt of a reverted auth change. (Safety-first.)
- B. Mandatory `/doctor-t1d-review` only; patient review optional.
- C. Recommended, not mandatory — maintainer judgement.
- X. Other (please specify)

[Answer]: C — Recommended, not mandatory. `/doctor-t1d-review` + `/patient-t1d-review` are encouraged tools for a reverted auth re-attempt but do not gate the re-merge; maintainer judgement decides. Accepted residual risk of a solo, non-gating posture is documented in escalation-matrix.md. (Guided, 2026-08-21)
