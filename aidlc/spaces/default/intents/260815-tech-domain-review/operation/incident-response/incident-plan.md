# Incident Response Plan — Review Deliverable

> Stage 4.5. The lifecycle and policy wrapping the runbooks. Solo-maintainer context: "incident response"
> is a disciplined personal process, not a staffed on-call rotation. Detection is driven by
> `observability-setup/alarms.md`.

## Scope of "incident"

1. **Deliverable incidents** — the review docs became inconsistent (integrity) or stale (currency).
2. **Recommendation incidents** — a finding that was implemented regressed the live kdiab platform. The
   P0 sub-class is a clinical-safety regression (patient-safety).

The kdiab platform's own runtime incidents (service outage, latency) are handled by the platform's
existing operations, out of scope here — except where they were *caused by* an implemented review
recommendation (then R3/R4 apply).

## Severity → response time (P0–P4, Q1=B)

| Sev | Acknowledge | First action | Post-incident review |
|---|---|---|---|
| P0 (clinical/patient-safety) | immediate | **rollback first** (R3) | **required** |
| P1 (high) | same day | rollback (R4) | **required** |
| P2 (medium) | ≤ 3 days | fix on branch (R1/R5) | required |
| P3 (low) | next burst | supersede/update (R2/R5) | optional |
| P4 (lowest) | backlog | doc fix | none |

## Lifecycle

```
DETECT  ->  TRIAGE  ->  RESPOND  ->  RESOLVE  ->  REVIEW
  |          |            |            |           |
 alarms    assign P0-P4  runbook     confirm     post-incident,
 (A1-A5)   (this table)  (R1-R5)     safe/green  close-the-loop
```
<!-- Text fallback: linear incident lifecycle — detect (from observability alarms), triage (assign a
P0-P4 severity), respond (run the matching runbook R1-R5), resolve (confirm safe/green state), review
(post-incident review for P0/P1/P2, feeding corrections back into the deliverable). -->

## Detection sources (from observability-setup)

| Alarm | Incident | Runbook |
|---|---|---|
| A1 (`review-verify` red) | deliverable integrity (P2) | R1 |
| A2 (currency drift issue) | stale finding (P3) | R2 |
| A5 (non-clinical Critical) | severity discipline (P2) | R1 |
| external (production behaviour) | implemented-recommendation regression (P0/P1) | R3/R4 |

## Post-incident review (Q3=A close-the-loop)

Mandatory for P0/P1/P2. The review captures: timeline, root cause, and **prevention** — and prevention is
written back into the deliverable: supersede/correct the originating finding and add a rule to
`CONVENTIONS.md` so the class can't recur, then re-run `verify.py`. This makes the review a learning
artifact, not a static snapshot.

## Communication

Solo maintainer: the GitHub issue is the incident record (reopened finding issue + a comment timeline).
For a P0 clinical incident, the clinical gate (`/doctor-t1d-review`) result is attached to that issue
before re-merge.
