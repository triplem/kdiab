# Deployment Pipeline — Clarifying Questions

> Stage 4.1 (Deployment Pipeline), enterprise scope, Operation phase. Lead: aidlc-pipeline-deploy-agent.
> This is a **recommendations-only / assessment intent**: the "deployable artifact" is the committed
> `docs/review/*.md` set plus its 30-finding backlog and the *deferred* queued GitHub-issue projection
> (1 epic + 29 sub-issues, ADR-RVW-005, `gh`-gated at OQ-1). There is no cloud infrastructure to deploy
> (no AWS per project rules; the platform's own CD is mature and out of scope). So the classic prompts
> (blue/green, canary metrics, CloudWatch Evidently/AppConfig) map to GitHub-native analogues below.
>
> Operation-phase questions are targeted, not a full Q&A — nearly everything is decided upstream. These
> four resolve the only genuinely-open *delivery* parameters. Answer with the letter(s); every question
> ends with `X. Other`.

---

## Q1 — Issue-materialization trigger (the promotion gate for findings)

The queued issue set (1 epic + ~29 sub-issues) is **deferred and `gh`-gated** (ADR-RVW-005, OQ-1). The
downstream Deployment Execution stage (4.3) is where a projection would actually run. How should the CD
pipeline treat that trigger?

- A. **Keep deferred — design only.** The pipeline documents the exact `gh` projection procedure but
  fires nothing; the maintainer runs it manually when ready (honours the park decision). *(recommended —
  matches ADR-RVW-005 and the intent's park semantics)*
- B. Materialize **all** issues now as part of this workflow (un-park OQ-1, create the epic + sub-issues).
- C. Materialize **only the 5 quick-wins + High-severity Near items** now; keep the rest deferred.
- D. Materialize the **epic only** now (as a tracking anchor); sub-issues later.
- X. Other (please specify)

[Answer]: D — Materialize the epic only now as a tracking anchor; sub-issues created on-demand as findings are pulled (dovetails with Q3 phased-but-pull). Actual creation runs at Deployment Execution (4.3) behind a confirmation gate; this stage designs the trigger.

---

## Q2 — Promotion gate for the docs deliverable itself (branch protection)

`review-verify.yml` (10 checks via `verify.py`) already runs on `docs/review/**` PRs, but is **not yet a
required status check**, so a red run doesn't block merge. Should the CD design make it load-bearing?

- A. **Recommend adding branch protection** — require the `Verify review deliverable integrity` check on
  `docs/review/**` before merge, and document the one-time setup as a deployment prerequisite. *(recommended
  — a gate that can't block is advisory only; key principle 4)*
- B. Leave it advisory (run-on-PR, not required) — the solo maintainer self-gates.
- C. Require it **and** also add a CODEOWNERS entry for `docs/review/**`.
- X. Other (please specify)

[Answer]: A — Recommend adding branch protection so `review-verify.yml` is a required check on `docs/review/**`; document the one-time setup as a deployment prerequisite (a gate that can't block is advisory only).

---

## Q3 — Recommendation rollout strategy (how findings get "promoted to prod" = implemented)

Each finding is one independently shippable change (feature branch → CI → merge, per team practices).
The ROADMAP already sequences them Near → Mid → Long. How strict is that ordering as a rollout strategy?

- A. **Phased-but-pull.** Near → Mid → Long is the *default* value ordering, but the maintainer pulls any
  item early when convenient; only the one hard dependency (FIND-CLIN-014 needs FIND-CLIN-001) is
  enforced. *(recommended — matches ROADMAP's "phases order value, don't gate each other except noted")*
- B. **Strict phased.** No Mid item starts until all Near items ship; no Long until all Mid ship.
- C. **Safety-first canary.** Ship the clinical-safety Near items first as a "canary" burst, validate in
  use, then open the rest.
- X. Other (please specify)

[Answer]: A — Phased-but-pull: Near → Mid → Long is the default value ordering; the maintainer pulls any item early when convenient; only the FIND-CLIN-014 → FIND-CLIN-001 hard dependency is enforced.

---

## Q4 — Rollback / supersede posture for a finding that goes wrong or stale

Two rollback cases exist: (a) an *implemented* recommendation regresses in production, and (b) a
*not-yet-implemented* finding goes **stale** as `main` advances past the codekb snapshot (the US-5
currency guard). How should the runbook handle a finding that must be withdrawn?

- A. **Revert + annotate.** For (a) `git revert` the implementation PR (standard); for (b) mark the
  finding `Superseded` in its theme doc with a dated rationale and drop it from the backlog/roadmap,
  re-running `verify.py` so counts stay consistent. Keep the record — never silently delete. *(recommended)*
- B. Revert only; delete withdrawn findings outright (smaller docs, but loses the audit trail).
- C. Park-in-place — leave stale findings flagged `Needs re-verification` but keep them in the backlog
  until re-checked, rather than removing.
- X. Other (please specify)

[Answer]: A — Revert + annotate: (a) `git revert` the implementation PR (standard); (b) mark the stale finding `Superseded` in its theme doc with a dated rationale, drop it from backlog/roadmap, re-run `verify.py` so counts stay consistent. Keep the record — never silently delete.
