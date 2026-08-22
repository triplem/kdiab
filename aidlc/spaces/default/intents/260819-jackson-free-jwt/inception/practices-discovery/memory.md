<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is maintained by the orchestrator during stage execution. Add observations at the gate ritual, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-08-19T17:00:00Z — Ran as a LIGHTWEIGHT RE-AFFIRMATION, not a fresh discovery. SKIPPED Step 2's four-agent parallel scan (practices affirmed 2026-08-16; codebase unchanged for the 5 practice areas) and SKIPPED Step 6 promote. Two reasons for no-promote: (1) nothing new to affirm — all governing rules already in project.md; (2) `practices-promote` targets `.claude/rules/aidlc-team.md`, which does NOT exist in this project (practices live in `aidlc/spaces/default/memory/{team,project}.md`, imported via `.claude/rules/aidlc.md`). Re-appending already-affirmed rules would DUPLICATE them in project.md. So the affirmation gate here re-confirms applicability WITHOUT mutating team.md/project.md.

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-08-19T17:00:30Z — For a future FRESH practices-discovery run in this project, confirm whether `practices-promote` resolves the space memory path (`aidlc/spaces/default/memory/{team,project}.md`) or literally writes `.claude/rules/aidlc-team.md`. If the latter, the promote step needs a project-specific adaptation. Not blocking #1606 (no promote needed this run).

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
