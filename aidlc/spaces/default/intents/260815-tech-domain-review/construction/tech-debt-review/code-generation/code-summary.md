# Code Summary — U5 tech-debt-review

**Unit:** U5 · **Bolt:** B8 · **Status:** complete. **Deliverable:** `docs/review/tech-debt.md`.
**The US-5 live-verification guard is this unit's headline contribution.**

## Findings produced (8)
| ID | Severity | Summary |
|---|---|---|
| FIND-DEBT-001 | Medium | nightscout 0 e2e tests (interop-critical service) |
| FIND-DEBT-002 | (verdict) | UI coverage RESOLVED (#1082 closed, lines:72 = ADR-015 floor); 80% enforced |
| FIND-DEBT-003 | Medium | Coverage-exclusion breadth overstates real coverage (users/nightscout) |
| FIND-DEBT-004 | Medium | nightscout Detekt baseline 26/19 UnreachableCode false positives |
| FIND-DEBT-005 | High | api:generate covers only 4 of 8 backends (calc, users hand-written) |
| FIND-DEBT-006 | Low | Cross-service duplication incl. glucose-conversion constant in 3 services |
| FIND-DEBT-007 | Medium | v3 HISTORY stubbed + stale #894-898 reference (untracked debt) |
| FIND-DEBT-008 | Medium | analyze suppresses all compiler warnings |

## Key decisions
- **The live-verify guard did real work.** It caught two stale codekb claims: (1) UI coverage #1082 is
  CLOSED and `lines:72` is an intentional ADR-015 floor — reported as RESOLVED, not open (the exact
  failure the project.md learned rule guards against); (2) the `TODO(#894-#898)` on the HISTORY stub
  references closed CRUD issues — the HISTORY gap is real but *untracked*.
- **gh confirmation** used for #1082 and #894–898 (all CLOSED); config/code read live for the rest.
- **Positive context recorded** — the codebase is genuinely high-discipline; these are residual debts.
- **DEBT-005 (client-gen, High)** foregrounded because calc (dose) and users (identity) drifting from
  their specs is the highest-impact item; ties to safety (calc) and security (users).

## Test coverage summary
No tests (recommendations-only). The live-verification ledger is the deliverable's integrity check.

## Deviations from plan
None. All theme units (U1–U6) now complete; backlog assembly (U7) is next.
