# Services — Review Deliverable System

**Stage:** application-design (2.6) · Companion to `components.md` / `component-methods.md`.
**Upstream inputs:** `requirements.md` (FR-D.1…D.5), `stories.md` (US-7…US-9), `team-practices.md`.

> For a recommendations-only intent there is no long-running runtime service. The two "services" below
> are the **production pipelines** that run once per review: they define orchestration, contracts,
> lifecycle, and scaling characteristics for the doc-generation and issue-materialization work. They are
> deliberately lightweight — this review is executed by one maintainer in occasional bursts (NFR-2/NFR-4),
> not deployed.

## S1 — Doc Generation Service

- **Responsibility:** run the five `ThemeReviewWorkstream`s against the evidence base, admit valid
  `FindingRecord`s to the `PrioritizedBacklog`, then render the full `docs/review/` document set
  (5 theme docs + BACKLOG + QUICK-WINS + ROADMAP + README).
- **Orchestration pattern:** **orchestration, not choreography.** A single review producer drives the
  sequence — workstreams first, then the aggregator, then the three projections, then the index. The
  workstreams are mutually independent (INVEST independence, `stories.md`) so they may run in any order
  or in parallel; everything downstream of the backlog is strictly ordered because it reads the
  prioritized list.
- **Contract (inputs):** RE codekb + live-repo verification (via `C7.verifyLive`). **Contract (outputs):**
  markdown files under `docs/review/` (path pinned by FR-D.1, resolving OQ-3).
- **Lifecycle:** one-shot batch per review run; idempotent — re-running regenerates the same docs from
  the same evidence. No persistent state.
- **Scaling:** single maintainer, single machine; the only concurrency is optional parallel workstreams.

## S2 — GitHub Issue Materialization Service (deferred execution)

- **Responsibility:** project the finished backlog onto GitHub — create the epic, create one native
  sub-issue per backlog item, reconcile labels (reuse-first, create-missing — Q5=B), cross-reference
  already-tracked items instead of re-filing (FR-D.5), and link sub-issues to the epic via the repo's
  `addSubIssue` GraphQL pattern.
- **Orchestration pattern:** orchestration — strictly sequential and idempotency-guarded:
  1. `ensureLabels()` — reconcile the `area:*` / `severity:*` / `quick-win` / `review` label set.
  2. `createEpic()` — one "Tech & Domain Review" epic (skipped if it already exists).
  3. For each backlog item **not** already tracked: `materialize()` then `linkSubIssue()`.
  4. Already-tracked items: record a `CrossRef`, never a new issue.
- **Contract (inputs):** the prioritized backlog + cross-reference index. **Contract (outputs):**
  `IssueRef`s written back into `BACKLOG.md`, or a queued follow-up list on degradation.
- **Degradation (A-2):** if `gh` is unavailable/unauthorized, S2 does not run; `fallbackQueue()` writes
  the intended issue set into the docs and the value-bearing deliverables still ship. A tooling gap
  MUST NOT block the docs (FR-D.1).
- **Lifecycle:** one-shot, **deferred** to a later implementation decision — this intent parks at end of
  Inception (RA-Q3=A). The service is designed now; nothing is created against GitHub in this run. This
  deferral **knowingly overrides FR-D.1's "issues opened in this run" clause** — see `decisions.md`
  ADR-RVW-005 "Reconciliation with FR-D.1" for why (park decision) and how (the queued follow-up list is
  the ready-to-open issue set, un-park gated on OQ-1).
- **Idempotency:** re-running must not duplicate issues — `createEpic` and `materialize` check for an
  existing issue by title/cross-ref before creating (aligns with the project "reuse issues, don't
  duplicate" rule).

## Service Boundary Summary

| Concern | S1 Doc Generation | S2 Issue Materialization |
|---|---|---|
| Reads | codekb + live repo | prioritized backlog |
| Writes | `docs/review/*.md` | GitHub issues (or queued list) |
| External dependency | none | `gh` CLI / GraphQL |
| Runs this intent? | yes (produces the docs) | no — designed, deferred |
| Failure posture | never silent; "no concern found" is explicit | degrade to queued list, docs still ship |
| Practice conformance | markdown committed under `docs/review/` per branch-per-change | issue-per-finding, no-duplicate, no-assignee-at-creation |

Both services honour `team-practices.md`: each finding/backlog item is independently shippable within one
maintainer burst (NFR-2), so when a recommendation is later implemented it maps to a single
feature-branch-per-issue change with its own green-CI merge — no recommendation implies a practice
violation (NFR-5).
