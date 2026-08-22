# Team Allocation — Jackson-free JWT Verification (#1606)

Traces to `../units-generation/unit-of-work.md`, `../user-stories/stories.md`,
`../requirements-analysis/requirements.md`, `../application-design/components.md`,
`../units-generation/unit-of-work-dependency.md`, `../units-generation/unit-of-work-story-map.md`,
`../refined-mockups/mockups.md` (skipped — no UI); governed by
`../practices-discovery/team-practices.md`.

## Allocation

| Role | Who | Responsibility |
|---|---|---|
| **Implementer** | Solo maintainer (`triplem`) + AI-DLC agents | Bolt 1 / U1 — all 8 tasks (T1–T8) |
| **Security reviewer** | Maintainer, ideally with an **external security advisor** | US-8 / T8 mandatory security review (the project's incident-response guidance recommends an external advisor for a solo maintainer) |
| **CI / gate** | GitHub Actions (automated) | Whole-platform green gate (9 Gradle modules + kdiab-ui) |

## Capacity Reality-check

- **One Bolt, one PR** — no parallelisation across Bolts needed (there is only one). Within U1, T4
  (per-service test-minter migration) is parallelisable across the 8 services if multiple agents work
  via worktrees on the same feature branch (team practice), but it is small/mechanical.
- **Estimate roll-up** (from `stories.md`): US-3 (L) is the long pole; US-1/US-4/US-6 (M); US-2/US-5/US-7/US-8 (S).
  Rough order-of-magnitude for one implementer: a few focused days including the full parity test matrix.
- **No external team dependency** — the change is self-contained in `kdiab-common` + build files + per-service tests.

## Coordination

- Single feature branch; parallel AI agents (if used) coordinate via git worktrees on that branch
  (team practice). Merge-commit to `main`, delete branches after.
