# NFR Validation Matrix — U1 Jackson-free JWT (#1606)

Consumes (N/A — 3.2/3.3 skipped): `nfr-requirements/performance-requirements.md`,
`nfr-requirements/scalability-requirements.md`, `nfr-design/performance-design.md`,
`nfr-design/scalability-design.md`. Plus `../observability-setup/dashboards.md`.

No formal NFR requirements/design artifacts exist (stages 3.2/3.3 skipped). The applicable NFRs for a
JWT-library swap are the implicit **parity + supply-chain** ones from ADR-023 and the acceptance
criteria in `build-and-test`/`quality-gates.md`. This matrix validates those.

## Matrix

| NFR | Target | How validated | Status |
|---|---|---|---|
| **Performance — auth latency** | No measurable regression vs. java-jwt | Architectural parity (local RS256 / cached JWKS) + CI behaviour; live load test deferred (`load-test-results.md`) | ✅ no concern (live parity deferred) |
| **Security — behavioural parity** | Identical accept/reject matrix, `UserPrincipal`, `401` body | CI negative-path matrix + ADR-023 manual security review (PASS) | ✅ verified |
| **Security — supply chain** | jackson/java-jwt/jwks-rsa absent from runtimeClasspath; no jackson 2.21.3 downgrade | `dependencyInsight … runtimeClasspath` (AC-1/AC-8) | ✅ verified |
| **Reliability — JWKS availability** | Verification tolerant of transient JWKS fetch failure | `Security.kt` `.retrying(true)` + 24h cache (stale key still serves) | ✅ by design |
| **Maintainability — coverage** | ≥ 80% line on new/modified code | Kover `koverVerify` | ✅ green |
| **Scalability** | Unchanged by a per-request local verify | No shared/contended resource added (JWKS cache is per-instance) | ✅ no change |

## Conclusion

Every applicable NFR for #1606 is **met or has no concern**. The one NFR with a deferred live check
(auth-path latency) has strong architectural + CI evidence of parity; its live confirmation is queued
in `load-test-plan.md` for if/when a running environment exists. No NFR blocks the change.
