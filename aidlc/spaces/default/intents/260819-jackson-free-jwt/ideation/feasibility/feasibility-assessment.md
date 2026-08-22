# Feasibility Assessment — Jackson-free JWT Verification (#1606)

Traces to `../intent-capture/intent-statement.md` (DoD: jackson off `runtimeClasspath`, behaviour
preserved exactly) and the market-research inputs `../market-research/build-vs-buy.md`,
`../market-research/competitive-analysis.md`, `../market-research/market-trends.md`. Architect lead
with compliance-agent perspective folded in (mode: inline).

## Technical Viability — HIGH (evidence-backed)

The primary technical uncertainty ("can jackson actually be removed from the runtime classpath
without breaking auth?") is **resolved by direct dependency-graph evidence**, not estimation.

**Evidence (`gradle dependencyInsight --dependency jackson-databind --configuration runtimeClasspath`):**
run on `kdiab-common` and on `kdiab-measures` (a representative DB-backed service). In both, every
path to `jackson-databind` roots at a single node:

```
io.ktor:ktor-server-auth-jwt-jvm:3.5.0
 +-- com.auth0:java-jwt:4.5.2      --> jackson-core / jackson-databind
 \-- com.auth0:jwks-rsa:0.24.0     --> jackson-databind
```

There is **no other consumer** of jackson on the runtime classpath. This matches epic #1603's
recorded history: of the three original jackson consumers, logback-jackson was removed by #1605 and
`ktor-server-openapi`/Swagger by #1607, leaving the JWT auth path (this issue) as the last.

**Conclusion:** dropping `ktor-server-auth-jwt` from the `ktor-server` bundle
(`gradle/libs.versions.toml`) and replacing it with a custom Ktor `AuthenticationProvider` removes
`java-jwt`, `jwks-rsa`, and therefore jackson entirely — satisfying the DoD. Neither Auth0 library is
declared directly, so no separate removal is needed; they leave with `ktor-server-auth-jwt`.

## Chosen Approach & Effort

**Decision (locked at this gate):** adopt **`nimbus-jose-jwt`** (BUY). Rationale: this is a
safety-sensitive authentication path on a T1D platform; an audited JOSE library minimizes the
security-critical code we own and the review surface. Nimbus adds `net.minidev:json-smart`
(+ `accessors-smart` → `org.ow2.asm:asm`) — ~3 transitive runtime deps, all jackson-free, so the DoD
still holds. The zero-new-dep custom verifier remains the documented rejected alternative.

**Scope of change (single shared file + build files):**
1. `gradle/libs.versions.toml` — remove `ktor-server-auth-jwt` from the `ktor-server` bundle; add `nimbus-jose-jwt` (+ version catalog entry).
2. `kdiab-common/plugins/Security.kt` — replace the ktor `jwt("auth-jwt")` provider with a custom `AuthenticationProvider` that verifies via Nimbus `DefaultJWTProcessor` + `RemoteJWKSet` (RS256/JWKS) and a `MACVerifier` (HMAC test mode), then maps `JWTClaimsSet` → `UserPrincipal` with the exact same rules as today's `buildPrincipal`.
3. `build-logic/.../kdiab.kotlin-base.gradle.kts` — the jackson force-pin constraint becomes a no-op; **keep it** unless a full per-service sweep at build-and-test proves jackson dead in every module (conservative — removing a no-op constraint is optional cleanup, not required for the DoD).

**Effort:** Medium. One shared file of real logic + a custom Ktor provider, plus full auth e2e
parity tests. Blast radius is all 8 backends via `kdiab-common`, but it is one atomic change.

## Risk Analysis

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Behaviour drift in claim extraction (roles/allowed_patients/audience/timezone) | Medium | High (auth bug) | Port `buildPrincipal` rules 1:1; full auth e2e parity suite (valid/expired/wrong-aud/no-roles/bad-sub/HMAC) is a merge gate (Q6-A) |
| JWKS key-rotation / caching regression | Medium | High | Nimbus `RemoteJWKSet` mirrors `jwks-rsa` caching; preserve cache size/TTL, rate-limit, `acceptLeeway`, HTTPS-required check |
| Test-mode HMAC path breaks (`jwt.test=true`) | Low | Medium (test suites) | Implement `MACVerifier` HMAC256 path with same secret/audience/issuer checks; covered by existing test config |
| `json-smart` carries its own CVE | Low | Medium | Feasibility notes json-smart is smaller than jackson but not CVE-free; Trivy/CodeQL CI + security review (Q6-B) catch it; can pin if needed |
| Force-pin removal silently downgrades something | Low | High | Do **not** remove the jackson constraint as part of #1606 unless the full sweep proves it dead; per project rule, never drop a force-pin without a runtimeClasspath check |
| `jwt.*` config-key churn confuses operators | Low | Low | Q4 permits bounded churn *with* a docs note + PR call-out; prefer keeping keys identical |

**Overall feasibility verdict: GO.** No technical blocker; the DoD is provably reachable; the main
work is careful behaviour-parity implementation + testing of a security-critical path.
