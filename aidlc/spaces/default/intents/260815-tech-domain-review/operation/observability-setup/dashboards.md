# Dashboards — Review Deliverable Observability

> Stage 4.4 (Observability Setup), enterprise scope. Lead: aidlc-operations-agent. Recommendations-only
> intent: the observable system is the deliverable's **health** + the backlog's **progress** + finding
> **currency**, not a running service. The consumes (`nfr-design/performance-design.md`,
> `security-design.md`, `reliability-design.md`, `infrastructure-design/monitoring-design.md`,
> `infrastructure-services.md`) do **not exist** — those stages were skipped — so there are no runtime
> SLIs to chart. The kdiab platform's own runtime dashboards (OTEL → Jaeger) are mature and out of scope.

## The three dashboards

### D1 — Backlog burn-down (progress) · Q2=A

- **Surface:** the GitHub **epic's native sub-issue progress tracker** (the built-in checklist bar) once
  the epic is materialized, plus `docs/review/BACKLOG.md` and `ROADMAP.md` as the static ordered views.
- **What it shows:** how many of the 30 actionable findings are Open / In-Progress / Done, grouped by
  the Near / Mid / Long band.
- **No GitHub Project board** (Q2=A chose the native tracker + docs — a solo maintainer doesn't need a
  kanban).

### D2 — Deliverable integrity history (health)

- **Surface:** the **Actions run history** of `review-verify.yml` (the 10-check gate).
- **What it shows:** green/red of the integrity gate over time. A red run = the deliverable became
  internally inconsistent (a dropped finding, broken link, count mismatch).
- **Query:** `gh run list --workflow review-verify.yml -R triplem/kdiab` (see `log-queries.md`).

### D3 — Currency report (freshness) · installed, Q1=B

- **Surface:** the weekly **`review-monitor.yml`** job's **step-summary** (rendered in the Actions run
  page), produced by `docs/review/monitor.py`.
- **What it shows:** cited full-path evidence anchors that are `fresh` / `changed` / `missing` vs the
  baseline commit `d6c8866b`, the backlog headline, and the open/closed review-issue burn-down.
- **Coverage note:** the monitor auto-verifies **full-path** anchors only; the deliverable's `.../`
  abbreviated citations are re-checked just-in-time per band (Q3=A) — the authoritative currency check.

## Text layout (D1 burn-down, conceptual)

```
Near  [##########----------]  n done / N   (safety-first)
Mid   [###-----------------]  n done / N
Long  [--------------------]  n done / N
```
<!-- Text fallback: three horizontal progress bars, one per roadmap band (Near/Mid/Long), each showing
findings done out of total. Populated from the epic's sub-issue states once materialized. -->

## Refresh cadence

| Dashboard | Refresh |
|---|---|
| D1 burn-down | live (GitHub epic tracker); static docs updated per merged finding |
| D2 integrity | on every `docs/review/**` PR + push |
| D3 currency | weekly (Mon 06:00 UTC) + on-demand via `workflow_dispatch` |
