# Alarms — Review Deliverable Observability

> Stage 4.4. Alert conditions for the deliverable's health/currency/progress. Per the operation-phase
> guardrail, alerting thresholds sit **below** the SLO breach so there is time to remediate (see
> `slo-config.md`). There is no paging/on-call for a solo-maintainer docs deliverable — "alarm" means a
> visible signal (a failed check, an auto-opened issue, a step-summary warning).

## Alarm catalogue

| ID | Condition | Signal / channel | Severity | Response |
|---|---|---|---|---|
| A1 | `review-verify.yml` **red** on a `docs/review/**` PR or push | Failing required check (blocks merge once branch protection is on) | High | Fix the flagged integrity defect before merge (see `verify.py` output) |
| A2 | Currency drift: a cited full-path anchor **changed or deleted** since baseline | `review-monitor.yml` auto-**upserts a single** "Review currency drift" issue (idempotent) + step summary | Medium | Re-verify the affected finding(s) against live `main`; update or supersede per `rollback-runbook.md` §2 |
| A3 | A **High**-severity finding open > **14 days** | Manual / label-query (`log-queries.md`); no auto-page | Medium | Pull it into the current burst (safety-weighted) |
| A4 | Epic **stalled** — no sub-issue closed in a rolling **90 days** | Manual burn-down review (D1) | Low | Decide: resume, re-prioritize, or accept-and-park remaining items |
| A5 | A **Critical** severity appears on a non-clinical finding | `verify.py` `severity-discipline` fails (→ A1 red) | High | Reclassify (ADR-RVW-004: Critical reserved for patient-safety) |

## Threshold rationale

- **A1/A5 are blocking** (gate-red) — an integrity/severity violation is a defect, not a warning
  (pipeline-deploy key principle 4).
- **A2 fires weekly** (the monitor cadence) — drift is detected within ≤ 7 days of a `main` change,
  comfortably ahead of the "0 broken anchors *at act-time*" SLO because the authoritative check is the
  just-in-time re-verify before pulling a finding (Q3=A). The weekly alarm is the early warning.
- **A3 (14 days) / A4 (90 days)** are advisory review nudges, not automated blocks — tuned for a
  solo-maintainer cadence, well inside the roadmap band targets in `slo-config.md`.

## What is deliberately NOT alarmed

- No latency / error-rate / saturation alarms — there is no runtime service in this deliverable. The
  kdiab platform's runtime alarms live in its own OTEL stack, out of scope here.
