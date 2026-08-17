# Tracing Configuration — Recommendation Lifecycle

> Stage 4.4. There is no distributed runtime to trace (recommendations-only intent). The meaningful
> "trace" is the **lifecycle of one recommendation** from finding to merged fix — the end-to-end path a
> reader follows to see where any finding stands. The kdiab platform's request tracing (OTEL →
> Jaeger, `X-Correlation-ID`) is a separate concern for the running services, out of scope here.

## The recommendation trace (spans)

```
[FINDING]            FIND-<AREA>-NNN in a theme doc (evidence + recommendation)
   |  cited in
   v
[BACKLOG ROW]        one row in BACKLOG.md (area, severity, effort, phase)
   |  sequenced by
   v
[ROADMAP BAND]       Near / Mid / Long (the single phase authority, ADR-RVW-006)
   |  materialized as (deferred; epic-anchored, Q1=D)
   v
[GITHUB SUB-ISSUE]   labels area:* + severity:* (+ quick-win); linked to the epic
   |  worked on
   v
[FEATURE BRANCH]     <type>/<issue>-<slug>  (In Progress label + assignee)
   |  proposed as
   v
[PULL REQUEST]       "Closes #<sub-issue>"; kdiab CI gate (Kover/Detekt/CodeQL/...)
   |  merged
   v
[MERGED FIX]         recommendation "in production"; sub-issue closed -> D1 burn-down advances
```
<!-- Text fallback: a linear trace from a finding, to its backlog row, to its roadmap band, to a GitHub
sub-issue, to a feature branch, to a PR (Closes #issue), to a merged fix that closes the issue. -->

## Correlation IDs

| Span | Correlation key |
|---|---|
| Finding ↔ backlog ↔ roadmap ↔ issue | the **finding ID** `FIND-<AREA>-NNN` (in the doc, the backlog row, and the sub-issue title) |
| Issue ↔ branch ↔ PR | the **issue number** (`<type>/<issue>-…` branch, `Closes #N` in the PR) |
| Epic ↔ sub-issues | GitHub native `addSubIssue` linkage |

## Following one recommendation end-to-end

```bash
# From a finding ID to its issue and PR
gh issue list -R triplem/kdiab --search "FIND-CLIN-001 in:title" --state all --json number,state,title
gh pr list   -R triplem/kdiab --search "FIND-CLIN-001" --state all --json number,state,title
```

## What is NOT traced

- No span/latency instrumentation, no trace sampling — there is no service handling requests in this
  deliverable. Tracing is the *documentary* lifecycle above, verifiable via labels + `Closes #N`.
