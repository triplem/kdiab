# Risk & Sequencing Rationale — Technology & Domain Review

**Stage:** delivery-planning (2.8) · Companion to `bolt-plan.md`.
**Upstream inputs:** `requirements.md` (NFR-3 value-density, FR-1.4 floor), `stories.md` (MoSCoW priorities),
`unit-of-work.md`, `unit-of-work-dependency.md`, `unit-of-work-story-map.md`, Application Design
`components.md`, `team-practices.md`, and `refined-mockups/mockups.md` (no sequencing impact for this docs-only review).

> This artifact explains **why** the Bolt order is what it is, and justifies the one deviation from the
> topological order in `unit-of-work-dependency.md`. The heuristic is **value-first with a hard
> clinical-safety floor** — a WSJF-flavoured argument (Reinertsen CD3 / SAFe WSJF) where clinical-safety
> value + risk-reduction dominates job size.

## Sequencing Heuristic

**Value-first, clinical-safety floor first (Q1=A).** Informally, each unit's WSJF ≈
(user/patient value + time-criticality + risk-reduction) ÷ job size. Clinical-safety units score highest on
every numerator term (patient safety is non-negotiable, NFR-3) and the dose review (U1) is the declared
non-trimmable floor (FR-1.4), so it leads. The ordering then follows the MoSCoW priorities from `stories.md`:
Must (clinical + core deliverables) → Should (security, tech-debt, roadmap) → Could (modernization) →
deferred (issues).

WSJF-style ranking of the theme units (qualitative, not a false-precision score):

| Unit | Value | Risk-reduction | Job size | Rank rationale |
|------|-------|----------------|----------|----------------|
| U1 dose-calc | highest (patient safety) | highest | L | floor — leads regardless of size (FR-1.4) |
| U2 guardrails/metrics | high (safety) | high | M | Must, follows the floor |
| U3 data-model | high (safety-relevant gaps) | medium | M | Must |
| U4 security | medium-high | high | M | Should — high risk-reduction but not immediate patient safety |
| U5 tech-debt | medium | medium | M | Should |
| U6 modernization | medium-low | low-medium | M | Could — lowest value-density, runs late |

## Risk Items Tackled Earliest

1. **Dose-calculation correctness (U1/B2)** — the highest patient-safety risk; any error here is directly
   harmful. Sequenced immediately after the enabler.
2. **Treatment guardrails + metric definitions (U2/B3)** — misleading stored events or displayed metrics are
   the next safety risk.
3. **Data-model gaps (U3/B4)** — schema gaps that silently drop clinically-relevant data.

Security (U4) carries high risk-reduction value but is sequenced in the Should band because its findings are
*flagged* obligations (FR-2.2), not immediate patient-safety defects; it still precedes the Could-priority
modernization work.

## Deviation From Topological Order (the one that needs justifying)

`unit-of-work-dependency.md` places `backlog-assembly` (U7) and `quick-wins` (U8) downstream of **all six**
theme units. The Bolt plan places them (B5/B6) **after only the three Must themes (B2–B4)**, ahead of the
Should/Could theme Bolts (B7–B9).

- **Why deviate:** value-first delivery. Waiting for the Could-priority modernization review to produce
  *any* actionable backlog would delay the maintainer's highest-value output for no safety reason. An
  actionable Must-theme backlog + quick-wins early is worth more than a strictly-topological single backlog
  late (NFR-3 value-density, NFR-2 burst incrementalism).
- **How coverage is preserved:** the backlog and quick-wins are **living documents** (Q3=A). B7–B9 each
  append their theme's findings to them as part of their Definition of Done, so the final backlog is
  complete. The roadmap (B10/U9) still runs **after every theme**, so it sequences the *complete* backlog —
  no roadmap band is computed on a partial backlog.
- **Net effect on the DAG:** the data dependency (a finding must exist before it can be in the backlog) is
  never violated — each finding is appended only after its theme Bolt ships. What changes is *when the
  backlog document is first materialized*, not the finding→backlog edge. This is an economic
  (document-materialization) choice, not a topological violation.

All other Bolt orderings follow the DAG exactly: U0 before every theme; each theme before its findings enter
the backlog; U9 (roadmap) and U10 (issues) after U7.

## Deferred-Execution Risk (Construction parked)

The entire Bolt plan is **deferred** — the intent parks at end of Inception (RA-Q3=A). The risk this creates
is staleness: by the time the maintainer un-parks, the codebase may have moved. Mitigation is built into
U0/U5: the `EvidenceLedger` live-verification guard re-checks every codekb-tracked anchor against the live
repo at execution time, so a resolved item is never reported as open (the `project.md` learned rule). The
`external-dependency-map.md` records the only external gate (`gh`) so the deferral has no hidden blockers.
