# Unit-of-Work ↔ Story Map — #1606 (Jackson-free JWT)

Maps the Units of Work to `../user-stories/stories.md` and the requirements
(`../requirements-analysis/requirements.md`); grounded in the design
(`../application-design/components.md`, `../application-design/component-methods.md`,
`../application-design/services.md`, `../application-design/component-dependency.md`,
`../application-design/decisions.md`). One unit (U1) covers all stories.

## Story → Unit → Task → Requirement

| Story | Unit | Task | Requirements |
|---|---|---|---|
| US-1 characterization/parity tests | U1 | T1 | NFR-2, NFR-5; FR-3/4/5/6 (pins current behaviour) |
| US-2 dependency swap | U1 | T2 | FR-1 |
| US-3 Nimbus verifier | U1 | T3 | FR-2, FR-3, FR-4, FR-5, FR-6 |
| US-4 test-minting migration | U1 | T4 | FR-10 |
| US-5 realm/config (conditional) | U1 | T5 | FR-7 |
| US-6 sweep + jackson-pin removal | U1 | T6 | FR-1 (AC-1.2), FR-8, NFR-4 |
| US-7 ADR + docs | U1 | T7 | FR-9 |
| US-8 release gate | U1 | T8 | NFR-1, NFR-6 |

## Coverage assertions

- **Every story maps to U1** (single unit) and to exactly one internal task. No orphan story.
- **Every requirement (FR-1..FR-10, NFR-1..NFR-6) is covered** by at least one task (FR/NFR column
  above; NFR-3 (DRY/single-file) and reversibility are cross-cutting properties of U1 itself).
- **INVEST independence** is intra-task, not inter-unit: the unit is atomic by design (compile-boundary
  coupling), so U1 is the smallest independently-shippable package that keeps `main` green.

## Construction hand-off

- **Bolts:** 1 (U1). No walking skeleton (team practice — incremental change).
- **Sequencing:** risk-first internal DAG T1→T8 (parity tests first; release gate last).
- **Delivery:** one atomic PR, merge-commit, `Closes #1606`, closing epic #1603 on merge.
