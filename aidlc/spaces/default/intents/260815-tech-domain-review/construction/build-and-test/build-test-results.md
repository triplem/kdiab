# Build & Test Results — Technology & Domain Review Deliverable

> Actual execution of the instruction files against the `docs/review/*.md` deliverable set on live
> `main`. This is an assessment intent: "build" = deliverable assembly/integrity; "test" = deliverable
> verification. Two defects were found by the tests and fixed in-place (stage-protocol Step 10); the
> suite is green after the fixes. Consumes the per-unit `code-generation-plan.md` + `code-summary.md`
> across units U0–U9.

## Build result

| Check | Result |
|---|---|
| All 10 deliverables present & non-empty | ✅ PASS |
| Canonical finding blocks (39: CLIN 14, DATA 5, SEC 7, DEBT 8, MOD 5) | ✅ PASS |
| Markdown fences balanced (no unterminated code blocks) | ✅ PASS |
| Evidence anchors resolve on live `main` (spot-check: `Security.kt#buildPrincipal`, `UserPrincipal.canAccess`, `DoseCalculationService`, `AnalyticsService` TIR/GMI, analyze `suppressWarnings`, `glucoseUnit`) | ✅ PASS |

Build status: **success** — the deliverable set assembles, renders, and its citations resolve.

## Test results

| Suite | Test | Result |
|---|---|---|
| Unit | T1 — mandated Finding-Record fields present (all 39) | ✅ PASS *(after fixing the check for `Recommendation (rewrite):`)* |
| Unit | T2 — ID contiguity & uniqueness per area | ✅ PASS |
| Unit | T3 — severity discipline (0 non-clinical Critical; 0 Critical total) | ✅ PASS |
| Unit | T4 — evidence cites are symbol/key based, no line numbers | ✅ PASS |
| Integration | T1 — every actionable finding in BACKLOG (30/30) | ✅ PASS *(after fixing the SEC-002 omission)* |
| Integration | T2 — BACKLOG ⇄ ROADMAP ⇄ theme phase-authority, no drift | ✅ PASS *(after fixing CLIN-010/CLIN-013)* |
| Integration | T3 — QUICK-WINS ⊆ BACKLOG, effort=S | ✅ PASS |
| Integration | T4 — README headline numbers (39 total, 0 Critical, 5 High) | ✅ PASS |
| Performance (navigability) | T1 — navigation graph fully connected (0 dead links) | ✅ PASS |
| Performance | T2 — README entry-point resolves BACKLOG/QUICK-WINS/ROADMAP/CONVENTIONS | ✅ PASS |
| Performance | T3 — docs stay skimmable (largest = clinical-safety.md 176 lines) | ✅ PASS |
| Security | T1 — recommendations-only invariant (no code/config/schema changed) | ✅ PASS |
| Security | T2 — no secrets committed in review docs | ✅ PASS |
| Security | T3 — no special-category patient PII leaked as evidence | ✅ PASS |
| Security | T4 — `security.md` finding anchors resolve on live `main` | ✅ PASS |

Totals: **15 checks run, 15 pass, 0 fail** (after applying two in-place fixes).

## Defects found by the tests — and fixed (stage-protocol Step 10)

### DEFECT-1 (traceability, integration T1) — FIND-SEC-002 dropped from the assembled backlog

- **Symptom:** `security.md` declares FIND-SEC-002 (Medium — "Doctor→patient access is JWT-embedded;
  revocation lags by token lifetime", a full Finding-Record with recommendation + incremental
  alternative), but it was **absent** from the BACKLOG ordered table, the queued GitHub-issue sub-issue
  mapping, and the ROADMAP Mid band — while README, BACKLOG, and the sub-issues/epic sections all
  already claimed "30 actionable findings / 30 sub-issues". The ordered table listed only 29 IDs.
- **Root cause:** U7 backlog-assembly dropped one Medium security row; the headline counts were written
  for 30 but the tables held 29.
- **Fix:** inserted SEC-002 at ordered-backlog row 16 (grouped with SEC-005/SEC-006, its value-density
  peers), renumbered rows 16→30, removed the placeholder row, updated the ordering-rationale band ranges
  (Medium 13–27, Low 28–30), added the sub-issue-mapping row, and added the ROADMAP Mid-band row.
- **Re-verify:** 30/30 actionable findings now present in backlog, sub-issues, and roadmap.

### DEFECT-2 (phase-authority drift, integration T2) — CLIN-010 & CLIN-013 phase stamps

- **Symptom:** the ROADMAP **Near** band lists FIND-CLIN-010 and FIND-CLIN-013 (both also championed as
  top "do-this-week" quick-wins in QUICK-WINS), but the theme doc *and* BACKLOG stamped their
  `roadmap-phase` as **Mid** — a violation of ADR-RVW-006 ("the per-finding tag and the roadmap grouping
  cannot drift"). Both are effort-S clinical quick-wins sitting at backlog rows 4–5, next to CLIN-002
  which was correctly stamped Near.
- **Root cause:** the backlog/theme stamped these two from the finding's original band rather than from
  the authoritative roadmap band (ADR-RVW-006 designates the roadmap as the single source of truth).
- **Fix:** aligned both stamps to the roadmap: theme-doc `Phase: Mid → Near` (clinical-safety.md) and
  BACKLOG `Phase` column `Mid → Near` for CLIN-010 and CLIN-013.
- **Re-verify:** 3-way phase-authority check (roadmap band ⇄ backlog Phase ⇄ theme Phase) now reports
  zero drift across all 30 actionable findings.

### False positive (not a defect) — FIND-MOD-002 "missing Recommendation"

- The first unit-T1 run flagged MOD-002 as missing a `Recommendation` field. MOD-002 is a **rewrite**
  finding and labels it `Recommendation (rewrite):` per the C-1 rule; the check's exact `Recommendation:`
  match missed the qualifier. The **check** was corrected (allow a parenthetical qualifier); MOD-002 is
  well-formed and was not modified.

## Readiness

- **Build-ready:** ✅ the deliverable set is complete and renders.
- **Test-ready / verified:** ✅ all 15 deliverable-verification checks pass after the two fixes.
- **Deployment-ready:** the docs ARE the deliverable and are committed under `docs/review/`. The only
  deferred item is the GitHub-issue projection (unit U10, `gh`-gated per ADR-RVW-005) — now internally
  consistent at 30 queued issues but not yet written to GitHub.
