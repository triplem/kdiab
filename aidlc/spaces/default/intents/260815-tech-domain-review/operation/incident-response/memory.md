<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is maintained by the orchestrator during stage execution. Add observations at the gate ritual, not by editing here directly.

## Interpretations
- 2026-08-16T20:25:00Z — Recommendations-only intent: two incident classes. (1) Deliverable incidents — verify.py red on main, currency drift — low severity, revert/supersede. (2) Implemented-recommendation regressions — a finding that was acted on and caused a real kdiab incident; for a T1D platform the top class is a clinical-safety regression (dose/IOB), which is genuine patient-safety, mapped to the highest urgency. Design centers on class (2) clinical.
- 2026-08-16T20:25:00Z — Consumes reliability-design.md, security-design.md (nfr-design) and deployment-architecture.md (infrastructure-design) do not exist (stages skipped); observability dashboards.md + alarms.md do exist and feed detection. Sourced runbooks from those + the clinical-safety findings + rollback-runbook.md.

## Deviations

## Tradeoffs
- 2026-08-16T20:30:00Z — Q1=B chose platform P0-P4 over the two-track review-severity model (A). Reconciled by MAPPING: clinical-safety regression=P0, non-clinical implemented regression=P1, deliverable integrity=P2, currency drift=P3, cosmetic=P4, with a safety-first "uncertain -> P0" default. This keeps the repo's existing severity taxonomy while still making patient-safety the top class (the intent of ADR-RVW-004 Critical=clinical).
- 2026-08-16T20:30:00Z — The genuinely important incident class for this T1D deliverable is an IMPLEMENTED clinical-safety recommendation regressing (R3, P0) — not the docs breaking. Centered the runbooks there: rollback-first (never forward-fix a dose path under pressure) + a mandatory /doctor-t1d-review gate + close-the-loop back into CONVENTIONS.md.

## Open questions
- 2026-08-16T20:30:00Z — Escalation gap: no external clinical advisor is currently named for P0 sign-off; escalation-matrix recommends establishing one before the first clinical-safety recommendation ships.
