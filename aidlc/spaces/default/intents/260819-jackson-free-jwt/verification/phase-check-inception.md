# Phase Boundary Verification — Inception → Construction (#1606)

Governance traceability check per `stage-protocol-governance.md`, run at the delivery-planning gate.
Sources: requirements, user-stories, application-design (components/methods/services/dependency/decisions),
units-generation (unit-of-work + dependency + story-map), delivery-planning (bolt-plan + allocation +
risk-sequencing + external-dependency-map).

## Requirement → Story → Design → Unit → Task Traceability

| FR/NFR | Story | Design component | Task |
|---|---|---|---|
| FR-1 (jackson off classpath) | US-2, US-6 | libs.versions.toml; sweep | T2, T6 |
| FR-2 (RS256/JWKS) | US-3 | `JwksTokenVerifier` | T3 |
| FR-3 (HMAC test) | US-1, US-3 | `HmacTokenVerifier` + shared claims verifier | T1, T3 |
| FR-4 (UserPrincipal exact) | US-1, US-3 | `ClaimsToPrincipalMapper` (exception-guarded) | T1, T3 |
| FR-5 (JWKS hardening) | US-1, US-3 | Nimbus `JWKSource` cache/rate-limit | T1, T3 |
| FR-6 (error/challenge + reason=) | US-1, US-3 | `JwtAuthenticationProvider` challenge | T1, T3 |
| FR-7 (config/realm) | US-5 | `JwtConfig` | T5 |
| FR-8 (jackson-only force-pin) | US-6 | ADR-023c | T6 |
| FR-9 (ADR) | US-7 | decisions.md → ADR-023 | T7 |
| FR-10 (test minting) | US-4 | `TestTokenMinter` | T4 |
| NFR-1 (security review) | US-8 | — (gate) | T8 |
| NFR-6 (whole-platform CI) | US-8 | — (gate) | T8 |

Every requirement traces forward through story → design → unit → task with no orphans. Every task
(T1–T8) maps back to at least one requirement.

## Consistency Checks

| Check | Result |
|---|---|
| Design decisions (ADR-023..c) consistent with requirements | ✅ (incl. the reviewer must-fix — exception-guarded mapper) |
| Unit DAG acyclic + edge_block ok | ✅ (single unit; sensor `edge_block: ok`) |
| Bolt plan matches unit DAG (1 unit → 1 Bolt) | ✅ |
| Walking-skeleton decision matches team practice | ✅ (skipped — incremental work) |
| No out-of-scope leakage (token issuance, canAccess, frontend) | ✅ |
| TOKEN_REJECTED refinement (FR-6, Q4=B+D) recorded in ADR-023b | ✅ |

## Verdict

**PASS.** Full inception→construction traceability intact; the design's one reviewer blocker is resolved;
the delivery plan is a single risk-first Bolt. Cleared to enter Construction.

## Carried-forward open items (for Construction)

- T5: confirm no `jwt.*`/realm change is actually needed (design expects none; US-5 conditional).
- T6: the jackson-pin removal is gated on the live platform-wide sweep (9 modules + `ktor-server-swagger`).
- Pin the Nimbus API to the chosen `nimbus-jose-jwt` version (`DefaultJWTClaimsVerifier` ctor, `JWKSourceBuilder`) per the design's impl notes.
