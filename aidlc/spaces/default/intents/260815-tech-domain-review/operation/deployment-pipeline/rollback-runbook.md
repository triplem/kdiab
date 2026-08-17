# Rollback Runbook — Review Deliverable & Recommendations

> Stage 4.1 (Deployment Pipeline), enterprise scope. Companion to `cd-config.md` / `deployment-strategy.md`.
> "Rollback is not optional" (pipeline-deploy key principle 2) — even a docs deliverable needs a tested
> reverse path. Every rollback here is **git-native and fully reversible**; there is no database state,
> traffic, or running service to unwind. Posture: **revert + annotate, never silent-delete** (Q4 = A).

## Rollback triggers

| # | Trigger | Which rollback |
|---|---|---|
| T1 | An **implemented** recommendation regresses on `main` (bug, failed smoke, error-rate/latency breach) | §1 Implemented-fix rollback |
| T2 | A **not-yet-implemented** finding goes **stale** — `main` advanced past the codekb snapshot (`d6c8866b`) and its evidence anchor no longer resolves (US-5 currency guard) | §2 Supersede-a-finding |
| T3 | A finding is judged **wrong** on review (false positive, or the concern is already mitigated) | §2 Supersede-a-finding |
| T4 | The **deliverable PR** itself lands broken content on `main` (bad merge, `verify.py` should have failed) | §3 Deliverable rollback |
| T5 | The **epic** was materialized in error, or the maintainer wants to re-park materialization | §4 Materialization rollback |

## 1. Implemented-fix rollback (T1)

Standard platform procedure — this runbook defers to the kdiab CD path and adds only the deliverable-side
bookkeeping:

1. `git revert <merge-sha>` on a `fix/<issue>-revert-<slug>` branch → PR → green CI → merge. (Forward-only,
   backward-compatible: no destructive schema change ships in a single finding, per the review's own
   guidance, so revert is safe.)
2. **Reopen** the finding's GitHub issue (do not close-as-done); add a comment with the regression detail.
3. In the theme doc, leave the finding as-is (it is still a valid recommendation) but append a dated note:
   `> Reverted <date>: first implementation regressed (<link>); re-approach needed.`
4. Post-rollback analysis: root-cause in the reopened issue before re-attempting (prevent recurrence).

## 2. Supersede a finding (T2 stale / T3 wrong) — the docs-native rollback

This is the case unique to a review deliverable. **Never silently delete** (Q4 = A) — supersede with a
record:

1. In the finding's theme doc (`clinical-safety.md`, `security.md`, …) set the finding's status to
   **`Superseded`** and add a dated rationale line:
   `Superseded YYYY-MM-DD — <reason: stale anchor / already mitigated by <link> / false positive>.`
2. **Remove** the finding's rows from `BACKLOG.md` and `ROADMAP.md` (it is no longer actionable), and if
   it was a quick-win, from `QUICK-WINS.md`.
3. **Update the headline counts** in `README.md` and the theme-doc headers so totals stay truthful
   (e.g. "30 actionable findings" → "29"; adjust the severity tally if the removed finding was High).
4. If a sub-issue was already opened for it, **close it** with a comment linking the supersede rationale
   (and remove it from the epic body's list).
5. **Re-run `python3 docs/review/verify.py`** and confirm exit 0. This is the integrity check that makes
   the supersede safe — it re-validates `backlog-traceability` (G6), `phase-authority` (G7), and
   `readme-numbers` (G9) so the withdrawn finding cannot leave the set inconsistent. **`verify.py` green
   is the smoke test for a docs rollback.**
6. Commit on a `docs/<issue>-supersede-<id>` branch → PR (the required gate re-runs) → merge.

> The `Superseded` marker is the audit trail: the finding stays discoverable in git history and in the
> theme doc, so a future reviewer sees *why* it was withdrawn rather than finding a silent gap.

## 3. Deliverable rollback (T4)

1. `git revert <deliverable-merge-sha>` → PR → merge. Markdown is idempotent; the revert restores the
   prior `docs/review/` tree exactly.
2. Confirm `verify.py` is green on the reverted state (it should be — you are returning to a
   previously-green commit).
3. Root-cause **why the gate let it through**: if `verify.py` should have caught the defect, add/adjust a
   check (a §13 learnings candidate — a new verifier check becomes a sensor). If Q2=A branch protection
   was not yet enabled, that is the fix — enable it.

## 4. Materialization rollback (T5)

1. **Sub-issues:** `gh issue close <n> --comment "re-parking materialization"` for any opened; unlink is
   automatic on close.
2. **Epic:** `gh issue close <epic-n> --comment "materialization re-parked (ADR-RVW-005)"`. The epic and
   sub-issues remain in GitHub history (closed, not deleted) — reversible and auditable.
3. Restore the deferred posture in the record: note the re-park in this intent's state / audit; the
   `BACKLOG.md` "Queued GitHub issues (deferred)" section is unchanged and remains the source of truth.
4. No data loss: nothing about the deliverable depends on the issues existing; the docs are the primary
   artifact, the tracker is a projection.

## Manual recovery beyond automated revert

- **Conflicting concurrent edits** to `docs/review/**` during a rollback: resolve on the revert branch;
  `verify.py` is the arbiter of a consistent final state.
- **A supersede that later proves wrong** (the finding was valid after all): re-instate by reverting the
  supersede PR and re-running `verify.py` — symmetric to §2.

## Rollback verification (the "smoke test")

For every case above, the deployment is not rolled back until:

- [ ] `python3 docs/review/verify.py` exits 0 (deliverable internally consistent), **and**
- [ ] `README.md` headline counts match the actual finding set, **and**
- [ ] for T5, `gh issue list --label epic --search "review"` shows the epic/sub-issues in the intended
      (closed or open) state.
