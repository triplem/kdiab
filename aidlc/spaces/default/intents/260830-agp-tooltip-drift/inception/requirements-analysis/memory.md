<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is maintained by the orchestrator during stage execution. Add observations at the gate ritual, not by editing here directly.

## Interpretations
- 2026-08-30T00:00:00Z — bugfix scope skips intent-capture (1.1), scope-definition (1.4) and practices-discovery (2.2), so the four upstream `consumes` artifacts (intent-statement, scope-document, team-practices) do not exist. Worked from the freeform intent "fix the agp chart tooltip drift" + the codekb (business-overview/architecture/code-structure) + direct source read of AgpChart.tsx. This is the standard bugfix-scope path, not a gap.
- 2026-08-30T00:00:00Z — Root-cause diagnosis: AGP data is 288 five-minute buckets (minuteOfDay 0,5,…,1435 per AgpResponse doc). `AgpChart.tsx` `<XAxis dataKey="minuteOfDay">` sets no `type`, so Recharts defaults to `type="category"`. Category axis spaces points by index, not numeric value; null-filtered gaps compress remaining points; tooltip snaps to category index → time label + percentile values drift from the cursor's x-position. Fix hypothesis (deferred to code-generation): numeric/linear x-axis over minuteOfDay domain [0,1440]. Requirements state the observable behaviour, not the implementation.

## Deviations
- 2026-08-30T00:00:00Z — §12a reviewer (aidlc-product-lead-agent) returned NOT-READY on iteration 1 with a code-grounded BLOCKER: the existing `AgpChart.test.tsx` mocks Recharts wholesale, so an AC asserting a *rendered* tooltip is untestable. Revised the artifact rather than defer: added a Test Strategy (TS-1 extract a pure minute→x-fraction + label-resolution helper and unit-test THAT; TS-2 manual browser per ADR-015), rewrote AC-1 around the pure helper, and named the print test suite. Also asked Q4 for real (reviewer flagged it un-asked) — user confirmed "correctness wins".

## Tradeoffs
- 2026-08-30T00:00:00Z — Chose the pure-helper unit-test guard over an E2E/Playwright hover assertion. Rationale: it stays inside the coverage-counted, Recharts-mock-free path (ADR-015 excludes recharts SVG from coverage), gives a fast deterministic regression guard, and the fix already benefits from extracting the mapping logic. E2E hover remains available as manual verification.

## Open questions
- 2026-08-30T00:00:00Z — Confirm at gate whether a GitHub issue should be created before code-generation (project rule: reference an issue in commits). None exists yet.
