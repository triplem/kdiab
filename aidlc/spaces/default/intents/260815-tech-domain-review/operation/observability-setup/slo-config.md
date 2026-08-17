# SLO Configuration — Review Deliverable

> Stage 4.4. Service-level objectives for the deliverable, quantified with percentages and time windows
> per the operation-phase guardrail. These are objectives for a **document + backlog**, not a service, so
> the SLIs are integrity/currency/progress signals rather than latency/availability.

## SLOs

| ID | Objective | SLI | Target | Window | Alarm (below breach) |
|---|---|---|---|---|---|
| SLO-1 Integrity | The deliverable stays internally consistent on `main` | `review-verify.yml` result on `main` | **100% green** | per-merge, rolling 90 days | A1 (any red) |
| SLO-2 Evidence linkage | Every finding is evidence-linked | `verify.py` `evidence-format` + `schema` | **100%** | per-merge | A1 |
| SLO-3 Currency at act-time | No finding is acted on with a broken/stale anchor | JIT re-verify before opening a finding's issue (Q3=A) | **100% of pulled findings re-verified** | per-pull | A2 (weekly early-warning) |
| SLO-4 Drift detection latency | Currency drift is surfaced quickly | time from a `main` change to the monitor flagging it | **≤ 7 days** | weekly monitor | A2 |
| SLO-5 Near-band throughput | Highest-value/safety items ship first | Near-band findings closed | **≥ 80% of Near closed** | rolling **30 days** from first pull | A3/A4 |

## Notes on each

- **SLO-1/2 are hard (100%)** — for prose, integrity is binary (consistent or not); there is no
  coverage-percentage analogue. The gate enforces it on every change.
- **SLO-3** is a *process* SLO: the authoritative currency guarantee is the human re-verify just before
  acting (Q3=A), not the scheduled monitor. The monitor (SLO-4) is the early-warning that keeps SLO-3
  cheap to meet.
- **SLO-5** quantifies the ROADMAP "Near ≈ 1–2 weeks of bursts" target as ≥80% closed within 30 days of
  starting the band — a soft objective bounded by solo-maintainer capacity (review constraint C-2), not a
  contractual availability figure.

## Error budget

There is no traffic-based error budget. The operative budget is **maintainer attention**: the roadmap
bands (Near→Mid→Long) allocate it value-first. A missed SLO-5 window is a signal to re-prioritize or
accept-and-park (A4), not an incident.

## Health + error metrics (guardrail minimum)

- **Health metric:** SLO-1 integrity (green rate of `review-verify` on `main`).
- **Error-rate metric:** count of currency-drift anchors per weekly monitor run (SLO-4 signal).
