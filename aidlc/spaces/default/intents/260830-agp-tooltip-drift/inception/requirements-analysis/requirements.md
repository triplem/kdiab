# Requirements — Fix AGP Chart Tooltip Drift

- **Intent:** "fix the agp chart tooltip drift"
- **Scope:** `bugfix` (Minimal depth) · Brownfield · single-component frontend defect
- **Component:** `kdiab-ui/src/features/analytics/AgpChart.tsx`
  (reused by the print/PDF page `kdiab-ui/src/features/report/AgpChartPage.tsx`)
- **Upstream artifacts:** `intent-statement`, `scope-document`, `team-practices` are **N/A** —
  bugfix scope skips intent-capture (1.1), scope-definition (1.4) and practices-discovery (2.2).
  Source of truth for this analysis is the freeform intent + the codekb
  (`business-overview.md`, `architecture.md`, `code-structure.md`) + direct source read.

---

## Intent Analysis

The user wants the Ambulatory Glucose Profile (AGP) chart's hover tooltip to report the correct
time-of-day and percentile values for the position under the cursor. Today the tooltip "drifts":
the reported values and the x-axis mapping do not line up with the cursor position (user confirmed
**Q1 = D, all of the above** — the whole x mapping is off, not just the label). The goal is a
**correct, linear time-to-x mapping** across the AGP chart so the tooltip, the median line, the
percentile bands, and the hour tick labels all agree.

### Root-cause diagnosis (evidence-based)

- The AGP payload is **288 five-minute buckets**, `minuteOfDay ∈ {0, 5, 10, …, 1435}`
  (`AgpResponse.bucketData` doc, `kdiab-ui/src/api/generated/analyze/api.ts:58`).
- `AgpChart.tsx` renders `<AreaChart data={chartData}>` with
  `<XAxis dataKey="minuteOfDay" ticks={[0,180,…,1440]} tickFormatter={formatMinuteOfDay}>` and
  **no `type` prop** → Recharts defaults the axis to `type="category"` (`AgpChart.tsx:145-150`).
- On a category axis: points are placed by **array index**, not by numeric minute; the `.filter()`
  that drops null-percentile buckets (`AgpChart.tsx:80-88`) **compresses** the surviving points to
  fill the width; and the `<Tooltip>` resolves the active point by category index. The net effect
  is that a point's horizontal position no longer maps linearly to its time, and the tooltip label
  (`formatMinuteOfDay` of the resolved bucket) is offset from the cursor's true time — the observed
  "drift".

*(The specific implementation — making the x-axis numeric/linear over the minute domain — is a
Construction concern (code-generation 3.5), not fixed here. Requirements below are stated as
observable behaviour.)*

---

## Functional Requirements

- **FR-1 — Tooltip time accuracy.** When the user hovers any x-position on the AGP chart, the
  tooltip's time label MUST correspond to the time-of-day at that x-position (within one 5-minute
  bucket). *Pass/fail:* hovering the bucket whose `minuteOfDay = 540` shows label "09:00".
- **FR-2 — Tooltip value accuracy.** The percentile values shown in the tooltip (median, P10–P90,
  P25–P75) MUST be those of the bucket identified by FR-1 — i.e. the values match the point under
  the cursor.
- **FR-3 — Linear x mapping.** Each bucket MUST be positioned along the x-axis in linear proportion
  to its `minuteOfDay` over the full-day domain (0 → 1440), so the median line, both bands, and the
  hour tick labels (00:00, 03:00, …, 24:00) are all mutually aligned. Missing/null buckets MUST
  leave a proportional gap, not compress the remaining points.
- **FR-4 — Both surfaces.** The fix MUST be correct on **both** the live analytics chart and the
  print/PDF report page (`AgpChartPage.tsx`), which share the `AgpChart` component
  (user confirmed **Q2 = A**).

## Non-Functional Requirements

- **NFR-1 — No visual/UX regression.** Existing behaviour MUST be preserved: the TIR reference
  lines (70/180), the colorblind-safe band patterns (diagonal/dot fills) and legend shape
  descriptors, the mg/dL↔mmol/L unit conversion, the ⓘ help affordance, the data-quality caption,
  and the accessibility roles/aria labels. Minor tick-spacing changes from a correct linear axis are
  acceptable; **correctness wins over pixel-identity** (user confirmed **Q4 = C** in a follow-up
  round after the reviewer flagged it as un-asked). Vertical accuracy is separately pinned by AC-4.
- **NFR-2 — Coverage gate.** Changed lines MUST keep kdiab-ui at/above the 80% coverage floor per
  the project quality gate (Vitest thresholds; recharts-render exclusions per ADR-015 still apply).
- **NFR-3 — Build/lint clean.** `npm run build` (TypeScript strict) + `npm run lint` MUST pass.

## Test Strategy (resolves reviewer BLOCKER #1)

The existing `kdiab-ui/src/__tests__/AgpChart.test.tsx` **mocks Recharts wholesale**
(`Tooltip: () => null`, `XAxis: () => null`, `Area: () => null`), and ADR-015 excludes real
recharts SVG rendering from coverage as "only testable via E2E". Therefore a component test that
"hovers and reads the rendered tooltip" is **not achievable** in the unit suite. The regression
guard is instead:

- **TS-1 (primary, automated).** The fix MUST extract the time-to-x mapping and the tooltip
  label-resolution into **pure, exported helper(s)** in `AgpChart.tsx` (e.g. a function mapping a
  `minuteOfDay` to its linear x-fraction over [0,1440], and one resolving a bucket to its display
  time label). A Vitest unit test asserts these helpers directly — independent of Recharts — so the
  mapping is provable without rendered SVG. This keeps the guard inside the coverage-counted,
  Recharts-mock-free path.
- **TS-2 (secondary, manual).** Manual browser verification of the live chart and the print page
  (per Q3 = A), since the rendered hover behaviour itself is E2E-only per ADR-015.

## Acceptance Criteria (BDD)

- **AC-1 — time→x mapping (automated via TS-1).** *Given* the extracted pure mapping helper, *when*
  it is given `minuteOfDay = 540`, *then* it yields the x-fraction `540/1440 = 0.375` and the
  resolved label "09:00"; and given `minuteOfDay = 0` and `1435` it yields `0.0` and `~0.9965`
  respectively (endpoints map correctly, not index-collapsed).
- **AC-2 — missing buckets keep true position (automated via TS-1 + manual TS-2).** *Given* AGP data
  with null/missing buckets, *when* the mapping is applied, *then* each surviving bucket keeps its
  true `minuteOfDay/1440` position (no compression) and hour ticks stay aligned. **Boundary probe
  (reviewer #4):** a bucket immediately adjacent to a null gap (e.g. `minuteOfDay = 615` next to a
  missing 610) MUST resolve to its own time ("10:00"-class label), not an index-shifted neighbour —
  this is the exact failure the category axis produces.
- **AC-3 — both surfaces (manual TS-2).** *Given* the print/PDF report page (`AgpChartPage.tsx`),
  *when* it renders, *then* the same time-to-x correctness holds as on the live chart.
- **AC-4 — vertical/clinical accuracy unchanged (reviewer #3, safety).** *Given* the same bucket
  data before and after the fix, *then* the **median curve, the P25–P75 and P10–P90 bands, and the
  two TIR `ReferenceLine`s (tirLow=70, tirHigh=180, unit-converted)** render at the **same glucose
  (y) values** — only the horizontal (time) mapping changes. No vertical/value regression on a
  patient-facing glucose chart.

## Constraints

- **C-1** — Frontend-only change confined to `kdiab-ui/src/features/analytics/AgpChart.tsx` (and its
  tests). No backend/API/openapi change — the analyze `AgpResponse` contract is unchanged. Test files
  in play: `kdiab-ui/src/__tests__/AgpChart.test.tsx` (add the TS-1 pure-helper assertions here) and
  `kdiab-ui/src/__tests__/AgpChartPage.test.tsx` (print surface; update if shared behaviour changes —
  reviewer #6).
- **C-2** — Follow project rules: feature branch `fix/<issue>-agp-tooltip-drift`, Conventional
  Commits, reference the GitHub issue (`Closes #N`), quality gates before PR, merge-commit (never
  squash), delete branches after merge.

## Assumptions

- **A-1** — The drift is the category-axis mapping described above, not an upstream data defect in
  kdiab-analyze (the buckets themselves are correct). To be confirmed empirically in code-generation.
- **A-2** — The label formatter `formatMinuteOfDay` truncates to `HH:00` (`${(m/60)|0}:00`), so the
  *rendered label alone* cannot distinguish sub-hour drift (a tooltip could be off by up to 59 min
  and still show the right "HH:00" for many buckets — reviewer #5). This is exactly why the primary
  guard is the **pure mapping unit test (TS-1/AC-1)**, which asserts the numeric x-fraction, not the
  coarse label. Changing the label granularity is out of scope.

## Out of Scope

- Any change to the kdiab-analyze backend, the `AgpResponse` schema, or bucket computation.
- Other analytics charts (BasalAvgChart, BolusAvgChart, dashboard CGM trend) — even if they share a
  similar axis pattern, they are not part of this intent.
- Broader AGP feature work (new percentiles, export formats, etc.).

## Open Questions (for later stages)

- **OQ-1** — Create the GitHub issue now (user confirmed **Q5 = A**); capture its number for the
  branch/commit references before code-generation. Owner: conductor, at build-and-test / pre-PR.
- **OQ-2** — Confirm A-1 empirically: reproduce drift in the browser and confirm the numeric-axis
  fix resolves it (manual verification per AC-1). Owner: code-generation / build-and-test.

---

## Review

**Verdict: NOT-READY**

Reviewed by: Product Lead (quality gate). Grounded in the actual source (`AgpChart.tsx`,
`AgpChart.test.tsx`, `AgpChartPage.tsx`, generated `analyze/api.ts`). This is a tight, well-scoped
single-component bugfix and the diagnosis is genuinely evidence-based — the cited line references
(XAxis `:145-150` with no `type` prop, null-filter `:80-88`, 288-bucket `minuteOfDay` domain from
`api.ts:56-60`) all check out against the live code. One blocker and a few gaps keep it from READY.

1. **[BLOCKER] AC-1's automated test is unachievable under the existing test harness — the primary
   regression guard is untestable as written.** The only existing test file
   (`kdiab-ui/src/__tests__/AgpChart.test.tsx`) **mocks Recharts wholesale**: `Tooltip: () => null`,
   `XAxis: () => null`, `Area: () => null`. Under this mock there is no rendered tooltip, no axis,
   no hover behaviour to assert against — so "a component test asserting the tooltip label matches
   the hovered bucket's time" (Q3 = A, AC-1) **cannot pass** without either (a) removing the Recharts
   mock and rendering real SVG (which ADR-015 explicitly excludes from coverage as "only testable via
   E2E"), or (b) extracting the time-to-x / label logic into a pure function and unit-testing *that*.
   The requirement asserts a testable AC but the codebase makes it false as stated. *Fix:* pick and
   state the test strategy explicitly. Recommended: require the fix to extract the linear
   minute→x-position (and the tooltip-label resolution) into a pure, exported helper and assert the
   mapping in a unit test (e.g. `minuteOfDay 540 → label "09:00"`, and that a null-bucket leaves a
   proportional gap). If instead the guard is to be an E2E/Playwright hover assertion, say so and drop
   the "component test" framing. Without this decision, engineering will hit the mock wall and come
   back to ask — which is the definition of NOT-READY.

2. **[MAJOR] Q4 was never actually asked — a load-bearing requirement rests on an assumed answer.**
   FR/NFR-1 and A-2 build on Q4 = C ("correctness wins over pixel-identity"), but the Q&A file
   states plainly "assumed default; not asked in guided round." The whole visual-regression posture
   (NFR-1) hangs on an answer the user never gave. The stage protocol (Step 9) requires resolving
   ambiguity before generating requirements, not deferring it to "veto at the gate." *Fix:* ask Q4
   for real and record the confirmed answer before this artifact is approved, or the gate approval
   must explicitly double as the Q4 answer — state which.

3. **[MAJOR] Missing clinical-safety edge case: no requirement pins the median-line / band vertical
   accuracy or the TIR reference lines during the axis change.** This is a T1D glucose chart — a
   clinician or patient reads the median and P25–P75 band against the 70/180 TIR lines to judge
   time-in-range. The requirements correctly focus on the *horizontal* (time) axis, but the fix
   changes the chart's axis configuration, and there is no explicit AC that the **y-values, the
   median curve, and the two `ReferenceLine`s (tirLow=70, tirHigh=180, unit-converted) remain
   unchanged** after the fix. A silent vertical or band regression on a glucose chart is a
   patient-facing misread risk. *Fix:* add an AC: "the median value, both percentile bands, and the
   70/180 TIR reference lines render at the same glucose values before and after the fix (only the
   horizontal mapping changes)."

4. **[MINOR] "within one 5-minute bucket" (FR-1) is stated but never given a pass/fail probe at the
   boundary.** FR-1's tolerance is reasonable, but the only worked example is the exact-hit case
   (540 → "09:00"). The drift bug is worst at the *ends* and around *null gaps*. *Fix:* add a probe
   that exercises a non-tick, near-a-gap bucket (e.g. hovering around a null-compressed region shows
   the correct neighbouring time, not an index-shifted one) — this is the exact failure the bug
   produces and AC-2 gestures at it but gives no concrete assertion.

5. **[MINOR] `formatMinuteOfDay` truncates to `HH:00`, so a whole class of "drift" is invisible to
   the current label.** The label formatter is `${(m/60)|0}:00` — every minute in an hour renders
   the same "HH:00" string. A tooltip could be off by up to 59 minutes and the *label* would still
   look right for many buckets, masking regressions and weakening AC-1's discriminating power. Worth
   a one-line note in Assumptions or Open Questions that the label's coarseness limits what the
   tooltip label alone can prove, and that the pure-mapping unit test (finding 1) is the real guard.
   Not a blocker, but the requirements over-trust the label as evidence.

6. **[NIT] Out-of-scope list is good but should name `AgpChartPage.test.tsx`.** A second test file
   (`kdiab-ui/src/__tests__/AgpChartPage.test.tsx`) exercises the print surface (FR-4/AC-3). If the
   fix touches shared behaviour, that suite may need updating too. Confirmed it exists; just flag it
   so code-generation doesn't miss it.

**What's good (brief):** diagnosis is code-grounded not speculative; scope boundaries (C-1, Out of
Scope) are crisp and correctly exclude the backend/`AgpResponse` contract and sibling charts;
both-surfaces requirement (FR-4) is correct since `AgpChartPage` genuinely reuses `AgpChart`;
accessibility/colorblind/unit-conversion preservation (NFR-1) shows customer awareness. The bones are
right — it fails the gate only on the untestable primary AC (1) and the un-asked Q4 (2), both of which
would send engineering back to ask. Resolve 1–3 and this is READY.

---

### Iteration 2

**Verdict: READY**

Re-reviewed the revised `## Test Strategy`, `## Acceptance Criteria`, `## Constraints`, and
`## Assumptions` against ground truth (`AgpChart.tsx`, `AgpChart.test.tsx`). All six prior findings
are resolved; each fix is code-grounded, not just prose.

1. **[BLOCKER → RESOLVED] AC-1 testability.** The new `## Test Strategy` (TS-1) mandates extracting
   the minute→x-fraction mapping and the label-resolution into **pure, exported helpers** and
   unit-testing those directly — which sidesteps the wholesale Recharts mock (`Tooltip/XAxis/Area:
   () => null`, confirmed at `AgpChart.test.tsx:7-17`) instead of fighting it. AC-1 was rewritten
   around the helper and its numeric assertions check out: `540/1440 = 0.375`, `0 → 0.0`, and
   `1435/1440 = 0.99652… ≈ 0.9965`. The primary guard is now genuinely achievable in the
   coverage-counted suite. Blocker cleared.

2. **[MAJOR → RESOLVED] Q4 asked for real.** The Q&A file (`:55-65`) now records Q4 as an actually-
   asked follow-up with the user's confirmed answer "C — correctness wins over pixel-identity."
   NFR-1 cites the confirmation explicitly. The load-bearing requirement no longer rests on an
   assumed answer.

3. **[MAJOR → RESOLVED] Clinical-safety vertical accuracy.** New **AC-4** pins the median curve, both
   percentile bands (P25–P75, P10–P90), and the two TIR `ReferenceLine`s (tirLow=70, tirHigh=180,
   unit-converted) to unchanged glucose (y) values — matching the source at `AgpChart.tsx:77-78`
   and `:173-174`. A silent vertical regression on a patient-facing glucose chart is now explicitly
   guarded.

4. **[MINOR → RESOLVED] Boundary probe.** AC-2 now carries a concrete near-null-gap probe
   (`minuteOfDay = 615` adjacent to a missing 610 must resolve to its own label, not an
   index-shifted neighbour) — the exact category-axis failure mode. `formatMinuteOfDay(615) =
   "10:00"` checks out.

5. **[MINOR → RESOLVED] Label coarseness.** New assumption **A-2** documents that `formatMinuteOfDay`
   truncates to `HH:00` (confirmed at `AgpChart.tsx:98-99`) and correctly redirects the real proof
   to the numeric TS-1/AC-1 unit test rather than the coarse label.

6. **[NIT → RESOLVED] `AgpChartPage.test.tsx` named.** C-1 now names it explicitly for
   code-generation.

No new findings. Scope, constraints, and out-of-scope remain crisp and correctly exclude the
backend/`AgpResponse` contract and sibling charts. Engineering can start without coming back to ask.
