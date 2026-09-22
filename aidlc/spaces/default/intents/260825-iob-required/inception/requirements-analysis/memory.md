<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is maintained by the orchestrator during stage execution. Add observations at the gate ritual, not by editing here directly.

## Interpretations
- 2026-08-25T00:00:00Z — Treated GitHub issue #1563 (FIND-CLIN-001) as the authoritative upstream in place of the skipped intent-statement/scope-document (bugfix scope skips ideation). Traced every FR/NFR to the finding, per the traceability memory rule (cite the synthesized artifact, not a raw answer).
- 2026-08-25T00:00:00Z — Refined FR-3 beyond the finding's literal "always surface IOB assumed 0": warn only when a correction dose > 0 is recommended, since IOB=0 is only clinically meaningful when unaccounted insulin could stack. User confirmed (Q2=B).

## Deviations
- 2026-08-25T00:00:00Z — upstream-coverage sensor expects references to intent-statement/scope-document/team-practices; those artifacts don't exist in bugfix scope, so requirements.md names them explicitly as N/A (skipped stages) and cites the codekb artifacts it did consume, to keep the traceability section honest rather than fabricate references.

## Tradeoffs
- 2026-08-25T00:00:00Z — Q1: chose contract-required + mapper BusinessValidationException (clear clinical 400) over pure schema-required (generic "Invalid request body" 400). Costs a nullable DTO field + explicit guard, but a safety-critical rejection deserves a clinically clear message. User confirmed (Q1=B).
- 2026-08-25T00:00:00Z — Q3: apply on /api/v1 with a documented breaking-field note rather than a v2 bump. api-design.md classes a new required field as breaking, but the sole consumer already complies and a v2 for one internal endpoint is disproportionate. User confirmed (Q3=A).

- 2026-08-25T00:00:00Z — §12a product-lead review returned READY with 3 non-blocking findings, all applied: (#1) FR-2 now rejects omitted AND explicit-null identically; (#3) FR-2 extended to reject negative activeIob (a negative IOB inflates the correction — same stacking direction the fix prevents) rather than leaving the boundary unnamed; (#2) FR-3 now states mutual exclusivity with the existing iobCoversFullCorrection warning. NFR-3 test matrix expanded accordingly.

## Open questions
- 2026-08-25T00:00:00Z — None blocking. Exact warning/error wording + UI i18n keys are a code-generation detail; semantics are fixed in requirements.md.
