# Anomaly Detection — Review Deliverable

> Stage 4.4. "Anomalies" for a docs deliverable are integrity/currency violations, not statistical
> outliers in a metric stream. Each anomaly maps to a deterministic check (in `verify.py` or
> `monitor.py`) — no ML baseline needed; correctness is binary.

## Anomaly catalogue

| ID | Anomaly | Detector | Kind | Feeds |
|---|---|---|---|---|
| AN1 | Cited full-path anchor **deleted** | `monitor.py` (`missing`) | currency | A2, D3 |
| AN2 | Cited full-path anchor **changed** since baseline | `monitor.py` (`changed`) | currency | A2, D3 |
| AN3 | Backlog count ≠ theme-doc actionable count ("N actionable findings" mismatch) | `verify.py` `backlog-traceability` + `readme-numbers` | integrity | A1 |
| AN4 | Phase drift (theme/backlog/roadmap disagree) | `verify.py` `phase-authority` | integrity | A1 |
| AN5 | Non-clinical **Critical** severity | `verify.py` `severity-discipline` | integrity | A1, A5 |
| AN6 | Broken intra-set link | `verify.py` `dead-links` | integrity | A1 |
| AN7 | Secret pattern committed in the docs | `verify.py` `no-secrets` | security | A1 |
| AN8 | A finding stuck In-Progress with no PR | label/query review (`log-queries.md`) | progress | A3 |

## Detection cadence

- **AN3–AN7** (integrity/security): every `docs/review/**` PR + push — blocking (the gate).
- **AN1–AN2** (currency): weekly scheduled monitor + on-demand — advisory (upserts one drift issue).
- **AN8** (progress): manual burn-down review (no automated detector; a solo-maintainer nudge).

## Why deterministic, not statistical

The deliverable is finite and prose-shaped: there is no time-series to learn a "normal" band from. Every
anomaly above has a crisp true/false test, so detection is a check that either passes or fails — the same
philosophy as the integrity gate. This avoids false-positive noise that a statistical anomaly detector
would introduce on a low-volume artifact.

## Response

Each anomaly's response is defined by its downstream alarm (`alarms.md`): integrity/security anomalies
block the merge and are fixed before promotion; currency anomalies trigger just-in-time re-verification
and a supersede/update per `rollback-runbook.md` §2.
