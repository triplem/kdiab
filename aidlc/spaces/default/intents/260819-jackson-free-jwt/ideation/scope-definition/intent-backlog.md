# Intent Backlog (proto-Units) — Jackson-free JWT Verification (#1606)

Prioritized proto-Units (MoSCoW). Traces to `../intent-capture/intent-statement.md`, the
`../feasibility/feasibility-assessment.md`, and the `../feasibility/constraint-register.md`. These
seed Units Generation (2.7); ordering reflects the risk-first sequence chosen at scope definition.
The whole set ships as one atomic PR — proto-Units are the internal build order, not separate PRs.

## Prioritized Proto-Units

| ID | Proto-Unit | MoSCoW | Depends on | Notes |
|---|---|---|---|---|
| PU-1 | **Auth characterization/parity tests** — pin CURRENT behaviour for every token class (valid, expired, wrong-audience, missing-roles, malformed-subject, HMAC test-mode) against `configureSecurity()` | **Must** | — | Risk-first: written & green on the OLD implementation first, then must stay green after the swap. The safety net. |
| PU-2 | **Build-file dependency change** — drop `ktor-server-auth-jwt` from the `ktor-server` bundle; add `nimbus-jose-jwt` to the version catalog | **Must** | — | Keep `ktor-server-auth` (base). Small, but PU-3 won't compile without it. |
| PU-3 | **Nimbus `bearer` verifier** in `Security.kt` — `bearer("auth-jwt")` provider + Nimbus RS256/`RemoteJWKSet` (prod) & `MACVerifier` HMAC (test); preserve `buildPrincipal` mapping, JWKS hardening, 401/`TOKEN_REJECTED` challenge | **Must** | PU-2 | The core change. Verified by PU-1's parity tests. |
| PU-4 | **Realm/config adjustment (conditional)** — edit `config/keycloak-realm.json` and/or `jwt.*` keys only if Nimbus needs a different audience/claim/config-key mapping; docs note for any operator-facing change | **Should** (conditional) | PU-3 | Preference: no change. Flag anything forcing an end-user re-login. |
| PU-5 | **Platform-wide jackson sweep + force-pin removal + dependency proof** — `dependencyInsight` across all 8 services confirms jackson/java-jwt/jwks-rsa gone; then remove the `kdiab.kotlin-base` jackson force-pin; capture proof as merge evidence | **Must** (pin removal gated on sweep) | PU-3 | Q3=B: remove pin in same PR IF sweep is clean everywhere; else keep pin, defer removal. Closes epic #1603's goal. |
| PU-6 | **ADR + docs** — ADR recording drop-`ktor-server-auth-jwt` / adopt-Nimbus-`bearer` decision; update any config/ops docs | **Must** | PU-3, PU-4 | Bundled in the PR (Q3). |

## Prioritization Rationale

- **Must** = required for the DoD (jackson off classpath) and the safety bar (parity tests, security-review-ready code, dependency proof). PU-1/2/3/5/6 are all on the critical path to a mergeable, DoD-satisfying PR.
- **Should (conditional)** = PU-4 fires only if the Nimbus integration forces a realm/config-key change; the goal is to preserve config identically. It is IN scope (per Q1b) but expected to be a no-op or a small documented delta.
- **Sequencing** is risk-first (PU-1 before the swap) then dependency-first for the rest (PU-2 → PU-3 → PU-4/5/6), matching the scope document's value stream.

## Notes for Units Generation (2.7)

Because this is one shared file + build files delivered as a single PR, Units Generation may collapse
PU-1..PU-6 into a **single Unit of Work** with an internal task DAG, or keep PU-1 (tests) and PU-3
(implementation) as paired sibling Units on the same Bolt. The finding that all proto-Units share the
one `Security.kt` change means they are tightly coupled and cannot be parallelised across separate PRs.
