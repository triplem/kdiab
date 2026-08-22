# Requirements — Jackson-free JWT Verification (#1606)

Traces to `../../ideation/intent-capture/intent-statement.md` and
`../../ideation/scope-definition/scope-document.md`; grounded in the codekb
`../../../codekb/kdiab-bkp/architecture.md`, `../../../codekb/kdiab-bkp/business-overview.md`,
`../../../codekb/kdiab-bkp/code-structure.md`; governed by `../practices-discovery/team-practices.md`.
Acceptance criteria use Given/When/Then per the inception phase rules.

## Functional Requirements

### FR-1 — Remove jackson from the runtime classpath
Drop `io.ktor:ktor-server-auth-jwt` (sole transitive source of `com.auth0:java-jwt` + `com.auth0:jwks-rsa` → jackson) from the `ktor-server` bundle; add `com.nimbusds:nimbus-jose-jwt`. Keep `io.ktor:ktor-server-auth` (base) and `io.ktor:ktor-server-swagger` (proven jackson-free — the Feasibility `dependencyInsight` on kdiab-measures showed ALL jackson paths route through `ktor-server-auth-jwt` only; `ktor-server-swagger` did not appear).
- **AC-1.1** — *Given* the merged change, *When* `gradle dependencyInsight --dependency jackson-databind --configuration runtimeClasspath` runs on every affected module, *Then* it returns no jackson on the runtime classpath (same for jackson-core, java-jwt, jwks-rsa).
- **AC-1.2** — *Given* the platform-wide sweep (FR-8), *When* it enumerates remaining runtime jackson consumers, *Then* it explicitly checks `ktor-server-swagger` (still in the bundle after #1607 dropped `ktor-server-openapi`) and confirms it pulls no jackson; if any surprise consumer survives, FR-8's fallback applies.

### FR-2 — RS256/JWKS verification via Nimbus (production)
Verify Bearer tokens with Nimbus (`DefaultJWTProcessor` + `RemoteJWKSet`) inside a Ktor `bearer("auth-jwt")` provider, replacing the java-jwt `verifier(provider, domain)` path.
- **AC-2.1** — *Given* a valid Keycloak RS256 token with a known `kid`, *When* a request hits a protected route, *Then* it authenticates and a `UserPrincipal` is populated (same as today).
- **AC-2.2** — *Given* a token signed with a key absent from JWKS or a bad signature, *When* verified, *Then* it is rejected exactly as today (401).

### FR-3 — HMAC256 verification (test mode)
When `jwt.test=true`, verify with a Nimbus `MACVerifier` (HMAC256) using `jwt.secret`. **Critical:** the current path uses `JWT.require(HMAC256).withAudience(jwtAudience).withIssuer(jwtDomain)` — issuer AND audience are enforced in test mode today. A Nimbus `MACVerifier` does **not** check issuer/audience automatically; these must be re-implemented explicitly (e.g. a `DefaultJWTClaimsVerifier` with required issuer + audience). This is the single most likely silent regression in the change.
- **AC-3.1** — *Given* `jwt.test=true` and a valid HMAC256 token, *When* a request arrives, *Then* it authenticates identically to today.
- **AC-3.2** — *Given* `jwt.test=true`, *When* a token with a **wrong issuer** or **wrong audience** (otherwise valid HMAC256) arrives, *Then* it is rejected (401) — exactly as `withIssuer`/`withAudience` reject today.
- **AC-3.3** — *Given* `jwt.test=true`, *When* an expired or bad-signature HMAC256 token arrives, *Then* it is rejected.

### FR-4 — Preserve UserPrincipal extraction exactly
Map Nimbus `JWTClaimsSet` → `UserPrincipal` with the exact rules of the current `buildPrincipal`: `sub`→userId (reject on non-UUID), `roles`→`Role` set (reject if empty), `allowed_patients`→UUID set (skip unparseable), `timezone` (default `UTC`), `audience` list; audience must contain `jwt.audience` or reject.
- **AC-4.1** — *Given* tokens exercising each rule (missing roles, **present-but-not-a-JSON-array roles**, blank/empty roles, malformed-UUID subject, wrong audience, unparseable `allowed_patients` entries), *When* verified, *Then* each yields the identical accept/reject + identical `UserPrincipal` fields as the current implementation. (Note: Nimbus claim access — `getStringListClaim` — differs from java-jwt's `getClaim(...).asList(String::class.java)`; the "present but wrong shape ⇒ empty ⇒ reject" behaviour must be preserved explicitly.)

### FR-5 — Preserve JWKS hardening
Preserve JWKS cache (size/TTL), rate-limiting, `acceptLeeway` (clock skew), and the HTTPS-required-for-non-local check.
- **AC-5.1** — *Given* an expired token within `acceptLeeway`, *When* verified, *Then* it is accepted (as today); beyond leeway it is rejected.
- **AC-5.2** — *Given* a `jwt.jwksUrl` whose host is **non-local** (the exact current predicate: host is *not* `localhost`, *not* `127.0.0.1`, and *does* contain a `.`) using a non-`https` scheme, *When* the app starts, *Then* startup fails with the same check as today. A host without a `.` (or `localhost`/`127.0.0.1`) is treated as internal and exempt.

### FR-6 — Preserve error/challenge behaviour
On rejection: respond `401` with the `ErrorResponse` body and emit the `security_event=TOKEN_REJECTED` structured log line (path/method/remote/correlationId).
- **AC-6.1** — *Given* any invalid token, *When* rejected, *Then* the response is `401` + `ErrorResponse(401,"Token is not valid or has expired")` and one `TOKEN_REJECTED` log line is emitted — identical to today.

### FR-7 — Configuration handling
Preserve `jwt.*` config keys where possible; realm config (`config/keycloak-realm.json`) and/or `jwt.*` keys may change only if Nimbus requires it, with an operator docs note. No forced end-user re-login (flag if unavoidable).
- **AC-7.1** — *Given* the existing `jwt.audience/domain/realm/jwksUrl/secret/test` config, *When* the new verifier loads, *Then* it operates without config change; any unavoidable change is documented and called out in the PR.

### FR-8 — Force-pin removal (conditional, gated, jackson-only)
The `kdiab.kotlin-base` constraints block holds **three** pins: `jackson-core`, `jackson-databind`, and **`handlebars` (CVE-2026-55760 — unrelated to jackson)**. Remove **only the two jackson constraint lines** in the same PR **iff** the platform-wide sweep proves jackson dead on every module's runtime classpath; otherwise keep them. The **handlebars pin MUST remain** regardless (removing it would silently re-open a HIGH CVE — the exact force-pin-downgrade trap the project rule warns about).
- **AC-8.1** — *Given* the sweep result, *When* it is clean everywhere, *Then* the two jackson constraint lines are removed, the handlebars pin is retained, and CI stays green (Trivy shows no new HIGH); *When* any jackson consumer survives, *Then* both jackson pins remain and the survivor is logged as a follow-up.

### FR-9 — ADR + documentation
Record an ADR for the drop-`ktor-server-auth-jwt` / adopt-Nimbus-`bearer` decision; update config/ops docs for any operator-facing change.
- **AC-9.1** — *Given* the merged PR, *Then* an ADR exists and any config/realm change has a docs note.

### FR-10 — Test token minting must not break (build blast radius)
The test suites of **every service** mint HMAC256 JWTs using `com.auth0.jwt.JWT.create().sign(Algorithm.HMAC256(JWT_SECRET))` (confirmed in `MeasureRoutesTest`, `MeasureE2ETest`, and the analyze/carbs/profiles/nightscout test helpers). `java-jwt` currently reaches this test code transitively via the main `ktor-server-auth-jwt`; dropping that artifact from `implementation` would break **test compilation across all services**. The change must keep the test suites compiling and green by either (a) migrating test token-minting to Nimbus `MACSigner`/`SignedJWT`, or (b) adding `java-jwt` as an explicit **`testImplementation`** dependency. Because the DoD targets the **runtime** classpath only, option (b) satisfies it (test-scope java-jwt/jackson never ships), but option (a) removes java-jwt entirely and is preferred for cleanliness — the choice is an Application-Design decision.
- **AC-10.1** — *Given* the merged change, *When* `./gradlew test integrationTest e2eTest` runs on every service, *Then* all suites compile and pass; no test loses its ability to mint a valid HMAC token.
- **AC-10.2** — *Given* option (b) is chosen, *When* the sweep runs, *Then* `java-jwt` appears only on `testRuntimeClasspath`, never on the main `runtimeClasspath` (so the DoD still holds).

## Non-Functional Requirements

- **NFR-1 (Security)** — No validation is weakened: signature (RS256/HMAC), issuer, audience, expiry (+leeway) all enforced. Security review passes with no unmitigated HIGH/CRITICAL. No secrets/PII logged. (OWASP A07/A09.)
- **NFR-2 (Behaviour parity)** — Functional parity is the bar (per Q1): identical accept/reject across the **full negative-path matrix** (valid, expired, nbf, wrong-audience, wrong-issuer, bad-signature, missing/blank roles, malformed-UUID subject, missing Authorization header, malformed Bearer, HMAC test valid+invalid). **No latency/startup NFR** — no perf budget required.
- **NFR-3 (Maintainability / DRY)** — the verifier lives once in `kdiab-common/plugins/Security.kt`; all 8 backends inherit it; route wiring (`authenticate("auth-jwt")`) unchanged.
- **NFR-4 (Supply chain)** — jackson + both Auth0 libs gone from runtime; no force-pin removal silently downgrades a surviving jackson into a CVE; Trivy/CodeQL/SBOM stay green.
- **NFR-5 (Testability)** — the full negative-path matrix (Q2) is automated as characterization/parity tests written against current behaviour first (risk-first), then kept green after the swap; Kover ≥80% on changed code. The matrix explicitly includes HMAC-test-mode wrong-issuer/wrong-audience (FR-3) and present-but-non-array roles (FR-4). Test token **minting** across all services must survive the java-jwt removal (FR-10).
- **NFR-6 (Deliverability)** — one atomic PR, merge-commit, `Closes #1606`, all 9 backends + kdiab-ui CI green (per team practices).

## Traceability Summary

| Requirement | Source (intent/scope) | Verified by |
|---|---|---|
| FR-1, FR-8, NFR-4 | intent DoD; scope in-scope #1/#8 | dependency proof (AC-1.1, AC-8.1) |
| FR-2, FR-3 | scope #3/#4 | AC-2.*, AC-3.1 |
| FR-4, FR-5, FR-6, NFR-2 | intent Q4; scope #4 | full negative-path matrix (AC-4.1/5.*/6.1) |
| FR-7 | scope Q1b (realm in scope) | AC-7.1 |
| FR-9, NFR-1 | intent Q6; scope #7 | ADR + security review |
| FR-10, NFR-5 | build reality (test minting); intent Q4 | all suites compile+green (AC-10.1); java-jwt test-only (AC-10.2) |
| NFR-3, NFR-6 | intent Q5; team-practices | single-file change + CI gate |

> **Review findings incorporated (2026-08-19):** the §12a reviewer (aidlc-product-lead-agent, VERDICT READY) and the orchestrator's own inline review produced six precision fixes, all folded in above: FR-8 scoped to jackson-only (handlebars pin stays); AC-1.2 names `ktor-server-swagger` as a sweep target; AC-3.2/3.3 assert HMAC-test-mode issuer/audience rejection; AC-4.1 adds the present-but-non-array roles shape; AC-5.2 states the exact non-local predicate; and **FR-10** (new) captures the cross-service test-token-minting build impact found by the orchestrator's inline scan.

## Review

**VERDICT: READY**

Reviewed against the approved intent (#1606), the scope document, and — because this is a security-critical, behaviour-preserving auth swap — the actual code under `kdiab-common/plugins/Security.kt`, `ErrorResponse.kt`, the `libs.versions.toml` bundle, and the `kdiab.kotlin-base` force-pin block. Every parity claim in FR-4/FR-5/FR-6 was verified true against the real implementation: the `buildPrincipal` reject rules (audience→null, non-UUID sub→null, empty roles→null, skip-unparseable allowed_patients, `timezone` default `UTC`), the JWKS cache/rate-limit/leeway/HTTPS-required hardening, and the `401` + `ErrorResponse(status.value, "Token is not valid or has expired")` + single `security_event=TOKEN_REJECTED` challenge line all match FR text exactly. Traceability is clean — every FR/NFR maps to an intent DoD item or a numbered scope item, and nothing out of scope (token issuance, `UserPrincipal`/`canAccess` semantics, frontend) leaks in. The negative-path matrix (Q2=A) is concrete and testable, and gating force-pin removal on a proof-driven sweep (FR-8) is the right risk posture. This is ready for engineering to start.

**Findings (non-blocking — fix before build-and-test, not before proceeding):**

- **FR-8 scope imprecision (highest-value fix).** The `kdiab.kotlin-base` constraints block holds *three* pins, not one: `jackson-core`, `jackson-databind`, **and `handlebars` (CVE-2026-55760, unrelated to jackson)**. FR-8 says "remove the jackson force-pin" without naming which lines. As written a literal reading could remove the handlebars pin too and silently re-open a HIGH CVE. FR-8/AC-8.1 must scope removal to the two jackson constraint lines only and explicitly state the handlebars pin stays. This is exactly the "removing a force-pin silently downgrades to a vulnerable version" trap the project's own learned rule (project.md, 2026-08-19) warns about — so it belongs pinned down at requirements time.

- **AC-1.1 / FR-8 rest on an unverified premise about a *different* swagger module.** The intent claims #1607 removed the Swagger→jackson path, but #1607 dropped `ktor-server-openapi`; the bundle still carries `ktor-server-swagger` (line 102, still used in `Application.kt`). Whether `ktor-server-swagger` pulls jackson is an open question that decides whether the DoD ("jackson off every runtime classpath") and the force-pin removal are even achievable in this PR. FR-8 is correctly *conditional* on the sweep, which protects the change — but AC-1.1 asserts a clean sweep as an outcome. Add an explicit **AC that names `ktor-server-swagger` as a sweep target** so build-and-test doesn't discover a surviving jackson consumer by surprise. If it survives, FR-8's fallback (keep pin, log follow-up) already covers it — but the requirement should say so, not leave it implicit in AC-1.1's blanket "no jackson."

- **FR-3 / AC-3.1 test-mode issuer claim is under-verified.** The current test path uses `JWT.require(HMAC256).withAudience().withIssuer()` — issuer IS enforced in test mode today. AC-3.1 asserts "authenticates identically" but its negative case only names "invalid/expired." The Q2=A matrix names wrong-issuer generally; make AC-3.1 explicitly assert wrong-issuer rejection *in HMAC test mode*, since that is a today-behaviour that a Nimbus `MACVerifier` rewrite could easily drop (issuer/audience checks are not built into `MACVerifier` — they must be re-added manually). This is the single most likely silent behaviour regression in the whole change.

- **FR-4 completeness — `getClaim("roles").asList(...)` null vs. missing.** The code treats a *missing* `roles` claim and a *present-but-non-list* `roles` claim both as empty→reject. FR-4/AC-4.1 says "missing/blank roles" but not "roles present but not a JSON array." Nimbus claim access differs from java-jwt here (`getStringListClaim` vs typed accessor). Add that shape to the matrix so parity is pinned, not assumed.

- **Minor — AC-5.2 wording.** The real HTTPS check treats any host without a `.` (plus `localhost`/`127.0.0.1`) as internal/exempt. AC-5.2 says "non-local `jwt.jwksUrl` without HTTPS → startup fails." That's correct, but the *definition* of "non-local" is subtle (`host.contains('.')`) and worth stating so a test author reproduces the exact predicate rather than a naive "not localhost."

No must-fix blockers. The five findings are precision tightenings for the Build & Test stage (test-matrix author and the force-pin edit); none change scope or block engineering from starting.
