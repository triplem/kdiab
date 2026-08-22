<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is maintained by the orchestrator during stage execution. Add observations at the gate ritual, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-08-19T17:20:00Z — §12a reviewer (aidlc-product-lead-agent) ran cleanly (101s, 16 tool uses — did NOT hang, unlike the architecture-reviewer prior). Verdict READY with 5 non-blocking precision findings, all incorporated: (1) FR-8 must scope removal to the TWO jackson pins only — the constraints block also pins handlebars (CVE-2026-55760), which MUST stay; (2) name ktor-server-swagger as an explicit sweep target (still in bundle post-#1607); (3) Nimbus MACVerifier does NOT auto-check issuer/audience — current test mode DOES (withIssuer/withAudience) → most likely silent regression; (4) roles present-but-non-array shape; (5) exact non-local HTTPS predicate (host.contains('.')).
- 2026-08-19T17:20:30Z — ORCHESTRATOR INLINE FINDING (not in reviewer list): every service's tests mint HMAC tokens via `com.auth0.jwt.JWT.create()` (java-jwt), reached transitively through the main ktor-server-auth-jwt. Dropping that artifact breaks TEST compilation platform-wide unless test minting migrates to Nimbus MACSigner (preferred) or java-jwt is added as testImplementation (DoD-compliant since DoD targets runtimeClasspath, not test). Added as FR-10 — a genuine build blast-radius expansion: the RUNTIME change is one shared file, but the TEST change touches every service's token-minting helper.

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-08-19T17:21:00Z — Chose to fold reviewer + inline findings into requirements NOW even though the verdict was already READY (no re-review needed). Cheap precision tightenings that de-risk Build & Test; better to pin them at requirements time than rediscover at implementation. FR-10's build-vs-test tradeoff (migrate test minting to Nimbus vs testImplementation java-jwt) is explicitly deferred to Application Design.

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
