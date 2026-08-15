# Settings Recommendations

Based on reflection analysis of intent: cleanup-claude-tooling

## CLAUDE.md Updates
- No further changes needed. This intent already rewrote the Rules/Skills indexes and added a note
  that SDLC/review workflows come from the ai-dlc plugin (PR #1538).

## .ai-dlc/settings.yml Changes
Change the default iteration passes. `[product, design, dev]` is too heavy for the common case —
most backend/config work has no product or design dimension, and it forced a per-intent override on
this chore.

```yaml
# before
default_passes: [product, design, dev]
# after
default_passes: []   # single dev pass by default; opt into product/design per-intent for UI work
```

No `quality_gates` change recommended — none were configured for this intent and none were needed
(the success criteria were mechanical and self-checked).

## Hat Instruction Updates
- None. The default hats were never exercised (manual execution), so there's no evidence to act on.

## Workflow Adjustments
- For **chore/config/docs** intents, prefer **`/ai-dlc:quick`** over `/ai-dlc:elaborate` →
  `/ai-dlc:execute`. The full ritual (knowledge synthesis, discovery subagent, wireframes,
  adversarial review, ticket-sync) is disproportionate at that scale.

## Hooks (already applied — recorded for completeness)
- `commit-guard.sh`: worktree-aware branch resolution + `ai-dlc/*` Conventional-Commits exemption +
  broadened `git commit` match (PR #1540). This is what unblocks `/ai-dlc:execute` on this repo.
- `session-start.sh`: stack detection reduced to Kotlin/JVM + React/Node (PR #1540).

## Elaboration Template Improvements
- The elaborate flow commits a `.gitignore` change on `main` in Phase 2.25, which `commit-guard`
  blocks. Either skip that commit or let the entry ride on the intent branch. Minor, but it's a
  guaranteed snag on any repo that protects `main` via a commit hook.
- When an intent has no product/design surface, elaboration should default to a single dev pass
  rather than inheriting the project-wide `default_passes`.
