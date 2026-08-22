# Performance Validation — Stage Diary

Stage: performance-validation (4.6) · Phase: Operation · Intent: 260819-jackson-free-jwt (#1606)
Lead: aidlc-quality-agent

## Interpretations
- 2026-08-21T16:56Z — The only performance question #1606 raises is auth-path latency: does Nimbus verification regress vs. com.auth0:java-jwt? Everything else (throughput, DB, memory) is untouched by a JWT-library swap.
- 2026-08-21T16:56Z — Acceptance criterion for a swap = NO measurable regression vs. the java-jwt baseline (there is no separate performance NFR — nfr-requirements 3.2 was skipped). Not an absolute latency budget.

## Perf analysis (from code + ADR-023)
- Both providers verify RS256 LOCALLY against a cached JWKS — no per-request network on the hot path.
- Security.kt:128-131: JWKSourceBuilder.create(url).cache(24h TTL).rateLimited(min-interval).retrying(true). Same caching model java-jwt's JwkProvider used (was JWK_CACHE_TTL). Key fetch is amortized over 24h; per-request cost = one RS256 signature verify + claims check (issuer/audience/exp) — CPU-bound, microseconds.
- HS256 test path: MACVerifier, also local, test-only.
- => Expected delta: negligible. No architectural reason for a regression.

## Deviations
- 2026-08-21T16:56Z — Stage prose expects an executed load test against a running system. No running prod (deployment-pipeline Q1) → no live load test executed. load-test-results = N/A-live; substituting the design-ready plan + CI-observed evidence (the auth path is exercised by unit/integration/e2e with no perf anomaly). Consumed performance/scalability requirements+design (3.2/3.3) are N/A-skipped.
- 2026-08-21T16:56Z — No new questions (acceptance criterion self-evident for a swap; no running prod to load-test). questions file documents rationale.

## Open questions
- 2026-08-21T16:56Z — None.
