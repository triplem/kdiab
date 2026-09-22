# Performance Test Instructions — #1563 activeIob required

Consumes `../iob-required/code-generation/code-summary.md`,
`../iob-required/code-generation/code-generation-plan.md`.

## Applicability

**N/A — no performance dimension changed.** The change adds two O(1) null/sign checks in the inbound
mapper and one O(1) boolean check in the warnings `buildList`. kdiab-calc is stateless and does no
additional I/O; the upstream `getActiveProfile` call is unchanged. There is no new query, loop, or
allocation of note.

## What would be measured if it applied

Were a perf concern present, the target would be the `POST /users/{id}/calc/dose` p95 latency, which is
dominated by the single upstream HTTP call to kdiab-profiles — unaffected by this change. No load test is
warranted for a Minimal-scope input-validation bugfix.
