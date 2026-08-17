# Build and Test — Stage Diary

Observation diary for the build-and-test stage (3.6). One entry per notable choice,
per the stage-file `## Learn` protocol. Not hand-edited outside the ritual.

## Interpretations

- 2026-08-16T18:55:00Z — For this recommendations-only / assessment intent the "build" is the
  assembly + rendering of the `docs/review/*.md` deliverable set (the code-generation output), and
  the "tests" are deliverable-verification checks: Finding-Record schema conformance, ID contiguity,
  severity discipline, cross-document traceability (theme ⇄ BACKLOG ⇄ ROADMAP ⇄ QUICK-WINS ⇄ queued
  issues), evidence-link resolution against live `main` (US-5 currency guard), and the
  recommendations-only invariant (no code/config source touched). There is no compiled software and
  no Kotlin/TS test suite to run — the deliverable *is* the code (per `project.md` correction).
- 2026-08-16T18:55:00Z — Test strategy is Comprehensive (aidlc-state.md), so the full instruction
  set is generated. Each test type is mapped onto the deliverable: unit = per-finding/per-doc schema
  validation; integration = cross-document consistency; performance = solo-maintainer navigability
  NFRs (NFR-4); security = devsecops lens over `security.md` + PII/secret-leak scan of the docs.

## Deviations

- 2026-08-16T18:55:00Z — Ran build-and-test INLINE adopting the aidlc-quality-agent (lead) +
  aidlc-devsecops-agent (support) personas, consistent with the code-generation stage's inline
  decision and this harness's standing inline guidance. The aidlc-architecture-reviewer sub-agent is
  known to hang here (`project.md` correction); no §12a reviewer is declared for this stage anyway.

## Tradeoffs

- 2026-08-16T18:55:00Z — On finding the SEC-002 backlog omission I FIXED the deliverable in place
  (stage-protocol Step 10 "diagnose and fix, then present at the gate") rather than only reporting
  it. Rationale: the fix is bounded, low-risk, and makes the deliverable self-consistent with its own
  "30 actionable findings / 30 sub-issues" headings; the alternative (report-only) would ship a
  deliverable that contradicts its own counts. Disclosed explicitly at the approval gate.

## Defects found & fixed

- 2026-08-16T18:55:00Z — DEFECT (traceability): FIND-SEC-002 (Medium security — doctor→patient access
  is JWT-embedded, revocation lags by token lifetime; full Finding-Record with recommendation +
  incremental alternative in `security.md`) was present as a canonical theme-doc finding but MISSING
  from (a) the BACKLOG ordered table, (b) the queued GitHub-issues sub-issue mapping, and (c) the
  ROADMAP Mid band — while BACKLOG's heading, README, and the sub-issues/epic sections all already
  claimed "30 actionable findings / 30 sub-issues". Single root cause: U7 backlog-assembly dropped
  one Medium security row. FIX: inserted SEC-002 at ordered-backlog row 16 (grouped with SEC-005/006,
  its value-density peers), renumbered rows 16→30, removed the placeholder row, updated the
  ordering-rationale band ranges (Medium 13–27, Low 28–30), added the sub-issue mapping row, and added
  the ROADMAP Mid-band row. Re-verified: 30/30 actionable present in backlog, sub-issues, and roadmap;
  IDs contiguous; no dupes.

## Open questions

- 2026-08-16T18:55:00Z — GitHub-issue materialization (unit U10) remains deferred/`gh`-gated
  (ADR-RVW-005, OQ-1); the queued issue set is now internally consistent at 30 rows but nothing was
  written to GitHub this run.
