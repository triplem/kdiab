<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is maintained by the orchestrator during stage execution. Add observations at the gate ritual, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-08-19T09:10:00Z — HARD EVIDENCE (implements project rule "verify runtimeClasspath before shedding a dep"): `gradle dependencyInsight --dependency jackson-databind --configuration runtimeClasspath` on BOTH kdiab-common and kdiab-measures shows jackson-databind is pulled EXCLUSIVELY through `io.ktor:ktor-server-auth-jwt-jvm:3.5.0`, via `com.auth0:java-jwt:4.5.2` AND `com.auth0:jwks-rsa:0.24.0` (both transitive of ktor-server-auth-jwt; neither is declared directly). No other consumer. So the real change = remove `ktor-server-auth-jwt` from the `ktor-server` bundle (`gradle/libs.versions.toml`) + write a custom Ktor AuthenticationProvider. Confirms DoD (jackson off runtimeClasspath) is achievable and closes epic #1603.
- 2026-08-19T09:10:30Z — jackson force-pin lives in `build-logic/src/main/kotlin/kdiab.kotlin-base.gradle.kts` as a `constraints { }` block (jackson-core/databind → 2.21.4, CVE-2026-54512/54513). It is a no-op when jackson is absent, so it can be REMOVED once every module is jackson-free — but removal must be gated on a full per-service sweep at build-and-test (services carry more deps than common). Conservative default: keep the constraint (harmless no-op) unless the sweep proves it dead everywhere.

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-08-19T09:11:00Z — Build-vs-buy evidence for the ADR: adopting nimbus-jose-jwt adds `net.minidev:json-smart` (+ `accessors-smart` → `org.ow2.asm:asm`) — ~3 new transitive runtime deps, all jackson-free (DoD still met). Custom verifier adds 0 (reuses kotlinx.serialization + JDK crypto). Net-dep-count favors custom; crypto-risk/review-burden favors nimbus. Both drop `ktor-server-auth-jwt` and need a custom AuthenticationProvider regardless, so that work is common.
- 2026-08-19T09:20:00Z — DECISION: user LOCKED nimbus-jose-jwt at the feasibility gate (Q1=A), resolving the build-vs-buy that market-research left open. The Application Design ADR (2.6) now DOCUMENTS this decision rather than re-deciding. Intent-statement's provisional nimbus lean is thus reaffirmed with evidence.

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
