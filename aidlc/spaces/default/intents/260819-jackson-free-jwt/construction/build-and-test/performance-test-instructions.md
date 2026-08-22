# Performance Test Instructions — U1 Jackson-free JWT (#1606)

## Applicability

**No performance NFR applies to #1606.** Per `../../../inception/requirements-analysis/requirements.md`
NFR-2, the acceptance bar is **behaviour parity**, explicitly with **no latency/startup budget**
("No latency/startup NFR — no perf budget required"). This is a like-for-like JWT-library swap on a
per-request verification path, not a throughput/latency feature.

No load tests, benchmarks, or regression-detection harnesses are generated for this stage.

## Posture (regression-avoidance only)

- **No new hot-path allocation of concern.** The Nimbus provider builds one shared
  `DefaultJWTClaimsVerifier` and (production) one cached, rate-limited `JWKSource` per install —
  parity with the retired `java-jwt`/`jwks-rsa` caching. FR-5 preserves the *intent* of bounded JWKS
  refetch, not an exact curve; no test asserts the exact cache/bucket behaviour.
- **JWKS network behaviour** (production RS256) is unchanged in shape: cached with a TTL +
  min-interval rate limiting. Not exercised by the test suites (which use HS256 `jwt.test=true`).

## If a perf question arises later (out of scope here)

Measure auth middleware latency with a simple `testApplication` micro-timing over N valid tokens
(HS256), comparing pre/post-swap — but only if a concrete latency SLO is introduced, which #1606
does not have. Track under a separate issue.
