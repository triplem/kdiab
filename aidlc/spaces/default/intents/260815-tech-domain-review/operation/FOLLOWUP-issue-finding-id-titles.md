## Request

Include the **AI-DLC finding ID** (e.g. `DEBT-009` / `FIND-DEBT-009`) in the **GitHub issue title** when
the Technology & Domain Review findings are materialized as issues (the deferred epic + sub-issue set in
[`docs/review/BACKLOG.md`](../blob/main/docs/review/BACKLOG.md)).

> Original request: _"add the ai dlc id to the github issues (eg. DEBT-009) in the github issue title,
> please follow semver formatting: https://semver.org/"_

## Why

Right now the queued sub-issue titles are the finding *summary* only. Putting the finding ID in the title
makes every issue traceable at a glance back to its theme-doc block, backlog row, and roadmap band
(the finding ID is the correlation key across the whole deliverable — see
`docs/review/tracing-config` lifecycle).

## Proposed convention (to confirm)

- **Sub-issue title:** `[FIND-<AREA>-NNN] <summary>` — e.g. `[FIND-DEBT-009] No performance/load-testing tier across the services`.
- Apply it in the materialization step (Deployment Execution) and record it in the review's
  `CONVENTIONS.md` / materialization section so it is followed consistently.

## Open question — the semver reference

The request links https://semver.org/ (semantic versioning = `MAJOR.MINOR.PATCH`). That does not map
directly onto a finding ID like `DEBT-009`, so please confirm which is intended:

1. **Version the review *deliverable* with semver** and reference it — e.g. the doc set is `v1.0.0` at the
   original 30 findings, and adding `FIND-DEBT-009` today is a **MINOR** bump to `v1.1.0`; issue titles
   could then read `[review v1.1.0 · FIND-DEBT-009] <summary>`. (A backward-compatible addition is a MINOR
   bump; a superseded/removed finding would be a MAJOR bump.)
2. **Just a structured/consistent title format** (semver linked only as an example of a disciplined
   scheme), i.e. the `[FIND-<AREA>-NNN] <summary>` convention above with no version component.
3. **Something else** — please specify.

## Acceptance criteria

- [ ] The finding-ID title convention is documented in `docs/review/` (CONVENTIONS + materialization).
- [ ] The semver question above is resolved (deliverable versioning vs. plain structured titles).
- [ ] The queued epic + sub-issue titles in `BACKLOG.md` are updated to match the agreed format before
      materialization.

_Filed to capture the request so it is not forgotten (per user)._
