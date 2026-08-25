<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is maintained by the orchestrator during stage execution. Add observations at the gate ritual, not by editing here directly.

## Interpretations
- 2026-08-24T00:00:00Z — record artifacts (code-generation-plan.md, code-summary.md) go under construction/jwt-test-guard/code-generation/ (for_each unit = intent slug), memory.md at stage-level per the engine memory_path — same split established in nfr-requirements.

## Deviations
- 2026-08-24T00:00:00Z — dispatched aidlc-developer-agent (subagent mode per directive) with the security-requirements.md as the authoritative spec + the enumerated ~36 test sites, rather than letting it re-derive scope. Instructed it NOT to commit/branch (we are on main, hook-protected; branch+PR is deployment-execution's job) — source changes accumulate uncommitted in the working tree.

## Tradeoffs
- 2026-08-24T00:00:00Z — guard folded INLINE in readJwtConfig() (no JwtConfig data-class signature change) to minimize blast radius; the opt-in check fires BEFORE the existing secret check (SR-7/TD-4) for deterministic failure messages.

## Open questions
- 2026-08-24T00:00:00Z — RESOLVED: developer subagent completed the change (guard in Security.kt + 36-site opt-in propagation + new AC-1 negative test). Independently verified by conductor: guard diff correct (opt-in check BEFORE secret check); 36/36 jwt.test sites, 0 missing allowTestMode (the one deliberate gap is the AC-1 guard-under-test); SecurityConfigTest AC-1/AC-2/AC-4 all PASS; Security.kt is Detekt-clean. §12a reviewer (resumed, warm context) verdict READY, verified against source.
- 2026-08-24T00:00:00Z — PRE-EXISTING (not this change): local detektMain fails on kdiab-common (21 UnreachableCode in RateLimit/AuditRoutes/Tracing) + kdiab-profiles (2: InjectDispatcher/UseOrEmpty) — files this change never touches. Known local-Detekt-version false-positive vs CI baseline (global CLAUDE.md documents UnreachableCode FPs; #1579 tracks nightscout's baseline). build-and-test must handle: run the full platform gate and, if local detekt fails on these pre-existing FPs, confirm CI (authoritative) is green rather than treating it as this patch's regression.
- 2026-08-24T00:00:00Z — build-and-test scope: only kdiab-common + kdiab-profiles compiled/tested locally; the ~30 mechanically-edited test files in the other 7 services (analyze/calc/carbs/measures/nightscout/treatments/users) are unproven until the full-platform build-and-test stage compiles+runs them + Kover ≥80%.
