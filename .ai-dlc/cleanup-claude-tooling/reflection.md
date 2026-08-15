---
intent: cleanup-claude-tooling
version: 1
created: 2026-08-15
status: completed
---

# Reflection: Prune obsolete `.claude/` tooling superseded by ai-dlc

## Execution Summary
- Units completed: 1/1 (delivered, but executed **manually**, not via `/ai-dlc:execute`)
- Total iterations: n/a — no build loop ran (no `iteration.json`/state files)
- Workflow: default (planner → builder → reviewer) — not actually exercised; work was done directly
- Sessions used: 1 (this conversation)
- Delivery: PR #1538 (merged); follow-up hooks hardening in PR #1540 (merged)
- Blockers encountered: 1 significant — the ai-dlc worktree/commit path was blocked by the project's `commit-guard` hook

## What Worked
- **Inline discovery instead of the discovery subagent.** The `.claude/` layout and stack were
  fully knowable with a few `ls`/`grep` calls, so a heavyweight discovery pass was unnecessary.
  The unit spec came out precise (explicit remove/keep lists) on the first try.
- **Grounding removals with verification queries first** (stack checks for Angular/Java/Spring/.NET,
  grep for references in hooks/settings/scripts, dangling-index detection) meant zero broken links
  and no surprises — the deletions were provably safe before they happened.
- **Verifiable, mechanical success criteria** (exact counts, `bash -n`, grep-clean, `git diff --stat`
  scope) made "done" objective and self-checkable — every criterion was confirmed before merge.
- **Respecting the project's delivery convention.** When the ai-dlc path failed, falling back to a
  normal `chore/<issue>` branch + Conventional Commit + PR kept `main` protected and the history clean.

## What Didn't Work
- **ai-dlc worktree/`execute` is incompatible with the project's `commit-guard`.** Committing
  `elaborate(...)` on an `ai-dlc/<slug>/main` worktree was denied because the guard read the *root*
  checkout's branch (`main`) and rejected the non-conventional commit type. Cost: the intent worktree
  had to be abandoned and the work re-delivered via a normal branch. (Now fixed in #1540 — the guard
  is worktree-aware and exempts `ai-dlc/*` branches.)
- **`default_passes: [product, design, dev]`** (chosen at setup) is wrong for most kdiab work and
  especially for chore/config intents — a config cleanup has no product or design dimension. I had to
  override to a single dev pass. The setup default is too heavy for the common case.
- **The full elaboration ritual is disproportionate for small chore intents.** Knowledge-synthesis
  over the 9-service monorepo, a discovery subagent, wireframes, adversarial review, and ticket-sync
  are all designed for feature work; for a ~30-file deletion they add ceremony without value. I
  streamlined them away manually rather than the tooling recognizing the scope.
- **Stale unit status.** Because execution bypassed the loop, `unit-01` still reads `status: pending`
  on `main` even though the work is merged — the on-disk spec no longer reflects reality.

## Session Insights
- The `elaborate` skill's Phase 2.25 commits a `.gitignore` change **on `main`** before creating the
  worktree — this is blocked by `commit-guard`'s main protection regardless of the worktree fix. Minor
  (the entry can ride on the intent branch), but it's residual friction in the elaborate flow.
- Two distinct process-fit signals in one cycle (commit-guard conflict + wrong default passes) both
  trace back to **`/ai-dlc:setup` defaults not being reconciled with the project's existing hooks and
  actual scope patterns**.

## Operational Outcomes
- n/a — no deployment surface; pure repo/config maintenance.

## Compound Learnings
- No `docs/solutions/` learnings were produced (manual, single-session execution).
- Meta-learning captured directly to project memory instead:
  `project-aidlc-commitguard-conflict` (now marked RESOLVED).

## Key Learnings
- For small chore/config intents, prefer `/ai-dlc:quick` (or direct execution) over the full
  elaborate → execute ritual; the ceremony/value ratio is poor at that scale.
- Reconcile `/ai-dlc:setup` output with pre-existing project hooks up front — adopting ai-dlc on a
  repo with strong custom hooks needs the hooks to be made ai-dlc-aware (done for commit-guard).
- Keep success criteria mechanical and greppable; it made this cycle self-verifying.

## Recommendations
- [x] Make `commit-guard` worktree-aware + exempt `ai-dlc/*` branches (done — #1540).
- [ ] Change `default_passes` in `.ai-dlc/settings.yml` to `[]` (single dev pass); opt into
      product/design passes per-intent when UI/product work is actually involved.
- [ ] Use `/ai-dlc:quick` for chore/config cleanups instead of full elaboration.
- [ ] (Optional) Teach the elaborate flow to skip the Phase 2.25 `.gitignore`-on-main commit, or make
      it tolerate the main-protection hook.

## Next Iteration Seed
No v2 needed — the cleanup is complete and its main follow-on (hook reconciliation) already shipped.
The reusable value is process-level: adjust setup defaults and prefer the quick path for chores.
