# Deployment Execution — Clarifying Questions

> Stage 4.3 (Deployment Execution), enterprise scope, Operation phase. Lead: aidlc-pipeline-deploy-agent.
> Support: aidlc-developer-agent. This is where the review deliverable is actually "deployed".
>
> **Live state discovered at execution (read-only):**
> - `docs/review/**` is **NOT** on `triplem/kdiab` (404); `review-verify.yml` is **NOT** on the default
>   branch either. All the review work is uncommitted in this backup clone, whose only git remote is a
>   **local** path (`claude` → `/home/triplem/projects/kdiab`) — there is no GitHub `origin` here.
> - **No review epic exists** yet (safe to create — no duplicate).
> - `verify.py` passes 10/10 locally; the 10 review labels exist (created at 4.2).
>
> This yields a **publish-before-materialize** dependency: an epic created now would link to theme docs
> that aren't on the repo (→ 404 links). The two decisions below resolve how to execute.

---

## Q1 — Epic materialization (the outward-facing action Q1=D from 4.1 authorized)

Creating a GitHub issue is outward-facing and hard to fully reverse (closeable, not deletable via API).
Given the docs aren't published yet, how should the epic be created?

- A. **Materialize the epic now with a self-contained body** — embed the full 30-row backlog as text in
  the epic body (so nothing depends on published docs), and note "theme docs pending publish; links to be
  added". Honours Q1=D, avoids broken links. *(recommended if you want the tracking anchor live today)*
- B. **Defer** — create nothing outward now; record the deployment as **staged, pending publish**. Fire
  the epic (and sub-issues) after the deliverable is published on `triplem/kdiab`. *(recommended if the
  docs should land first)*
- C. **Materialize now with doc links** — accept temporary 404 links until the docs are published.
- X. Other (please specify)

[Answer]: B — Defer the epic until the deliverable is published. No GitHub issue created this stage; the epic (and sub-issues) fire after PR #1557 merges so theme-doc links resolve. Labels are already provisioned (4.2), so nothing blocks it.

---

## Q2 — Publishing the deliverable itself (git)

`docs/review/**` + `review-verify.yml` + the `aidlc/` record are uncommitted, and this backup clone has
no GitHub `origin`. Team practices forbid pushing to the trunk directly and require a feature branch → PR
→ green CI → merge-commit. What should this stage do about git?

- A. **Nothing — you'll drive git yourself.** I make no commit/push; the deliverable is published by you
  (or from the canonical `/home/triplem/projects/kdiab` clone) via the normal PR flow. This stage records
  the publish procedure as a runbook step. *(recommended — safe given the topology + practices)*
- B. **Stage a local feature branch + commit in this clone (no push)** so it's ready for you to push and
  PR.
- C. **Attempt the full publish** (feature branch → push → PR) — only viable if a GitHub `origin` remote
  is configured for this clone first.
- X. Other (please specify)

[Answer]: C — Attempt the full publish. This clone already has a GitHub `origin` (token-in-URL). Executed: feature branch `docs/1551-review-deliverable-publish` → commit (Refs #1551) → push → PR **#1557** (green, CLEAN, MERGEABLE). Merge itself left to the maintainer (scope ended at PR; team practice: human merges with a merge-commit).
