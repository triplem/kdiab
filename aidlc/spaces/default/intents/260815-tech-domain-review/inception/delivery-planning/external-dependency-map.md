# External Dependency Map — Technology & Domain Review

**Stage:** delivery-planning (2.8) · Companion to `bolt-plan.md`.
**Upstream inputs:** `requirements.md` (A-2, FR-D.1 fallback), `stories.md` (US-7), `unit-of-work.md`,
`unit-of-work-dependency.md`, `unit-of-work-story-map.md`, Application Design `components.md`,
`team-practices.md`, and `refined-mockups/mockups.md` (no external UI dependency — docs-only review).

> The review is **almost fully self-contained** — its evidence base is the RE codekb plus live-repo
> verification, both local. There is exactly one gated external dependency, and it has a designed fallback.

## Gated Items

| Item | Consumed by | Owner | Lead time | Blocks | Mitigation / workaround |
|------|-------------|-------|-----------|--------|--------------------------|
| `gh` CLI availability + auth (GitHub API) | B11 issue-materialization (U10) | Solo maintainer's environment | none (local tool) | Only B11's *issue* output — **never the docs** | `fallbackQueue()`: if `gh` is unavailable/unauthorized, write the ready-to-open issue set into `BACKLOG.md` as a queued follow-up list (FR-D.1 fallback, A-2). Docs ship regardless. |
| End-of-Inception continue decision (OQ-1) | B11 (and all execution) | Solo maintainer | human decision | All Bolt execution (Construction is parked) | The whole plan is deferred by design (RA-Q3=A); B11 runs only after the maintainer un-parks. |
| Live repo state at execution time | B1/B8 (live-verify guard) | The repo itself | none | Accuracy of codekb-tracked findings | `EvidenceLedger.verifyLive()` re-checks each anchor; unreachable → `confidence=Low` + manual re-check flag, never reported as confirmed-open on stale evidence. |

## What is NOT an external dependency

- **The evidence base** (RE codekb: `architecture.md`, `component-inventory.md`, `code-structure.md`,
  `code-quality-assessment.md`) — local, committed, treated as authoritative (A-1).
- **No external APIs, data-availability windows, or external-team hand-offs** — the review touches only local
  code and the local repo. There is no Dexcom/Glooko/LibreLinkUp/Nightscout-parity work (explicitly out of
  scope per `requirements.md`).
- **No cloud/AWS dependency** — per `project.md`, this project uses no AWS/Bedrock; nothing in the plan
  assumes cloud infrastructure.

## Summary

The critical path has **no blocking external dependency for the value-bearing deliverables** (the docs). The
single external tool (`gh`) gates only the deferred issue projection and has a designed, non-blocking
fallback. This is consistent with the intent's self-hosted, solo-maintainer posture and makes the deferral
(park) safe — there are no hidden external blockers waiting to surprise the maintainer on un-park.
