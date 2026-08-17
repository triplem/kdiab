# Delivery Planning — Questions

**Stage:** delivery-planning (2.8) · **Phase:** Inception (capstone) · **Depth:** Comprehensive (enterprise)
**Intent:** review technology & domain — recommendations-only (parks at end of Inception)

This stage chooses the **economic Bolt sequence** — the order a solo maintainer executes the 11 units
(from `unit-of-work.md`) to produce the review, respecting the dependency DAG in
`unit-of-work-dependency.md`. Bolt sequencing is an economic value judgment, not derivable from the DAG.

Constraints already fixed / derived (NOT asked):
- **Solo maintainer** (persona Sam) — team allocation is one person; concurrency is strictly sequential
  (one Bolt per burst) regardless of the DAG's parallel opportunities.
- **Skip the walking-skeleton Bolt** — established nine-service system, incremental work (`team-practices.md`).
- **Value-density priority, clinical-safety strictly first** (NFR-3); each unit independently shippable in a
  burst (NFR-2).
- **Construction execution is deferred** — the plan is followed if/when the maintainer un-parks (RA-Q3=A, OQ-1).

Each question notes a recommended default; nothing is pre-answered.

---

## Q1 — Sequencing heuristic

How should the Bolts be ordered?

- **A.** **Value-first with the clinical-safety floor first:** foundational → dose-calc (floor) →
  guardrails/metrics → data-model → (backlog + quick-wins over the Must themes) → security → tech-debt →
  roadmap → modernization → issues. Delivers safety value earliest and extends deliverables as themes land.
  *(Recommended — mirrors NFR-3 value-density and the burst model.)*
- **B.** **Strict topological order:** all six theme units, then backlog, then quick-wins, then roadmap,
  then issues (deliverables wait for every theme).
- **C.** **Risk-first:** highest-uncertainty / safety-critical items first (clinical + security), then the rest.
- **D.** **WSJF-scored:** compute (value + time-criticality + risk-reduction) ÷ job-size per unit and order by score.
- **X.** Other (please specify)

[Answer]: A — Value-first with the clinical-safety floor first. *(Mode: guided, 2026-08-16)*
---

## Q2 — Bolt granularity

How much work does one Bolt wrap?

- **A.** **One unit per Bolt** — each Bolt is one review work package, independently shippable in a single
  maintainer burst (~11 Bolts). *(Recommended — matches NFR-2 and the burst model.)*
- **B.** **Bundle related units** — e.g. a clinical Bolt (U1+U2), a deliverables Bolt (U7+U8+U9) (~5–6 Bolts).
- **C.** **Thin slices spanning units.**
- **X.** Other (please specify)

[Answer]: A — One unit per Bolt (~11 Bolts), each independently shippable in a burst. *(Mode: guided, 2026-08-16)*
---

## Q3 — Backlog assembly strategy (the DAG tension)

The prioritized backlog (U7) and quick-wins (U8) depend on the theme findings. Must the maintainer finish
*all six* themes before assembling the backlog, or build it incrementally?

- **A.** **Incremental / living backlog:** assemble a Must-themes backlog early (after the clinical +
  data-model themes), then extend it as security / tech-debt / modernization land. Deviates from strict
  topological order — captured in `risk-and-sequencing-rationale.md`. *(Recommended — value-first, matches bursts.)*
- **B.** **Strict:** assemble the backlog once, after all six theme units complete.
- **X.** Other (please specify)

[Answer]: A — Incremental / living backlog: initial Must-themes backlog, extended as later themes land (deviation from strict topological order, justified in the rationale). *(Mode: guided, 2026-08-16)*
---

## Q4 — Deferred issue-materialization (U10) and the `gh` external dependency

How should the GitHub-issue Bolt (U10) be placed and gated?

- **A.** **Sequenced last, deferred + gated:** docs-first; U10 runs only after the end-of-Inception continue
  decision (OQ-1) and only if `gh` is available/authorized, else the queued fallback list (A-2).
  *(Recommended.)*
- **B.** **Inline per theme:** open issues as each theme's findings land.
- **X.** Other (please specify)

[Answer]: A — Issue-materialization (U10) sequenced last, deferred + gh-gated (docs-first; runs only after the OQ-1 continue decision and if gh available, else queued fallback). *(Mode: guided, 2026-08-16)*