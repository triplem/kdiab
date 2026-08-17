<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is maintained by the orchestrator during stage execution. Add observations at the gate ritual, not by editing here directly.

## Interpretations
- 2026-08-16T19:55:00Z — Recommendations-only intent: no running service, so "observability" = the health + progress lifecycle of the deliverable. Observable system = (a) deliverable integrity (review-verify.yml run history), (b) backlog burn-down (epic native sub-issue progress), (c) finding currency (evidence anchors vs live main). Mapped the 6 required artifacts to these honestly; no fabricated CloudWatch/Grafana dashboards.
- 2026-08-16T19:55:00Z — All five consumes (nfr-design/*, infrastructure-design/*) do not exist (those stages skipped). Sourced from the deliverable + cd-config/deployment artifacts + the platform's known OTEL stack (documented in root CLAUDE.md, out of scope to re-observe here).

## Deviations
- 2026-08-16T20:00:00Z — Q1=B: installed a scheduled monitor (monitor.py + review-monitor.yml) on PR #1557 — a real outward addition, not documented-only. monitor.py needed a false-positive fix: bare filenames (NightscoutV3Routes.kt) and example citations (path/File.kt in CONVENTIONS.md) matched the citation regex; tightened to require the first path segment to be a real top-level repo dir. Result: 3 resolvable full-path anchors, all fresh.

## Tradeoffs
- 2026-08-16T20:00:00Z — Q1=B (install monitor) vs Q3=A (JIT currency) look tense but are complementary: the weekly monitor is an ADVISORY drift early-warning + burn-down (SLO-4, ≤7-day detection); the JIT per-band re-verify is the AUTHORITATIVE currency gate (SLO-3). Framed that way in slo-config/alarms; no contradiction.
- 2026-08-16T20:00:00Z — Anomaly detection is deterministic checks (verify.py/monitor.py), not statistical baselines — a finite prose deliverable has no time-series to learn a normal band from, and deterministic checks avoid false-positive noise on a low-volume artifact.

## Open questions
