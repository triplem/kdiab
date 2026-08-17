# Code Summary — U10 issue-materialization (deferred)

**Unit:** U10 · **Bolt:** B11 · **Status:** complete (as a **queued design**, execution deferred).
**Deliverable:** "Queued GitHub issues (deferred)" section in `docs/review/BACKLOG.md`.

## What was produced (and what was NOT)
- **Produced:** the ready-to-open issue set — 1 epic + 29 native sub-issues, each with `area:*`+`severity:*`
  labels (5 also `quick-win`), the label reconciliation list, and the dedup/cross-reference rules.
- **NOT produced:** any live GitHub issue. Execution is deferred (ADR-RVW-005, RA-Q3=A park) and gh-gated
  (A-2, OQ-1). Nothing was written to GitHub.

## Key decisions
- **Faithful deferral** — the design exists, execution waits for the un-park decision; this matches the
  intent's park semantics and avoids creating 30 issues on the repo without the maintainer's go-ahead.
- **Dedup is explicit (FR-D.5):** AR-001 cross-referenced not re-filed; #1082 (closed, positive verdict)
  gets no issue; the v3 HISTORY debt gets a NEW issue rather than reattaching to the closed #894-898.
- **Repo conventions honoured:** native `addSubIssue`, no assignee at creation, reuse-first labels.
- **29 sub-issues** = 30 actionable backlog rows minus the positive-verdicts marker row.

## Test coverage summary
No tests (recommendations-only; deferred execution).

## Deviations from plan
None. Roadmap (U9) is the last unit.
