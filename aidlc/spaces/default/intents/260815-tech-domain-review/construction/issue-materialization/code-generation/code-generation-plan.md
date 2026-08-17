# Code-Generation Plan — U10 issue-materialization (DEFERRED)

**Unit:** U10 · **Story:** US-7 (issues half) · **Priority:** Must (deferred exec) · **Bolt:** B11.
**Deliverable:** the queued ready-to-open issue set appended to `docs/review/BACKLOG.md`.

> **Execution deferred** (ADR-RVW-005, RA-Q3=A park, gh-gated A-2). NO GitHub issues created this run.

## Steps
- [x] Step 1 — Design the epic + 29 native sub-issues (one per actionable backlog row; positive verdicts
  excluded), labelled `area:*`+`severity:*` (+`quick-win`), reuse-first. *(→ FR-D.1, ADR-RVW-005)*
- [x] Step 2 — Apply dedup (FR-D.5): AR-001 cross-referenced (not re-filed); #1082 closed (no issue,
  positive verdict); #894-898 closed (HISTORY debt gets a NEW issue, not attached to them). *(→ FR-D.5)*
- [x] Step 3 — Append the "Queued GitHub issues (deferred)" section to `BACKLOG.md` (fallbackQueue). *(→ A-2, FR-D.1 fallback)*
- [x] Step 4 — Write plan + summary.

## Story-to-step traceability
| Step | Anchor |
|---|---|
| 1 | US-7 (issues), ADR-RVW-005 (epic+sub-issues, reuse-first), github-issue-management.md (addSubIssue, no-assignee) |
| 2 | FR-D.5 (no duplicate), project "reuse issues" rule |
| 3 | A-2 / FR-D.1 fallback (docs ship; issues queued) |

## Coverage
Epic + 29 sub-issues queued with labels; dedup applied; execution deferred + gh-gated per OQ-1. ✓
Note: 29 sub-issues (not 30) — backlog row 30 is the positive-verdicts marker, not an actionable item.
