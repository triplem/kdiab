# Scope Definition — Clarifying Questions (lean)

**Intent:** #1606 — jackson-free JWT verification. Refs #1603.
**Upstream:** `../intent-capture/intent-statement.md`, `../feasibility/feasibility-assessment.md`, `../feasibility/constraint-register.md`.

> The in/out boundary is largely settled by intent-capture + the constraint register; these
> questions confirm it and set sequencing + PR bundling. `X. Other` is always the final option.

---

## Q1 — Confirm the in/out scope boundary

**In scope (proposed):** drop `ktor-server-auth-jwt` from the `ktor-server` bundle; add `nimbus-jose-jwt`; rewrite `kdiab-common/plugins/Security.kt`'s provider on Nimbus (RS256/JWKS + HMAC test mode) preserving `buildPrincipal`, JWKS hardening, error/challenge; auth parity tests; ADR + any config-key docs note.
**Out of scope (proposed):** token issuance / Keycloak realm changes; `UserPrincipal`/`canAccess` semantics; removing jackson consumers outside the JWT path (none remain); any service route/business-logic change.

- A. **Confirm as written** — in/out boundary is correct
- B. Adjust the boundary — I'll specify what to add/remove under Other
- X. Other (please specify)

[Answer]: RESOLVED via Q1a + Q1b. Boundary = **B (adjusted)**: keep the in-scope list but (i) the mechanism is Ktor's built-in `bearer("auth-jwt")` provider + Nimbus (drop `ktor-server-auth-jwt`, keep `ktor-server-auth`), and (ii) **realm config is IN scope** (`config/keycloak-realm.json` may change if the verifier needs different audience/claim/config-key mapping). See Q1a (mechanism) and Q1b (realm) below. **Mode:** guided (2026-08-19)

---

## Q2 — Sequencing preference for the work

- A. **Risk-first** — write/confirm auth **characterization (parity) tests against the CURRENT behaviour first**, then swap the implementation and make them still pass. Best safety net for a behaviour-preserving auth change (recommended)
- B. **Value-first** — implement the Nimbus swap first, then add/extend tests (classic test-after, the team default)
- C. **Dependency-first** — do the build-file dep changes first, then code, then tests
- X. Other (please specify)

[Answer]: A — **risk-first**: pin current auth behaviour with characterization/parity tests, then swap to Nimbus and keep them green. **Mode:** guided (2026-08-19)

---

## Q3 — What is bundled into #1606's single PR vs. split out?

- A. **Bundle all of it** — implementation + tests + ADR in the one PR; **keep** the jackson force-pin (no-op) and leave its removal to a later cleanup once the full sweep confirms jackson is dead everywhere
- B. Bundle implementation + tests + ADR **and** remove the jackson force-pin in the same PR (only if the build-and-test sweep proves jackson dead platform-wide within this PR)
- C. Split — implementation/tests in #1606, docs/ADR as a separate follow-up
- X. Other (please specify)

[Answer]: B — **bundle all + remove the jackson force-pin in the same PR**, contingent on the build-and-test platform-wide sweep proving jackson dead everywhere within this PR (if the sweep finds a surviving consumer, the pin stays and removal defers). **Mode:** guided (2026-08-19)

---

## Q1a (follow-up) — "keep ktor-server-auth-jwt": terminology check

`ktor-server-auth-jwt` is the artifact that transitively pulls `java-jwt` + `jwks-rsa` + jackson, and its API is java-jwt-bound. `ktor-server-auth` (base) is separate and jackson-free.

- A. **I meant keep Ktor auth generally** — a custom `AuthenticationProvider` on `ktor-server-auth` (Nimbus-backed) is exactly right; DROP the `ktor-server-auth-jwt` artifact. (DoD achievable)
- B. **Keep the `ktor-server-auth-jwt` artifact literally** — understanding this means jackson CANNOT be removed and the intent's DoD (jackson off classpath) is unachievable; re-scope the intent
- C. Let me discuss further
- X. Other (please specify)

[Answer]: A (resolved after discussion + dependency proof) — keep Ktor auth via the built-in **`bearer("auth-jwt")` provider** on `ktor-server-auth` (base, jackson-free) with **Nimbus** verification inside `authenticate {}`; DROP the `ktor-server-auth-jwt` artifact. User initially doubted the java-jwt dependency; `dependencyInsight --dependency com.auth0:java-jwt` proved `ktor-server-auth-jwt-jvm:3.5.0` is its sole consumer. DoD achievable. **Mode:** guided (2026-08-19)

---

## Q1b (follow-up) — "the keycloak realm should be adopted as well": what do you mean?

- A. **Adopt the realm as-is (fixed input)** — the new verifier must conform exactly to the existing Keycloak realm's tokens/claims; NO realm changes (this is what "out of scope: realm changes" already means)
- B. **Include realm config in scope** — allow updating `config/keycloak-realm.json` (e.g. if the new verifier needs a different audience/claim/config-key mapping); bring realm changes IN scope
- C. Something else about the realm — I'll specify
- X. Other (please specify)

[Answer]: B — **realm config IN scope**: `config/keycloak-realm.json` may be edited if the Nimbus verifier needs a different audience/claim/config-key mapping. (Still no forced end-user re-login unless a claim/audience change makes it unavoidable — flag if so.) **Mode:** guided (2026-08-19)

