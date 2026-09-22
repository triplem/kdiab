# Code Generation Plan — Fix AGP Chart Tooltip Drift

**Intent:** fix the agp chart tooltip drift · **Scope:** bugfix (Minimal) · **Issue:** triplem/kdiab#1646
**Branch:** `fix/1646-agp-tooltip-drift`
**Traceability:** every step maps to a requirement in
`inception/requirements-analysis/requirements.md`.

## Root fix (one line, high leverage)

The `<XAxis dataKey="minuteOfDay">` in `AgpChart.tsx` has no `type`, so Recharts defaults to a
**category** axis (positions by index). Make it a **linear numeric** axis over the full-day minute
domain so points, hour ticks, and the tooltip share one linear scale.

## Steps

- [ ] **Step 1 — Extract pure, exported helpers (TS-1 / AC-1).** In
  `kdiab-ui/src/features/analytics/AgpChart.tsx`:
  - `export const MINUTES_PER_DAY = 1440`
  - `export function minuteToXFraction(minuteOfDay: number): number` → `minuteOfDay / MINUTES_PER_DAY`
    (the linear position invariant that the category axis violated).
  - Promote the existing `formatMinuteOfDay` to a module-level **exported** pure function (label
    resolution helper). *(Traces: FR-1, FR-3, AC-1.)*
- [ ] **Step 2 — Convert the XAxis to a linear time axis (FR-1, FR-3, NFR-1).** Add
  `type="number"`, `domain={[0, MINUTES_PER_DAY]}`, `scale="linear"`, `allowDecimals={false}` to the
  `<XAxis>`; keep `dataKey="minuteOfDay"`, `ticks={xTicks}` (now rendered at true linear positions),
  and `tickFormatter={formatMinuteOfDay}`. No other axis/legend/pattern changes → preserves the
  colorblind band patterns, TIR lines, unit conversion, a11y (NFR-1). *(Traces: FR-1, FR-2, FR-3, FR-4.)*
- [ ] **Step 3 — Confirm vertical accuracy is untouched (AC-4, clinical safety).** Verify the three
  `<Area>` series (`p10_p90`, `p25_p75`, `median`) and the two `<ReferenceLine>`s (tirLow=70,
  tirHigh=180, unit-converted) are unchanged — only the x-axis config changes. No code change beyond
  Step 2 should touch y. *(Traces: AC-4, NFR-1.)*
- [ ] **Step 4 — Unit tests for the pure helpers (TS-1, AC-1, AC-2).** In
  `kdiab-ui/src/__tests__/AgpChart.test.tsx` (which mocks Recharts — the helpers are testable without
  SVG), add assertions:
  - `minuteToXFraction(540) === 0.375`; `minuteToXFraction(0) === 0`; `minuteToXFraction(1435)` ≈
    `0.99652…` (endpoints map correctly — not index-collapsed). *(AC-1)*
  - Monotonic / proportional: `minuteToXFraction` strictly increases with minute; a bucket adjacent
    to a gap keeps its own fraction (`minuteToXFraction(615) === 615/1440`), independent of how many
    buckets were filtered — the "no compression" invariant. *(AC-2 boundary probe)*
  - `formatMinuteOfDay(540) === "09:00"`, `formatMinuteOfDay(615) === "10:00"`,
    `formatMinuteOfDay(0) === "00:00"`. *(AC-1)*
- [ ] **Step 5 — Quality gates (NFR-2, NFR-3).** Run in `kdiab-ui/`: `npm run lint`,
  `npm run build` (tsc strict + vite), `npm run test` (Vitest incl. coverage). All must pass; changed
  lines stay ≥ 80% coverage (the new helpers are pure and fully covered by Step 4). *(Traces: NFR-2, NFR-3.)*
- [ ] **Step 6 — Manual verification note (TS-2, AC-3).** Record that the live chart + print page
  (`AgpChartPage.tsx`, shared component) should be visually confirmed in-browser; captured as a
  build-and-test / pre-merge checklist item since recharts render is E2E/manual per ADR-015.

## Out of scope (from requirements)

- No backend / `AgpResponse` / openapi change. No change to sibling charts. No label-granularity change.

## Test strategy

Minimal (bugfix): requirement-driven unit tests on the extracted pure helpers (the achievable guard
given the wholesale Recharts mock + ADR-015). Rendered hover behaviour is manual/E2E.
