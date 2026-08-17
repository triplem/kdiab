# Code Generation — Stage Diary

Observation diary for the code-generation stage (3.5). One entry per notable
choice, per the stage-file `## Learn` protocol. Not hand-edited outside the ritual.

## Interpretations

- 2026-08-16T16:18:13Z — "Code" for this recommendations-only intent = the `docs/review/*.md`
  deliverable set, not compiled software; the review deliverable system designed in Inception is
  authored as markdown at the workspace root (`docs/review/`), while `code-generation-plan.md` +
  `code-summary.md` remain the per-unit record artifacts. Grounded in application-design/services.md
  (S1 Doc Generation writes `docs/review/*.md`) and unit-of-work.md.
- 2026-08-16T16:18:13Z — U0 area-code abbreviations for the `FIND-<AREA>-NNN` id scheme fixed as
  CLIN (clinical-safety), DATA (data-model), SEC (security), DEBT (tech-debt), MOD (modernization).
  ADR-RVW-003 only exemplified `FIND-CLIN-001`; the other four are inferred, low-risk naming choices.

## Deviations

- 2026-08-16T16:16:20Z — Jumped to code-generation, skipping the per-unit functional-design,
  nfr-requirements, nfr-design, and infrastructure-design stages (marked `[S]`). Rationale: the user
  answered the OQ-1 park-vs-continue decision with "Produce review docs now" — the intermediate
  per-unit design stages add little for a docs-only deliverable already fully specified by the
  Inception ADRs (ADR-RVW-001..007). Recorded via `aidlc-jump.ts execute`.
- 2026-08-16T16:18:13Z — Running code-generation INLINE (adopting the aidlc-developer-agent persona)
  rather than via a per-unit Task subagent, despite the stage's `mode: subagent`. Rationale: (1) this
  harness's standing guidance is to work inline unless the user asks for a subagent — the user did not;
  (2) the deliverable is analytical review work where the main context already holds all design +
  codebase context, so a cold subagent would re-derive it at higher cost and lower fidelity; (3) the
  clinical units (U1/U2/U3) benefit from the project's `/doctor-t1d-review` + `/patient-t1d-review`
  skills, applied inline. The stage's per-unit gate is suppressed (engine `gate:false`) either way.

## Tradeoffs

- 2026-08-16T16:18:13Z — Evidence links use `path/File.kt#symbol` (ADR-RVW-007, no line number) so
  citations survive routine refactors of a moving `main`; accepted the minor "symbol lookup vs. line
  jump" navigation cost for durable, verifiable evidence under NFR-1.

## Open questions

- 2026-08-16T16:18:13Z — U10 (issue-materialization) execution stays deferred/gh-gated (ADR-RVW-005,
  OQ-1); this run produces the queued ready-to-open issue set inside BACKLOG.md, not live GitHub issues.

## Review (§12a) — inline, per project.md correction

- 2026-08-16 — The aidlc-architecture-reviewer-agent sub-agent hangs in this environment (documented
  project.md correction), so the §12a review was performed INLINE. Integrity pass over all 11 units:
  10 `docs/review/*.md` deliverables present; 39 distinct finding IDs, contiguous per area
  (CLIN-001..014, DATA-001..005, DEBT-001..008, MOD-001..005, SEC-001..007) with no gaps/dupes; every
  deliverable ≥2 H2 (required-sections equivalent PASS); evidence links spot-checked against live symbols
  (DoseCalculationService#calculateDose, Security#canAccess, AnalyticsService#computeTir, analyze
  suppressWarnings) all resolve. Code sensors (linter/type-check) have no TS/JS to check — deliverables are
  markdown only. Verdict: READY. Upstream coverage: every FR (1.1/1.2a/1.2b/1.5/2.1/2.2/3.1/3.2/4.1/D.1-5)
  and every non-Won't story (US-1..US-9) has owning findings/deliverables; US-5 currency guard applied and
  caught two stale codekb claims.
