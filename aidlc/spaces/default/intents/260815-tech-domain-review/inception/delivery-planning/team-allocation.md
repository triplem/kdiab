# Team Allocation — Technology & Domain Review

**Stage:** delivery-planning (2.8) · Companion to `bolt-plan.md`.
**Upstream inputs:** `requirements.md` (NFR-4 solo audience), `stories.md` (persona Sam), `unit-of-work.md`,
`unit-of-work-dependency.md`, `unit-of-work-story-map.md`, `team-practices.md`, Application Design
`components.md`, and `refined-mockups/mockups.md` (no allocation impact — a docs-only review has no UI build to staff).

> This is a **solo-maintainer** project (persona Sam, NFR-4: deliverables readable by one non-committee
> maintainer). There is one mob of one. Every Bolt is owned by the solo maintainer, optionally AI-assisted.
> There is no Program Board (that analog only applies when the team count > 1).

## Allocation

| Bolt | Owner | Notes |
|------|-------|-------|
| B1 review-foundations | Solo maintainer (Sam) | AI-assisted authoring; sets the shared contract |
| B2 clinical-dose-review | Solo maintainer (Sam) | Highest-scrutiny Bolt; the non-trimmable floor |
| B3 clinical-guardrails-metrics | Solo maintainer (Sam) | |
| B4 data-model-review | Solo maintainer (Sam) | |
| B5 backlog-assembly (initial) | Solo maintainer (Sam) | |
| B6 quick-wins (initial) | Solo maintainer (Sam) | |
| B7 security-review | Solo maintainer (Sam) | |
| B8 tech-debt-review | Solo maintainer (Sam) | uses the live-verify guard |
| B9 modernization-review | Solo maintainer (Sam) | |
| B10 roadmap | Solo maintainer (Sam) | |
| B11 issue-materialization | Solo maintainer (Sam) | deferred; requires `gh` auth (external dependency) |

## Concurrency & Capacity

- **Strictly sequential** — one Bolt per burst. Although `unit-of-work-dependency.md` identifies parallel
  opportunities (the six theme units, and backlog vs quick-wins), a single maintainer executes serially, so
  the DAG's parallelism is latent capacity, not a scheduling input here.
- **Capacity model:** occasional bursts (NFR-2). Each Bolt is sized to one burst (unit complexity S–L from
  `unit-of-work.md`); the L-sized B2 (dose review) may span two bursts.
- **No external team hand-offs** — the review is self-contained; the only external dependency is the `gh`
  CLI for B11 (see `external-dependency-map.md`).

## Notes on Team Formation (1.5)

Team Formation did not establish multiple mobs for this intent (solo maintainer). Per `team-practices.md`,
parallel agents may collaborate via git worktrees on the same branch if the maintainer chooses to parallelize
a Bolt, but the default allocation is one person owning the sequence end-to-end. This keeps the plan faithful
to the solo-maintainer NFR-4 audience.
