<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is maintained by the orchestrator during stage execution. Add observations at the gate ritual, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-08-19T12:35:00Z — CONTRADICTION RESOLVED. User doubted that `ktor-server-auth-jwt` depends on java-jwt; proved it with `gradle dependencyInsight --dependency com.auth0:java-jwt --configuration runtimeClasspath` (kdiab-common): `java-jwt:4.5.2 <- ktor-server-auth-jwt-jvm:3.5.0 <- runtimeClasspath` (and same for jwks-rsa:0.24.0). ktor-server-auth-jwt is their SOLE consumer. Decision: drop `ktor-server-auth-jwt`, keep `ktor-server-auth` (base), use its built-in `bearer("auth-jwt")` provider + Nimbus verification. Keeps Ktor's auth framework central (honours the user's instinct) while achieving the jackson-off-classpath DoD.
- 2026-08-19T12:35:30Z — SCOPE ADJUSTED from the feasibility default: realm config (`config/keycloak-realm.json`) is now IN scope (Q1b=B) — permitted to change if the Nimbus verifier needs a different audience/claim/config-key mapping. Token *format* stays a Keycloak-issued RS256 JWT; only realm CONFIG may change, and only if required. Flag any change that would force an end-user re-login.

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-08-19T12:20:00Z — CONTRADICTION at Q1: user said "ktor-server-auth-jwt is still the way to go". But feasibility proved `ktor-server-auth-jwt` IS the sole jackson source (pulls java-jwt + jwks-rsa), and its API is java-jwt-bound (`JWTCredential.payload` = java-jwt `Payload`). Keeping that artifact ⇒ jackson stays ⇒ DoD fails. Raised follow-up Q1a to distinguish "keep Ktor auth generally (custom provider on ktor-server-auth — DoD-OK)" from "keep the ktor-server-auth-jwt artifact literally (DoD impossible, re-scope)". Suspect terminology confusion between ktor-server-auth (base, jackson-free, KEPT) and ktor-server-auth-jwt (java-jwt-bound, DROPPED). MUST resolve before artifacts.
- 2026-08-19T12:20:30Z — Q1 second point: user "the keycloak realm should be adopted as well" — ambiguous. Raised Q1b: adopt-realm-as-fixed-input (no changes, matches current out-of-scope) vs include-realm-config-in-scope (allow config/keycloak-realm.json edits if the verifier needs different audience/claims/config keys). Resolve before finalizing the in/out boundary.

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-08-19T12:21:00Z — Q2=risk-first (characterization tests pin current behaviour before the swap) — strengthens the team's classic test-after default specifically because this is a security-critical, behaviour-preserving change. Q3=bundle-all + remove-force-pin in the same PR, gated on the platform-wide jackson sweep passing within the PR.
