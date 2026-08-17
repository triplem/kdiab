# Units Generation — Questions

**Stage:** units-generation (2.7) · **Phase:** Inception · **Depth:** Comprehensive (enterprise)
**Intent:** review technology & domain — recommendations-only (parks at end of Inception)

This stage decomposes the **review deliverable system** (from Application Design) into implementable
**units of work** with a cycle-free dependency DAG and a unit→story map. It describes *what can depend on
what* (topology only) — the build order / critical path is chosen later in Delivery Planning (2.8), so it
is NOT asked here.

The natural unit boundary follows the five theme workstreams + the deliverable-assembly steps from
`components.md`. Each question notes a recommended default; nothing is pre-answered.

---

## Q1 — Unit boundary strategy

How should the review work be sliced into units?

- **A.** **By review theme/workstream** (clinical-safety, data-model, security, tech-debt, modernization)
  **plus** separate deliverable-assembly units (backlog, views, issues). Matches the stories' INVEST
  independence — each theme is independently runnable and cuttable. *(Recommended.)*
- **B.** **By application-design component** (one unit per component: FindingRecord, EvidenceLedger, each
  workstream, backlog, quick-wins, roadmap, issue-sync, index).
- **C.** **Coarse:** one "theme reviews" unit + one "deliverables" unit (2–3 units total).
- **X.** Other (please specify)

[Answer]: A — By review theme/workstream + separate deliverable-assembly units. *(Mode: guided, 2026-08-16)*

---

## Q2 — Clinical-safety granularity (the non-trimmable floor)

Clinical-safety is the Must/non-trimmable floor (FR-1.4), spanning dose-calculation correctness (US-1)
and guardrails + metric definitions (US-2). Keep as one unit or split?

- **A.** **Split into two units:** dose-calculation review (US-1 — the MVR floor that survives any capacity
  cut) + guardrails-&-metrics review (US-2). Isolates the single non-trimmable deliverable as its own unit.
  *(Recommended.)*
- **B.** **Keep as one** clinical-safety unit covering US-1 + US-2.
- **X.** Other (please specify)

[Answer]: A — Split into two units: dose-calculation review (US-1, MVR floor) + guardrails-&-metrics review (US-2). *(Mode: guided, 2026-08-16)*

---

## Q3 — Deliverable-assembly units

How should the backlog / quick-wins / roadmap / GitHub-issue work be unitized?

- **A.** Three units: **backlog-assembly** (US-7, depends on all theme units); **views** (quick-wins US-8
  + roadmap US-9); **issue-materialization** (deferred, depends on backlog). *(Recommended.)*
- **B.** One combined **deliverables** unit (backlog + quick-wins + roadmap + issues).
- **C.** Four separate units (backlog, quick-wins, roadmap, issues) — finest-grained.
- **X.** Other (please specify)

[Answer]: C — Four separate deliverable units: backlog, quick-wins, roadmap, issues. (The ReviewIndex/README, having no dedicated unit here, is folded into the backlog-assembly unit as the master aggregation point — see diary.) *(Mode: guided, 2026-08-16)*

---

## Q4 — Foundational unit (FindingRecord schema + EvidenceLedger)

The FindingRecord schema (C1) and EvidenceLedger/live-verification (C7) are cross-cutting — every theme
workstream depends on them. How should they be unitized?

- **A.** A single **foundational unit** (schema + evidence-link format + live-verification guard) that all
  theme units depend on. Makes the shared contract an explicit dependency root. *(Recommended.)*
- **B.** Folded into each theme unit (no separate foundational unit) — each workstream re-states the schema.
- **X.** Other (please specify)

[Answer]: A — Single foundational unit (FindingRecord schema + EvidenceLedger + live-verification guard) that all theme units depend on. *(Mode: guided, 2026-08-16)*

---

## Q5 — Shipping model per unit

How does each unit's output land in the repo?

- **A.** **Each unit independently shippable** — one feature-branch-per-issue, each review doc/finding
  committable in a single maintainer burst (matches `team-practices.md` + NFR-2). *(Recommended.)*
- **B.** All review docs land in a **single PR** at the end of the review.
- **X.** Other (please specify)

[Answer]: A — Each unit independently shippable (feature-branch-per-issue, each doc/finding committable in one burst). *(Mode: guided, 2026-08-16)*
