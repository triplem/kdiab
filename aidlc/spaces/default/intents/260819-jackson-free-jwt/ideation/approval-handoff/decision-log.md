# Decision Log — Ideation (Jackson-free JWT #1606)

All material decisions from the Ideation phase, with their source stage. Traces to
`../intent-capture/intent-statement.md`, `../scope-definition/scope-document.md`,
`../scope-definition/intent-backlog.md`, `../feasibility/feasibility-assessment.md`,
`../feasibility/constraint-register.md`, and `../market-research/competitive-analysis.md`.
(`team-assessment` and `wireframes` were skipped — D-13 — so those ideation artifacts are absent by design.)

## Decisions

| # | Decision | Stage | Rationale |
|---|---|---|---|
| D-1 | Treat #1606 as a **new feature-scope intent** (not a continuation of the completed logback intent) | intent birth | Distinct auth-touching subject; user confirmed feature scope |
| D-2 | **Definition of done = jackson off `runtimeClasspath`** across all services (closes epic #1603), verified by `dependencyInsight` | intent-capture Q1 | The real goal is eliminating jackson + its force-pin |
| D-3 | Replace **both** `java-jwt` and `jwks-rsa` (not just java-jwt) | intent-capture Q3 | Both pull jackson; both are transitive of `ktor-server-auth-jwt` |
| D-4 | Preserve claim mapping, both signing paths, JWKS hardening, error/challenge **exactly**; **allow** bounded `jwt.*`/realm config change | intent-capture Q4 / scope Q1b | Behaviour-identical auth; config may adapt to Nimbus with a docs note |
| D-5 | **One atomic PR** (single shared `kdiab-common` change) across all 8 backends | intent-capture Q5 | `configureSecurity()` is shared; no per-service divergence |
| D-6 | Verification bar = auth e2e + security review + dependency proof + `./gradlew check` + all CI green | intent-capture Q6 | Auth-touching, safety-sensitive |
| D-7 | Run market-research **lean** (build-vs-buy only); skip broad competitive/market-sizing | market-research | Internal refactor; stage's own "skip for refactors" condition |
| D-8 | **Build-vs-buy: adopt Nimbus** (`nimbus-jose-jwt`); custom verifier is the rejected alternative | feasibility Q1 | Audited crypto / low review burden wins for a safety-sensitive path; +3 deps all jackson-free |
| D-9 | **Mechanism: Ktor `bearer("auth-jwt")` provider on `ktor-server-auth` + Nimbus**; drop `ktor-server-auth-jwt` | scope Q1a | Keeps Ktor auth central; least custom code; DoD-compatible. Proven `ktor-server-auth-jwt` is the sole java-jwt/jwks-rsa consumer |
| D-10 | **Realm config IN scope** (`config/keycloak-realm.json`) if the verifier needs different audience/claim/config mapping | scope Q1b | User direction; token format unchanged; flag any forced re-login |
| D-11 | **Risk-first sequencing** — characterization/parity tests before the swap | scope Q2 | Safety net for a behaviour-preserving auth change |
| D-12 | **Bundle impl+tests+ADR and remove the jackson force-pin in the same PR**, gated on a clean platform-wide sweep | scope Q3 | Close #1603 fully; never drop a force-pin without a runtimeClasspath check |
| D-13 | **Skip team-formation + rough-mockups** ([S]) | ideation path | Solo maintainer + AI agents; no UI |
| D-14 | No compliance control beyond the mandated security review; no blockers | feasibility Q2/Q3 | T1D self-management platform, Keycloak/OIDC, no PCI |

## Open Items Carried into Inception

- **I-1**: Enumerate exact `jwt.*` / realm config mapping under Nimbus (does anything change?) → Application Design (2.6).
- **I-2**: Force-pin removal is gated on the platform-wide jackson sweep → Build & Test (3.6).
- **I-3**: ADR numbering — record under `ADR-{USR|common}-NNN` or platform `ADR-NNN` (decide in Application Design). Note: kdiab-common is the shared library; an `ADR-common-NNN` or a platform ADR both fit.
