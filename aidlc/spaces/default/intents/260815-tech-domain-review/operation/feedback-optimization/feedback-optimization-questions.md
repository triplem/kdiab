# Feedback & Optimization — Clarifying Questions

> Stage 4.7 (Feedback & Optimization), enterprise scope — the **final stage** of the workflow. Lead:
> aidlc-operations-agent; support: aidlc-aws-platform-agent (cost/optimization — no cloud, so no spend).
> Recommendations-only intent: SLO/cost/drift/feedback all frame the **deliverable's** lifecycle, not a
> running service. Two decisions.

---

## Q1 — Deliverable lifecycle: one-shot vs living

Is this review a one-time artifact, or a living document?

- A. **Living, refresh-on-trigger.** Keep the deliverable maintained: the currency monitor flags drift,
  findings are superseded/added as the platform evolves (FIND-DEBT-009 today is the first example), and a
  light re-review runs when `main` drifts materially. *(recommended — matches the installed monitor + the
  close-the-loop feedback design)*
- B. **One-shot, then archive.** Work the roadmap to completion, then freeze the deliverable; no ongoing
  maintenance.
- C. **Living on a fixed cadence** (e.g. re-review quarterly regardless of drift).
- X. Other (please specify)

[Answer]: B — One-shot, then archive. Work the roadmap to completion, then freeze. The installed currency monitor supports the consumption period (keeping findings current while acted on); it does not imply perpetual re-review.

---

## Q2 — Which captured improvements to record as committed follow-ups (select all that apply)

This session surfaced several improvements. Which should the feedback-loop record as tracked follow-ups?

- A. **Issue-title + semver convention** — the queued issue (finding ID in issue titles; resolve the
  semver question). *(blocked by the GitHub outage; ready-to-create)*
- B. **Deliverable semver versioning** — version the doc set (adding FIND-DEBT-009 = a MINOR bump) if you
  confirm reading (1) of the semver question.
- C. **Surface-tool bug #1553** — the AI-DLC learnings-surface reads 0 candidates (already filed upstream).
- D. **Ops follow-ups** — enable branch protection on `docs/review/**` (4.1 Q2=A) + establish an external
  clinical advisor for P0 (4.5).
- X. Other (please specify)

[Answer]: A, B, C, D — record all four as tracked follow-ups. Selecting B resolves the semver question toward reading (1): version the review deliverable with semver (adding FIND-DEBT-009 = MINOR bump v1.0.0→v1.1.0). All four are captured in feedback-loop.md.
