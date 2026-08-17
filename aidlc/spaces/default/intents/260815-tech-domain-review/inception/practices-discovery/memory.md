# Practices Discovery — Stage Diary

Observation diary for the practices-discovery stage (Inception 2.2). Four standard
headings per stage-protocol §Learn.

## Interpretations

- 2026-08-16T13:35:00Z — Brownfield project, enterprise scope. Way-of-working / testing / code-style / deployment evidence is conclusive from the just-produced RE codekb + the repo's extensive `.claude/rules/*` set + org/team/project memory. The one genuine gap not visible in code is the walking-skeleton stance.
- 2026-08-16T13:35:00Z — Merge strategy: git history shows EVERY recent merge to main is a merge-commit (`Merge pull request #NNNN from triplem/<type>/<issue>-<desc>`), not squash. This overrides org.md's squash default and matches the user's saved preference (preserve `Closes #N`). Surfaced at the interview for explicit affirmation because it is an org→team override.

## Deviations

- 2026-08-16T13:35:00Z — Step 2 prescribes a 4-agent parallel evidence scan (pipeline-deploy/quality/developer/devsecops). Deviated to INLINE synthesis: the four perspectives' evidence is already fully captured by (a) the 9 RE codekb artifacts produced minutes earlier in this same session and (b) the repo's committed practice docs under `.claude/rules/` (branching-strategy, quality-gates, test-pyramid, kotlin-style, typescript-style, security, commit-conventions, solid-principles, logging, api-design). Re-dispatching 4 subagents to re-read files already in-context is redundant and adds latency. The stage GOAL (discover team practices from evidence) is fully met; evidence.md cites the concrete sources per perspective.

## Tradeoffs

- 2026-08-16T13:35:00Z — Chose inline synthesis over subagent fan-out to avoid redundant re-scanning and the background-completion latency observed in the RE stage. Trade-off: no fresh independent scan, but the RE scan is minutes old (HEAD d6c8866b, same commit) so freshness is intact.

## Open questions
