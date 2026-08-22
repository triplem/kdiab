# Scope Document — Jackson-free JWT Verification (#1606)

Traces to `../intent-capture/intent-statement.md`, `../feasibility/feasibility-assessment.md`, and
`../feasibility/constraint-register.md`. Product lead with delivery-agent sequencing perspective.

## In Scope

1. **Remove the jackson source** — drop `io.ktor:ktor-server-auth-jwt` from the `ktor-server` bundle in `gradle/libs.versions.toml`. (Proven sole consumer of `com.auth0:java-jwt` + `com.auth0:jwks-rsa`, hence of jackson, on the runtime classpath.)
2. **Keep Ktor's auth framework** — retain `io.ktor:ktor-server-auth` (base, jackson-free) and use its built-in **`bearer("auth-jwt")` provider**; route wiring `authenticate("auth-jwt") { }` in every service is unchanged.
3. **Add Nimbus** — `com.nimbusds:nimbus-jose-jwt` (version-catalog entry) for token verification.
4. **Rewrite the verifier in `kdiab-common/plugins/Security.kt`** — inside the `bearer` provider's `authenticate {}`, verify with Nimbus: RS256 via `RemoteJWKSet` (prod) and HMAC256 via `MACVerifier` (`jwt.test=true`), preserving **exactly**: `buildPrincipal` claim mapping (`sub`→userId, `roles`, `allowed_patients`, `timezone`, `audience`, plus all reject rules), JWKS hardening (cache size/TTL, rate-limit, `acceptLeeway`, HTTPS-required check), and the 401 + `ErrorResponse` + `security_event=TOKEN_REJECTED` challenge behaviour.
5. **Risk-first tests** — characterization/parity tests that pin the CURRENT auth behaviour across all token classes (valid, expired, wrong-audience, missing-roles, malformed-subject, HMAC test-mode) BEFORE the swap, then remain green after it. Full auth e2e is a merge gate.
6. **Realm config (conditional, IN scope)** — `config/keycloak-realm.json` may be edited **if** the Nimbus verifier needs a different audience/claim/config-key mapping. Preference: no change. Any change that would force an end-user re-login must be flagged explicitly.
7. **ADR + docs** — an ADR recording the drop-ktor-auth-jwt / adopt-Nimbus decision; a config-key/ops note if any `jwt.*` key or realm config changes.
8. **Remove the jackson force-pin** (`kdiab.kotlin-base` constraints block) in the **same PR**, **contingent** on the build-and-test platform-wide sweep proving jackson dead on every module's runtime classpath. If any consumer survives, the pin stays and its removal defers.

## Out of Scope

- The **token issuance mechanism / auth protocol** — Keycloak still issues RS256 JWTs; the token *format* is unchanged (only realm *config* may change per item 6).
- `UserPrincipal` fields and the `canAccess` authorization logic — semantics preserved exactly.
- Any **jackson consumer outside the JWT path** — none remain (logback #1605, Swagger #1607 already done); if the sweep finds one, it is logged as a separate follow-up, not fixed here.
- Any **service route handler or business-logic change** — the provider name `auth-jwt` and route wiring stay stable so no service code changes.
- **kdiab-ui / frontend** — unaffected (token issuance and format unchanged).

## Sequencing & Delivery Shape

- **Sequencing: risk-first.** Order: (1) characterization/parity tests on current behaviour → (2) build-file dep changes → (3) Nimbus `bearer` verifier in `Security.kt` → (4) realm/config + docs if needed → (5) platform-wide sweep + force-pin removal + dependency proof → (6) ADR. Tests written against current behaviour are the safety net for the swap.
- **Delivery: one atomic PR** on a feature branch `feat/1606-*`, `Closes #1606`, merge-commit (not squash). Single shared change in `kdiab-common` (+ build files) — all 8 backends inherit it. This closes epic #1603.
- **Value stream:** reduced supply-chain/CVE surface (jackson + its force-pin gone) with zero user-visible change and no forced re-login.
