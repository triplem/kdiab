# Bolt Plan — Jackson-free JWT Verification (#1606)

Traces to `../units-generation/unit-of-work.md`, `../units-generation/unit-of-work-dependency.md`,
`../units-generation/unit-of-work-story-map.md`, `../requirements-analysis/requirements.md`,
`../user-stories/stories.md`, `../application-design/components.md`,
`../refined-mockups/mockups.md` (skipped — no UI), governed by
`../practices-discovery/team-practices.md`. Delivery-agent lead.

## Bolt Sequence

**One Bolt** — the single Unit of Work U1 maps 1:1 to one Bolt. Construction stages 3.1–3.5 run once
for this Bolt; 3.6 (Build & Test) and 3.7 (CI Pipeline) run once after it.

| Bolt | Unit | Walking skeleton? | Gate |
|---|---|---|---|
| **Bolt 1** | U1 — Jackson-free JWT verification | **No** (team practice: skeleton skipped for incremental work on an established platform) | Standard Bolt gate (there is only one Bolt, so no ladder prompt / autonomy-mode choice is needed) |

## Bolt 1 internal task order (risk-first, from units-generation)

T1 parity tests → T2 dep swap → T3 Nimbus verifier → T4 test-minter migration → T5 realm/config (likely
no-op) → T6 platform-wide sweep + jackson-pin removal → T7 ADR-023 → T8 release gate (security review +
whole-platform CI green). Critical path: **T2 → T3 → T6 → T8**.

## Walking Skeleton Decision

**Skipped.** Per `team-practices.md` (and `aidlc/spaces/default/memory/team.md`): the platform is an
established, running nine-module system; #1606 is incremental work on existing code with no thin
end-to-end slice to bootstrap. This is a `PRACTICES`-governed skip, not a scope-grid default — the team
layer explicitly skips the skeleton ceremony for incremental work. Since there is exactly one Bolt, the
walking-skeleton gate and the post-skeleton ladder prompt do not apply; Bolt 1 runs as a standard gated
Bolt.

## Construction hand-off

- **Worktree base / merge target:** `main` (org practice), on a feature branch `feat/1606-jackson-free-jwt`.
- **One atomic PR**, merge-commit (not squash), `Closes #1606` → closes epic #1603 on merge.
- **Per-unit design stages (3.1–3.4)** are largely pre-answered by Application Design (ADR-023 + the
  component/method specs); Construction should reuse them rather than re-deriving.
