# Code-Generation Plan — U5 tech-debt-review

**Unit:** U5 · **Story:** US-5 · **Priority:** Should · **Bolt:** B8. **Deliverable:** `docs/review/tech-debt.md`.
**Carries the mandatory US-5 live-verification guard.**

## Steps
- [x] Step 1 — Read codekb `code-quality-assessment.md` (test counts, coverage, Detekt baselines, 9 debt signals).
- [x] Step 2 — **LIVE-VERIFY every codekb anchor** against the live repo (US-5 / project.md rule):
  `gh issue view 1082, 894-898`; read `vite.config.ts` floor; count nightscout baseline entries; check
  nightscout e2e dir; check `NightscoutV3Routes.kt` HISTORY; check `api:generate`; check analyze `suppressWarnings`.
- [x] Step 3 — Author `docs/review/tech-debt.md` with a live-verification results box + FIND-DEBT-001..008;
  mark resolved items (UI coverage #1082) as NOT open; correct stale #894-898↔HISTORY reference. *(→ US-5)*
- [x] Step 4 — Write plan + summary.

## Live-verify ledger (the core of this unit)
| Anchor | Result | Action |
|---|---|---|
| #1082 | CLOSED | not open debt (DEBT-002) |
| vite lines:72 | ADR-015 intentional floor | context, not a gap |
| #894-898 | CLOSED (CRUD, not HISTORY) | stale ref (DEBT-007) |
| v3 HISTORY | still stubbed (routes:77) | real untracked debt (DEBT-007) |
| nightscout baseline | 26 / 19 UnreachableCode | real (DEBT-004) |
| nightscout e2e | 0 | real (DEBT-001) |
| api:generate | 4 of 8 | real (DEBT-005) |
| analyze suppressWarnings | true | real (DEBT-008) |

## Coverage (FR-3.1 / FR-3.2)
FR-3.1: DEBT-001/003 (pyramid), DEBT-002 (Kover picture). FR-3.2: DEBT-004 (Detekt), DEBT-006 (duplication).
US-5 guard applied to all anchors; 2 stale codekb claims caught. ✓
