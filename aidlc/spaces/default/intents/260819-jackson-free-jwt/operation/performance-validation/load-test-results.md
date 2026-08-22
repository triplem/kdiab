# Load Test Results — U1 Jackson-free JWT (#1606)

Consumes (N/A — 3.2/3.3 skipped): `nfr-requirements/performance-requirements.md`,
`nfr-requirements/scalability-requirements.md`, `nfr-design/performance-design.md`,
`nfr-design/scalability-design.md`. Plus `../observability-setup/dashboards.md`.

## No live load test executed (N/A — no running environment)

The `load-test-plan.md` requires a running instance to drive load against; there is none (no running
prod — deployment-pipeline Q1). **No live load test was executed.** The plan is design-ready for when a
running environment exists.

## Standing evidence (in lieu of a live load test)

| Evidence | Source | Finding |
|---|---|---|
| Auth path exercised under test | CI unit + integration + e2e (`build-and-test`) | No latency anomaly / timeout observed; suites pass in normal time |
| Architectural parity | `Security.kt` + ADR-023 (`load-test-plan.md` § "Why a regression is architecturally unlikely") | Local RS256 verify over 24h-cached JWKS — same model as java-jwt; no hot-path network |
| JWKS fetch amortization | `Security.kt:129` `cache(24h TTL)` | Key fetch is a one-off per 24h, not per-request — cannot dominate p95 |
| Reject path | negative-path test matrix | Fast local `401`, no upstream work |

## Verdict

**No measurable performance concern.** With no running prod, the live parity load test is **deferred**
(runnable via `load-test-plan.md` when an environment exists); the architectural analysis + CI
behaviour give high confidence the swap is latency-neutral. If a running environment is later
introduced, execute `load-test-plan.md` and record the candidate-vs-baseline p95/p99 here to close the
deferral.
