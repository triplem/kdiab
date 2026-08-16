<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is maintained by the orchestrator during stage execution. Add observations at the gate ritual, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-08-15T19:27:47Z — Ran market research LEAN (3 questions, reframed as "reference landscape") because positioning is a personal self-hosted tool (Q2=A) and interoperability was deprioritized (Q6); full competitive/market analysis would be noise for an internal recommendations-only review.

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-08-15T19:27:47Z — Fetched the Nocturne repo (github.com/nightscout/nocturne) via WebFetch to satisfy the ideation guardrail "market-research claims require citations"; generated the rest from labelled ecosystem knowledge rather than fetching Nightscout/Tidepool (lower value, marked [verify]).

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-08-15T19:27:47Z — Build-vs-buy verdict for kdiab-calc: BUILD + align to references, NOT embed AAPS/Loop/OpenAPS. Rationale: those are closed-loop AID engines (safety/scope/regulatory weight) vs. kdiab-calc's advisory bolus role; the bolus-wizard formula is public spec, so the work is correctness assurance not reinvention.

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-08-15T19:27:47Z — [safety] Verify kdiab-calc implements IOB (insulin-on-board) subtraction and dosing guardrails (max-bolus, negative-correction, unit mg/dL vs mmol/L) — carry into Reverse-Engineering / Functional-Design as the highest-value correctness thread.
- 2026-08-15T19:27:47Z — Verify kdiab's Keycloak realm exposes passkeys/OIDC (auth hardening signal from Nocturne) and that TIR/AGP computations match consensus definitions.
