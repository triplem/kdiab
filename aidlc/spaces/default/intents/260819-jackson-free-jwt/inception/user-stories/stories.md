# User Stories — Jackson-free JWT Verification (#1606)

Enabler/technical stories mapping the requirements (`../requirements-analysis/requirements.md`) and the
intent-backlog proto-Units into INVEST stories. Grounded in
`../../../codekb/kdiab-bkp/business-overview.md`, `../../../codekb/kdiab-bkp/component-inventory.md`;
governed by `../practices-discovery/team-practices.md`. Acceptance criteria use Given/When/Then;
estimates use the team S(1d)/M(3d)/L(5d) scale. Personas P1-P4 per `personas.md`.

## US-1 — Characterization/parity test harness (risk-first)
**As** the Maintainer (P1) / Security Reviewer (P2), **I want** the current auth behaviour pinned by
tests for the full negative-path matrix **before** any code changes, **so that** the Nimbus swap can be
proven behaviour-identical. *(→ PU-1, NFR-5, NFR-2; FR-4/5/6)*
- **AC** — *Given* the current `configureSecurity()`, *When* the matrix (valid, expired, nbf, wrong-audience, wrong-issuer, bad-signature, missing/blank/non-array roles, malformed-UUID sub, missing header, malformed Bearer, HMAC valid+invalid+wrong-issuer/aud) runs, *Then* every case is asserted and green on the OLD implementation.
- **Estimate:** M · **Depends on:** — · **INVEST:** independent (runs against current code).

## US-2 — Swap the dependency (drop ktor-server-auth-jwt, add Nimbus)
**As** the Maintainer (P1), **I want** `ktor-server-auth-jwt` removed from the bundle and
`nimbus-jose-jwt` added, keeping `ktor-server-auth`, **so that** the jackson-pulling artifact leaves the
runtime classpath. *(→ PU-2, FR-1)*
- **AC** — *Given* the edited `libs.versions.toml`, *When* the project resolves, *Then* `ktor-server-auth` remains, `ktor-server-auth-jwt` is gone, `nimbus-jose-jwt` is present, and everything still compiles (after US-3/US-4).
- **Estimate:** S · **Depends on:** — · **INVEST:** small, but not shippable alone (needs US-3/US-4 to compile).

## US-3 — Nimbus bearer verifier in Security.kt
**As** the Maintainer (P1), **I want** the `jwt("auth-jwt")` provider replaced by a `bearer("auth-jwt")`
provider that verifies via Nimbus (RS256/`RemoteJWKSet` + HMAC `MACVerifier`), maps claims to
`UserPrincipal` identically, and preserves JWKS hardening + the 401/`TOKEN_REJECTED` challenge, **so that**
authentication is behaviour-identical with no jackson. *(→ PU-3, FR-2/3/4/5/6)*
- **AC** — *Given* US-1's matrix, *When* the Nimbus verifier replaces the java-jwt path, *Then* every matrix case stays green, incl. HMAC-test-mode wrong-issuer/wrong-audience rejection (re-added explicitly since `MACVerifier` doesn't check them) and present-but-non-array roles → reject.
- **Estimate:** L · **Depends on:** US-1, US-2 · **INVEST:** the core vertical slice; independently testable via US-1.

## US-4 — Keep test token minting working across all services
**As** the Maintainer (P1), **I want** the cross-service test token-minters (which use
`com.auth0.jwt.JWT.create()`) to keep compiling and minting valid HMAC tokens after java-jwt leaves the
main classpath, **so that** no service's test suite breaks. *(→ FR-10)*
- **AC** — *Given* java-jwt no longer on the main classpath, *When* `./gradlew test integrationTest e2eTest` runs on every service, *Then* all suites compile+pass (via Nimbus `MACSigner` minting, or `testImplementation(java-jwt)`). **Invariant (either strategy):** java-jwt appears only on `testRuntimeClasspath`, never the main `runtimeClasspath` — a passing test build must not mask a java-jwt leak onto the main classpath (per requirements AC-10.2).
- **Estimate:** M · **Depends on:** US-2 · **INVEST:** independently verifiable (test build per service).

## US-5 — Realm/config adjustment (conditional)
**As** the Operator (P3), **I want** any required `jwt.*`/`config/keycloak-realm.json` change documented
with an ops note (and no forced re-login), **so that** deployment is not surprised. *(→ PU-4, FR-7)*
- **AC** — *Given* the Nimbus verifier, *When* it loads existing config, *Then* it works without change; *else* any change is documented + PR-flagged, and P4 (end users) are not forced to re-login.
- **Estimate:** S · **Depends on:** US-3 · **INVEST:** conditional; independently documentable.

## US-6 — Platform-wide jackson sweep + jackson-only force-pin removal
**As** the Maintainer (P1) / Security Reviewer (P2), **I want** a `dependencyInsight` sweep across all 8
backend services **and `kdiab-common`** (9 Gradle modules — the change lives in `kdiab-common`) proving
jackson gone (incl. checking `ktor-server-swagger`), then removal of **only** the two
jackson force-pin lines (handlebars pin retained), **so that** epic #1603 closes without re-opening a
CVE. *(→ PU-5, FR-8, FR-1/AC-1.2)*
- **AC** — *Given* the sweep is clean everywhere, *When* the two jackson pins are removed, *Then* the handlebars pin stays, Trivy shows no new HIGH, and CI is green; *if* any consumer survives, both jackson pins stay and it's logged as a follow-up.
- **Estimate:** M · **Depends on:** US-3, US-4 · **INVEST:** independently verifiable (dependency proof).

## US-7 — ADR + documentation
**As** the Maintainer (P1), **I want** an ADR recording the drop-`ktor-server-auth-jwt`/adopt-Nimbus
decision and any config/ops docs, **so that** the decision is traceable. *(→ PU-6, FR-9)*
- **AC** — *Given* the merged PR, *Then* an ADR exists (numbered per the ADR convention) and any operator-facing change has a docs note.
- **Estimate:** S · **Depends on:** US-3, US-5 · **INVEST:** independent doc deliverable.

## US-8 — Definition-of-Done / release gate (security review + full CI green)
**As** the Maintainer (P1) / Security Reviewer (P2), **I want** the whole-platform exit criteria named as a
checkable gate, **so that** #1606 cannot merge until it is provably safe and green. *(→ NFR-1, NFR-6; intent Q6)*
- **AC-8a (security review)** — *Given* the diff, *When* a security review (`/security-review` or equivalent) runs, *Then* there are no unmitigated HIGH/CRITICAL findings, and the review outcome is linked from the PR.
- **AC-8b (whole-platform CI green)** — *Given* the PR, *When* CI runs, *Then* **all 8 backend services + `kdiab-common` (9 Gradle modules) + kdiab-ui** are green across the full gate (`./gradlew check` — tests + Detekt + Kover), with **Kover ≥80% on changed code**, and no CI check failing or pending.
- **AC-8c (delivery form)** — *Given* the merge, *When* it lands, *Then* it is one atomic **merge-commit** PR (never squash) with `Closes #1606`, and both remote+local branches are deleted after.
- **Estimate:** S · **Depends on:** US-1..US-7 · **INVEST:** the release-gate slice; testable via CI + review status.

## MVP Boundary

All eight are **Must** for a mergeable PR that satisfies the DoD and safety bar — this is a single
cohesive change, not a partially-shippable set. US-5 is conditional (fires only if config must change);
US-8 is the release gate (security review + whole-platform CI green). There is no "nice-to-have" tier;
the only deferrable item is the follow-up if the sweep finds a surprise jackson consumer (US-6 fallback).

## Review

**VERDICT: READY**

Reviewed as the §12a product-lead reviewer for the customer/business voice, against the stage
definition, the upstream requirements (FR-1..FR-10, NFR-1..NFR-6), the ideation proto-Units
(PU-1..PU-6), and — because this is a security-critical, behaviour-preserving auth swap — the actual
code the parity claims rest on (`kdiab-common/plugins/Security.kt`, `libs.versions.toml`, and the
`kdiab.kotlin-base` force-pin block). Every code-grounded claim in the stories was verified true:
`buildPrincipal`'s reject rules, the HMAC test-mode `withAudience`/`withIssuer` enforcement that US-3's
AC calls out as the top regression risk, the JWKS cache/rate-limit/leeway/non-local-`https` predicate,
and the `401`+`ErrorResponse`+single `TOKEN_REJECTED` challenge all match the story text exactly. The
force-pin block does hold three constraint lines (jackson-core, jackson-databind, handlebars), and both
`ktor-server-auth-jwt` and `ktor-server-swagger` are still in the bundle — so US-6's "remove only the
two jackson lines, keep handlebars, sweep must check swagger" framing is grounded, not assumed.
Traceability is clean: all six proto-Units map to stories; all ten FRs map to at least one story
(coverage table plus the slash-range citations — FR-3 and FR-6 land in US-1/US-3); no orphan story and
nothing out of scope (token issuance, `canAccess` semantics, frontend) leaks in. INVEST is honestly
assessed — US-2's non-independence and US-3's size are flagged rather than hidden — and the dependency
graph, critical path, and text fallback are internally consistent. Personas fit an internal enabler
change well; P4's "success = end users never notice" is the correct customer lens for a
behaviour-preserving swap. This is ready for engineering to start.

**Findings (non-blocking — tighten before build-and-test, not before proceeding):**

- **NFR-1 (mandatory security review) and NFR-6 (full CI gate) have no dedicated story or testable exit
  AC.** The assessment dismisses them as "cross-cutting / the gate," but two of them are hard Must
  obligations that a developer/QA cannot infer a pass/fail from the stories alone: (a) the *mandatory
  security review with no unmitigated HIGH/CRITICAL*, and (b) NFR-6's *"all 9 backends + kdiab-ui CI
  green, Kover ≥80% on changed code, one atomic merge-commit PR `Closes #1606`."* US-6's AC pins
  Trivy/CI-green only for the *pin-removal sweep*; no story's AC states "every service's `./gradlew
  check` is green" or "Kover ≥80% on changed code" as the DoD exit condition. This is the one real
  completeness gap — the whole-platform green bar and the security sign-off are implied but never
  written as a checkable criterion. Either add a thin "Definition of Done / release gate" story (or an
  explicit AC on US-6/US-3) that names the 9-backend + kdiab-ui green bar, the ≥80% changed-code
  coverage floor, and the security-review sign-off.

- **Module-count inconsistency — "8 backends" vs "9 backends + kdiab-ui."** US-6 and persona P1 say the
  sweep runs across "all 8 services"; NFR-6 and team practice say "9 backends + kdiab-ui." The change
  physically lives in `kdiab-common` (the 9th Gradle module, where `Security.kt` is), and its parity
  tests compile there too. Reconcile the count so the build-and-test author does not sweep or gate one
  module short — the runtime-classpath sweep and the coverage floor must include `kdiab-common`.

- **US-2's AC is not independently satisfiable.** "…and everything still compiles (after US-3/US-4)"
  cannot go green until US-3/US-4 land. The story text honestly documents this, and for a single-PR
  change it is an acceptable INVEST compromise — but the AC as phrased is a sequencing note, not a
  standalone pass/fail. No change required; flagged so the build-and-test author does not treat US-2 as
  a gate that can be closed on its own.

- **US-4's minting-strategy choice (Nimbus `MACSigner` vs `testImplementation(java-jwt)`) is left open
  in the AC ("via … or …").** Correct to defer the decision to Application Design, but the AC should
  assert the *invariant that holds either way* — if option (b) is chosen, java-jwt appears only on
  `testRuntimeClasspath`, never the main `runtimeClasspath` (requirements AC-10.2). US-4's AC states the
  choice but not that DoD-preserving invariant; add it so a passing test build cannot mask a java-jwt
  leak onto the main classpath.

No must-fix blockers. All four findings are precision/completeness tightenings for the Build & Test
stage (the test-matrix author, the coverage/CI gate, and the force-pin edit); none change scope, break
traceability, or block engineering from starting.
