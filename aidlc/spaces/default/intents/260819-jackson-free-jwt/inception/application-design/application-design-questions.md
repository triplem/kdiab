# Application Design — Clarifying Questions

**Intent:** #1606 — jackson-free JWT verification. Refs #1603.
**Upstream:** `../requirements-analysis/requirements.md`, `../user-stories/stories.md`, `../../../codekb/kdiab-bkp/architecture.md`, `../../../codekb/kdiab-bkp/component-inventory.md`, `../practices-discovery/team-practices.md`.

> Two genuine design decisions (the verifier internals — Nimbus `DefaultJWTProcessor` + `JWKSourceBuilder`
> caching + `DefaultJWTClaimsVerifier` for audience/issuer/exp — I'll decide and document in the ADR).
> `X. Other` is always the final option.

---

## Q1 — Test token-minting strategy (FR-10 / US-4)

Every service's tests mint HMAC tokens via `com.auth0.jwt.JWT.create()`. When `ktor-server-auth-jwt` (hence java-jwt) leaves the main classpath, test compilation breaks unless addressed.

- A. **Migrate test minters to Nimbus `MACSigner`/`SignedJWT`** — fully removes java-jwt everywhere (main *and* test); consistent with the production verifier; touches every service's test helper (more edits). Cleanest end state.
- B. **Add `testImplementation(java-jwt)`** — minimal change; java-jwt stays only on `testRuntimeClasspath` (never main, so the DoD still holds). Least effort; leaves java-jwt (and its jackson) in the test classpath only.
- C. **Extract a shared test-token helper in `kdiab-common` test-fixtures** using Nimbus, so all services reuse one minter (removes java-jwt AND de-duplicates the copy-pasted minters). Most work, best long-term.
- X. Other (please specify)

[Answer]: A — **migrate each service's test minter to Nimbus `MACSigner`/`SignedJWT`**. Removes java-jwt everywhere (main + test); consistent with the production verifier. **Mode:** guided (2026-08-19)

---

## Q2 — ADR placement & number

The change lives in `kdiab-common` (shared by all services) — a cross-cutting auth/dependency decision.

- A. **Platform ADR `docs/adr/ADR-023-jackson-free-jwt-verification.adoc`** (next free number after ADR-022) — recommended; it's a platform-wide auth decision, not service-specific
- B. **New service ADR under `kdiab-common/docs/adr/`** (create the dir; e.g. `ADR-CMN-001-...`)
- C. Both — a short platform ADR that links a detailed kdiab-common ADR
- X. Other (please specify)

[Answer]: A — **platform ADR `docs/adr/ADR-023-jackson-free-jwt-verification.adoc`** (next after ADR-022). **Mode:** guided (2026-08-19)

---

## Q3 (design finding — needs a decision) — bearer provider can't do the custom challenge; use a custom AuthenticationProvider?

**Verified** against `ktor-server-auth-jvm-3.5.0`: `BearerAuthenticationProvider$Config` has no `challenge` method — it can't reproduce FR-6's exact `ErrorResponse` body + `TOKEN_REJECTED` log. Scope-definition chose Option A (bearer); this API constraint forces a revisit.

- A. **Custom `AuthenticationProvider`** (scope-definition Option B) — on `ktor-server-auth` base, Nimbus-backed, with a `challenge {}` equivalent that reproduces FR-6 exactly. Recommended. Still keeps Ktor's auth framework + `authenticate("auth-jwt")` route wiring.
- B. **Keep the bearer provider + a global 401 handler** (StatusPages `status(Unauthorized)`) to inject the body/log — but this changes ALL 401s, not just auth rejections, and fires `TOKEN_REJECTED` for unrelated 401s. Not exact parity.
- C. **Relax FR-6** — accept the bearer provider's default 401 (no `ErrorResponse` body, no `TOKEN_REJECTED` log). Weakens parity + drops a security log. Not recommended.
- X. Other (please specify)

[Answer]: A — **custom `AuthenticationProvider`** on `ktor-server-auth` (Nimbus-backed) with a `context.challenge{}` that reproduces FR-6 exactly. Supersedes scope-definition Q1a (bearer) on the verified API constraint. Route wiring `authenticate("auth-jwt")` unchanged. **Mode:** guided (2026-08-19)

---

## Q4 (OPEN — user requested discussion) — TOKEN_REJECTED logging: reproduce exactly, or improve?

User asked to revisit `TOKEN_REJECTED`. Today the `challenge{}` logs one identical `security_event=TOKEN_REJECTED path/method/remote/correlationId` line + 401 `ErrorResponse` for ALL failure cases (missing header, malformed Bearer, bad signature, wrong issuer/audience, expired, claims-don't-map/empty-roles). Options under discussion:

- A. **Reproduce exactly (1:1)** — safest for a behaviour-preserving change; same single line for all cases.
- B. **Improve: add a `reason=` field** (expired / bad-signature / wrong-audience / no-roles / no-token) while keeping the same line prefix + 401 body — richer security telemetry, small deviation from strict parity.
- C. **Improve: distinguish "no token / missing header"** from "invalid token" (the former is arguably not a *rejected* token) — a semantic change to what counts as TOKEN_REJECTED.
- D. **Change `remote=`** from `remoteHost` to `X-Forwarded-For` (proxy-aware client IP) — separate concern, scope-creep risk.
- X. Other (please specify)

[Answer]: B + D — **add a `reason=` field AND make `remote=` proxy-aware**. Keep the log prefix + the 401 `ErrorResponse` body byte-for-byte identical (client contract unchanged). Add `reason=(no-token|malformed|bad-signature|expired|wrong-audience|wrong-issuer|invalid-claims)`, and set `remote=` to the first `X-Forwarded-For` IP falling back to `remoteHost` (best-effort; XFF is spoofable unless behind a trusted proxy — kdiab is). Intentional, documented refinement of FR-6: HTTP contract preserved exactly, security log enriched. Build & Test asserts the new `reason=` per negative-path case. **Mode:** guided (2026-08-19)

