# Observability Setup — Clarifying Questions

> Stage 4.4 (Observability Setup), enterprise scope, Operation phase. Lead: aidlc-operations-agent.
> Recommendations-only intent: there is no running service to instrument, so "observability" = the
> **health of the deliverable** (review-verify integrity over time) + the **progress of the backlog**
> (which findings are implemented) + the **currency of findings** (do evidence anchors still resolve as
> `main` moves past the codekb snapshot `d6c8866b`). No cloud telemetry, no CloudWatch/Grafana.
>
> The consumes (`nfr-design/*`, `infrastructure-design/*`) don't exist (those stages were skipped). The
> kdiab platform's own runtime observability (OTEL → Jaeger, per root CLAUDE.md) is mature and out of
> scope. Two targeted decisions:

---

## Q1 — Observability delivery: documented vs installed

`ci-pipeline` (3.7) already installed one workflow (`review-verify.yml`). Should this stage's
observability be documented-only, or also **install** a scheduled monitor?

- A. **Documented-only** — provide the `gh`/git queries, SLO definitions, and alarm conditions the
  maintainer runs/wires manually. Install nothing new. *(recommended — keeps the ongoing footprint to the
  single existing gate; a solo maintainer doesn't need automated paging)*
- B. **Install a scheduled staleness/progress monitor** — add a `cron` GitHub Actions workflow that
  re-runs the currency check (evidence anchors vs live `main`) and/or reports backlog burn-down, opening
  an issue on drift. (More automation; another workflow file + PR.)
- C. Documented-only now, but include a **ready-to-install** monitor workflow in an appendix for later.
- X. Other (please specify)

[Answer]: B — Install a scheduled monitor. Done: added `docs/review/monitor.py` (stdlib currency + burn-down reporter) and `.github/workflows/review-monitor.yml` (weekly + manual; upserts a single idempotent currency-drift issue) to PR #1557. Advisory only — never blocks a PR; the JIT per-band re-verify (Q3=A) stays the authoritative currency check.

---

## Q2 — Progress-tracking surface (the "dashboard")

How should backlog progress be made observable?

- A. **GitHub epic native progress tracker + BACKLOG.md** — the epic's sub-issue checklist gives a
  built-in burn-down; `BACKLOG.md`/`ROADMAP.md` are the static views. No extra setup. *(recommended)*
- B. **Also set up a GitHub Project board** — columns Near / Mid / Long × Open / In-Progress / Done for a
  visual kanban.
- C. Epic tracker only (skip even the static-doc dashboard framing).
- X. Other (please specify)

[Answer]: A — GitHub epic native progress tracker + BACKLOG.md/ROADMAP.md static views. No Project board.

---

## Q3 — Currency re-verification cadence (the key freshness SLO)

Findings cite `path/File.kt#symbol` against `main` @ `d6c8866b`. As `main` advances, some anchors rot
(US-5). How often should currency be re-verified?

- A. **Per-band, before pulling work** — re-verify a finding's anchors right before opening its issue /
  starting it (just-in-time; matches Q3 phased-but-pull from 4.1). *(recommended)*
- B. **Quarterly** — a scheduled full re-verification of all open findings.
- C. **On every N commits / release** to `main`.
- X. Other (please specify)

[Answer]: A — Per-band, just-in-time: re-verify a finding's anchors right before opening its issue / starting it. The installed weekly monitor (Q1=B) is the advisory early-warning; this JIT check is the authoritative currency gate.
