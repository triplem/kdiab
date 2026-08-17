# Escalation Matrix — Review Deliverable Incidents

> Stage 4.5. Who acts, in what order, per severity. Solo-maintainer reality: escalation is mostly
> *self*-escalation to a stricter gate (a clinical review skill) rather than paging another person — with
> an external clinical advisor recommended as the one human escalation worth adding for P0.

## Roles

| Role | Who | Responsibility |
|---|---|---|
| Maintainer | the solo maintainer (`@triplem`) | Detect, triage, run the runbook, own the fix |
| Clinical gate | `/doctor-t1d-review` (+ `/patient-t1d-review` for UX) | Mandatory sanity check before re-merging any clinical-safety change |
| Clinical advisor *(recommended, not yet in place)* | an external endocrinologist / T1D clinician | Human sign-off for a P0 patient-safety incident |

## Escalation by severity

| Sev | Step 1 | Step 2 | Step 3 |
|---|---|---|---|
| **P0** clinical/patient-safety | Maintainer: **rollback immediately** (R3) | Clinical gate `/doctor-t1d-review` before any re-attempt | Clinical advisor sign-off (recommended) + mandatory post-incident review |
| **P1** high | Maintainer: rollback (R4) | Relevant review skill if domain-sensitive | Post-incident review |
| **P2** medium | Maintainer: fix on branch (R1/R5) | verify.py gate | Post-incident review |
| **P3** low | Maintainer: supersede/update next burst (R2/R5) | — | — |
| **P4** lowest | Maintainer: doc fix in backlog | — | — |

## Contacts

| Channel | Value |
|---|---|
| Incident record | the reopened GitHub finding issue (labelled `review` + `area:*` + `severity:*`) |
| Maintainer | `@triplem` (GitHub) |
| Clinical gate | `/doctor-t1d-review`, `/patient-t1d-review` (repo skills) |
| Clinical advisor | *(gap — recommend establishing a named external T1D clinician for P0 sign-off)* |

## Escalation triggers (when to go up a step)

- A P0 rollback does **not** restore safe dose behaviour → escalate to clinical advisor immediately; do
  not iterate on the dose path alone.
- A clinical-safety change fails `/doctor-t1d-review` twice → stop; the finding needs re-scoping, not
  another attempt.
- Any uncertainty about whether an incident is patient-affecting → treat as **P0** until proven otherwise
  (safety-first default, consistent with ADR-RVW-004 Critical=clinical).

## Notes

- No PagerDuty/on-call rotation — inappropriate for a solo-maintainer project; the discipline is the
  rollback-first + clinical-gate protocol, not staffing.
- The single most valuable escalation improvement is establishing the external clinical advisor contact
  before the first clinical-safety recommendation ships (proactive, not reactive).
