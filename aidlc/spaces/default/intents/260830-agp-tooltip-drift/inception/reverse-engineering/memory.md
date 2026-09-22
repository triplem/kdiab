<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is maintained by the orchestrator during stage execution. Add observations at the gate ritual, not by editing here directly.

## Interpretations
- 2026-08-30T00:00:00Z — "AGP chart tooltip drift" scoped to `kdiab-ui/src/features/analytics/AgpChart.tsx` (the AGP AreaChart); the report-print wrapper `features/report/AgpChartPage.tsx` reuses the same component. The tooltip is configured on a Recharts `<Tooltip>` inside an `<AreaChart>` whose `<XAxis dataKey="minuteOfDay">` declares no `type`, so it defaults to a category axis while carrying numeric `ticks={0,180,…,1440}` — the classic index/position mismatch that produces tooltip "drift".

## Deviations
- 2026-08-30T00:00:00Z — Ran RE as a FRESHNESS REFRESH, not a full 9-module subagent re-scan, following the documented precedent of prior runs on this stage (intents #1617, #1563, both recorded in reverse-engineering-timestamp.md). Justification: (1) codekb last refreshed 2026-08-25 (5 days ago); (2) `git diff --stat 88428807..HEAD` shows the only non-`aidlc/` source delta is the merged kdiab-calc iob-required work — zero change to the monorepo module/service structure; (3) minimal-depth bugfix touching one frontend component. The 8 content artifacts are reused as-is; directly re-verified the bug-relevant source (AgpChart.tsx) and updated the timestamp marker only. A full subagent re-scan would be disproportionate.

## Tradeoffs
- 2026-08-30T00:00:00Z — Chose to re-verify only the AGP chart area rather than re-scan kdiab-ui broadly. The bug is a single-component charting defect; a broad UI re-scan adds latency without changing the codekb baseline that requirements-analysis (2.3) consumes.

## Open questions
- 2026-08-30T00:00:00Z — Confirm during requirements-analysis whether a GitHub issue already exists for this AGP tooltip drift (project rule: create/reference an issue before writing code); none identified yet in the working tree.
