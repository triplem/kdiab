# Load Test Plan — U1 Jackson-free JWT (#1606)

Consumes (N/A — 3.2/3.3 skipped): `nfr-requirements/performance-requirements.md`,
`nfr-requirements/scalability-requirements.md`, `nfr-design/performance-design.md`,
`nfr-design/scalability-design.md`. Plus `../observability-setup/dashboards.md` (the auth-path latency
panel this plan would populate).

Design-ready plan (no running prod to execute it — see `load-test-results.md`). It becomes runnable the
moment a running environment exists.

## Objective

Prove the Nimbus verification path adds **no measurable latency regression** versus the pre-#1606
`com.auth0:java-jwt` provider. This is a **parity** test, not an absolute-throughput test.

## What to measure

- **Auth-path overhead**: p50/p95/p99 latency of an authenticated endpoint (e.g. `GET
  /api/v1/measurements`) served with a **valid** Keycloak RS256 token, versus the same endpoint on the
  pre-#1606 build.
- **Reject-path cost**: latency of a request with an **invalid** token (should be a fast local reject —
  `401`, no upstream work).
- **Cold JWKS fetch**: first-request latency after cache expiry (24h TTL) — a one-off amortized cost,
  measured once, not per-request.

## Method (design-ready)

1. Baseline: run the load profile against the last pre-#1606 image (`sha-<prior>` from GHCR).
2. Candidate: run the identical profile against the #1606 image.
3. Tool: `k6`/`gatling` against a single service (kdiab-measures canary), steady closed-model load
   (e.g. 50 VUs, 5 min), valid-token and invalid-token scenarios.
4. Compare p95/p99 candidate vs. baseline; **pass = candidate within noise of baseline** (e.g. ≤ 5%
   p95 delta), populate the `dashboards.md` "Auth-path latency" panel.

## Why a regression is architecturally unlikely (pre-justification)

Both providers do a **local RS256 signature verify against a cached JWKS** (`Security.kt` —
`JWKSourceBuilder…cache(24h).rateLimited().retrying()`; java-jwt used an equivalent cached
`JwkProvider`). Per-request cost is CPU-bound (one signature check + issuer/audience/exp claim check),
microsecond-scale, no network on the hot path. The swap changes the library, not the algorithm or the
caching model.

## Not tested

Throughput/scalability of the services themselves (unchanged by #1606), DB, and frontend — out of
scope for a JWT-library swap.
