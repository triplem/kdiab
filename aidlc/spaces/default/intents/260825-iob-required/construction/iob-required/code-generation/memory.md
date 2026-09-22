<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is maintained by the orchestrator during stage execution. Add observations at the gate ritual, not by editing here directly.

## Interpretations
- 2026-08-25T00:00:00Z — Stage mode is `subagent`, but I implemented INLINE as the developer-agent persona. Justification: tightly-scoped safety fix fully specified by requirements.md, and I already held all context inline (exact files, StatusPages behavior, kotlinx.serialization semantics, the FE client). A cold subagent would re-derive it and risk drift on a safety-critical change; harness guidance also discourages spawning agents unnecessarily. Same justified inline deviation as reverse-engineering.
- 2026-08-25T00:00:00Z — Verified the ONLY production DoseRequest(...) construction site is CalcMapper (now supplies a validated non-negative value); no other module references DoseRequest/DoseRequestDto. So removing the domain default is safe — only test constructors needed updating.

## Deviations
- 2026-08-25T00:00:00Z — Extended beyond the plan's step titles to update the integration + e2e suites (raw JSON bodies omitted activeIob → would now 400) and added HTTP-layer FR-2 tests, so the full three-tier suite compiles/passes. Implied by NFR-3 + the removed default; not a scope change.

## Tradeoffs
- 2026-08-25T00:00:00Z — DTO field chosen as `Double? = null` (not non-nullable-no-default): routes omitted AND explicit-null through the mapper's clinical 400 rather than kotlinx's generic SerializationException 400. Costs a nullable field + guard but delivers the clinical message (Q1=B).
- 2026-08-25T00:00:00Z — e2e valid-dose test given activeIob=1.0 (not 0.0) to preserve its `warnings.size == 0` happy-path assertion; a 0.0 there would now legitimately emit the FR-3 warning. Kept the assertion meaningful rather than loosening it.

- 2026-08-25T00:00:00Z — §12a architecture-reviewer returned READY (completed cleanly this run — did NOT hang, unlike the design-doc reviews the project memory warns about). It verified all 4 flagged points end-to-end. Non-blocking findings, none applied here: (a) an unparseable treatment date makes the UI's calcIOB return NaN → serialized as null → now a safe fail-closed 400 (better than the old silent 0) — worth a FOLLOW-UP issue to guard calcIOB against NaN, out of scope for #1563; (b) pre-existing OpenAPI ErrorResponse.code integer vs RateLimitErrorResponse.code string; (c) pre-existing kdiab-calc/CLAUDE.md route-path drift (/dose vs /calc/dose). (b)+(c) are pre-existing, untouched.
- 2026-08-25T00:00:00Z — Verified kdiab-ui/src/__tests__/DoseCalculator.test.tsx needs NO change: it mocks calcApi.calculateDose and asserts activeIob: expect.any(Number) (component always sends a computed number); the test's activeIob is an optional COMPONENT PROP, unaffected by the required DoseRequestBody type.

## Open questions
- 2026-08-25T00:00:00Z — Compilation + Kover coverage verified in the Build and Test stage (./gradlew check). Pre-existing hardcoded test JWT secrets (integration + e2e) tripped the secret-scan hook — false positives, part of the documented jwt.test=true HMAC fixture, not introduced by this change; left untouched.
