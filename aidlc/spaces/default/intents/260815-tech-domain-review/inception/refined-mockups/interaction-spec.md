# Interaction Specification — Technology & Domain Review

**Status: NOT APPLICABLE** (no application UI) — replaced by the review-output consumption flow.

## No application interaction to specify

There are no modals, wizards, inline edits, or screen states to specify because no UI is built
(`mockups.md`). The stage's "for non-UI: create an API developer-experience specification" branch also
does not apply — this intent adds **no new API surface**; it reviews the existing one.

## Review-output consumption flow (the only relevant interaction)

An async, single-actor flow over documents (from `user-flow.md`):

1. The maintainer reads the prioritized **backlog** (GitHub issues, grouped by theme).
2. Picks **quick wins** to action in the next capacity burst.
3. Uses the **phased roadmap** to sequence larger items over time.

States are trivial and inherent to GitHub/Markdown (open/closed issues; rendered docs) — no custom
loading/empty/error/success states to design.
