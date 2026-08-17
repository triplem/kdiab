# Incident Response — Clarifying Questions

> Stage 4.5 (Incident Response), enterprise scope, Operation phase. Lead: aidlc-operations-agent.
> Recommendations-only intent: incidents are (1) **deliverable defects** (integrity/currency — low
> severity, revert/supersede) and (2) **implemented-recommendation regressions** — a finding that was
> acted on and broke something in the live kdiab platform. Because kdiab is a **T1D safety platform**,
> the highest-severity incident is a clinical-safety regression (a dose/IOB change that misbehaves), i.e.
> genuine patient-safety. Consumes: observability `dashboards.md`/`alarms.md` (exist); nfr/infra design
> consumes were skipped. Design-only (no install). Three targeted decisions.

---

## Q1 — Severity model for review-deliverable incidents

- A. **Two-track, reuse the review severity scale.** Deliverable-defect incidents map to Low/Med;
  implemented-recommendation regressions inherit the finding's severity, and a **clinical-safety
  regression is always P1 (patient-safety)** regardless of the finding's paper severity. *(recommended —
  keeps Critical=clinical, ADR-RVW-004, and makes safety the top class)*
- B. Adopt the platform's `P0–P4` labels wholesale for review incidents too.
- C. Single flat "review incident" severity (no tiers).
- X. Other (please specify)

[Answer]: B — Adopt the platform's P0–P4 labels for review incidents too (consistent with the repo's existing severity taxonomy). Mapped: a clinical-safety regression = P0 (patient-safety), non-clinical implemented regression = P1, deliverable integrity = P2, currency drift = P3, cosmetic = P4. Safety-first default: uncertain → P0.

---

## Q2 — Clinical escalation path (a T1D patient-safety incident)

If an **implemented** dose/IOB recommendation misbehaves in production, who/what is the escalation?

- A. **Rollback-first + clinical sanity gate.** Immediately `git revert` the fix (rollback-runbook §1),
  then require a clinical re-review via the `/doctor-t1d-review` skill (and `/patient-t1d-review` for UX)
  before any re-attempt; solo maintainer owns coordination. *(recommended for a solo maintainer with no
  formal on-call)*
- B. External endocrinologist advisor is paged/consulted before rollback.
- C. Maintainer judgement only (no defined clinical gate).
- X. Other (please specify)

[Answer]: A — Rollback-first + clinical sanity gate: immediately git revert the fix, then require /doctor-t1d-review (+ /patient-t1d-review for UX) before any re-attempt; solo maintainer coordinates. Escalation-matrix also recommends establishing an external clinical advisor for P0 sign-off.

---

## Q3 — Post-incident feedback into the deliverable

Operation guardrail: P1/P2 incidents require a post-incident review. Should a post-incident review from a
bad recommendation **update the deliverable**?

- A. **Yes — close the loop.** A P1/P2 caused by a recommendation supersedes/annotates the originating
  finding (rollback-runbook §2) **and** adds a preventive rule to `CONVENTIONS.md` so the class can't
  recur; re-run `verify.py`. *(recommended)*
- B. Post-incident review is recorded externally only (issue/wiki); the deliverable is not edited.
- C. No formal post-incident review for a solo-maintainer project.
- X. Other (please specify)

[Answer]: A — Close the loop: a P0/P1/P2 caused by a recommendation supersedes/annotates the originating finding AND adds a preventive rule to CONVENTIONS.md so the class can't recur; re-run verify.py. Makes the review a learning artifact, not a static snapshot.
