# Incident Runbooks — Review Deliverable

> Stage 4.5 (Incident Response), enterprise scope. Lead: aidlc-operations-agent. Recommendations-only
> intent: incidents are deliverable defects and — most seriously — **implemented-recommendation
> regressions** in the live kdiab T1D platform. Detection comes from `observability-setup/dashboards.md`
> and `observability-setup/alarms.md`. The nfr-design consumes (`reliability-design.md`,
> `security-design.md`) and infrastructure-design (`deployment-architecture.md`) were skipped, so runbook
> recovery leans on the platform's existing CD + `deployment-pipeline/rollback-runbook.md`.

## Severity (platform P0–P4, Q1=B)

| Sev | Meaning | Trigger examples |
|---|---|---|
| **P0** | Patient-safety / critical | An implemented clinical-safety recommendation misbehaves in production (bad dose/IOB) |
| **P1** | High | A non-clinical implemented recommendation causes a functional/security regression |
| **P2** | Medium | Deliverable integrity failure on `main` (verify.py red); a wrong high-severity finding discovered |
| **P3** | Low | Currency drift (stale anchor) flagged by the monitor; a wrong low finding |
| **P4** | Lowest | Cosmetic doc issue |

## R1 — Deliverable integrity failure (P2) · alarm A1/A3–A7

1. Read the failing `verify.py` check in the Actions log (which of the 10 failed).
2. Fix the inconsistency on a `docs/<issue>-fix` branch (restore a dropped finding, repair a link, correct
   the headline count).
3. `python3 docs/review/verify.py` → exit 0; PR → gate green → merge.
4. If branch protection (Q2=A of 4.1) is on, the red gate already blocked the bad merge — this is the
   happy path (defect never reached `main`).

## R2 — Currency drift (P3) · alarm A2

1. The weekly monitor (`review-monitor.yml`) upserted the "Review currency drift" issue listing
   changed/missing anchors.
2. For each affected finding, re-verify against live `main` (the JIT check, 4.4 Q3=A).
3. Still valid → update the citation. No longer valid → **supersede** per `rollback-runbook.md` §2
   (mark `Superseded`, drop from backlog/roadmap, re-run `verify.py`).

## R3 — Implemented clinical-safety recommendation regresses (P0) · THE critical runbook

> A finding like FIND-CLIN-001 (IOB default) / FIND-CLIN-014 (stacking) was implemented and the change
> misbehaves in production. This is patient-safety.

1. **Rollback first (Q2=A).** Immediately `git revert` the offending merge (rollback-runbook §1) — do not
   attempt a forward-fix under time pressure on a dose path.
2. **Confirm safe state.** Verify the reverted behaviour matches the pre-change dose logic; run the
   affected service's tests (kdiab-calc unit + e2e).
3. **Clinical sanity gate (Q2=A).** Before ANY re-attempt, run `/doctor-t1d-review` on the change (and
   `/patient-t1d-review` if it is UX-facing). No re-merge without a clinical pass.
4. **Reopen** the finding's issue with the regression detail; **do not** close-as-done.
5. **Post-incident review (Q3=A, mandatory for P0).** See `incident-plan.md`; close the loop into the
   deliverable (§below).

## R4 — Implemented non-clinical recommendation regresses (P1/P2)

1. `git revert` the merge (rollback-runbook §1); confirm CI green on the revert.
2. Reopen the finding's issue with root-cause; re-approach behind the standard platform gate.
3. Post-incident review if P1 (or P2 at maintainer discretion).

## R5 — Wrong finding discovered (false positive, P2/P3)

1. A finding turns out to be already-mitigated or incorrect (e.g. a currency re-verify shows the concern
   doesn't hold).
2. **Supersede** it (rollback-runbook §2): mark `Superseded` with rationale, drop from backlog/roadmap,
   fix the headline count, re-run `verify.py`.
3. If any sub-issue was opened, close it linking the rationale.

## Close-the-loop (Q3=A) — applies to R3/R4 post-incident

- **Supersede or correct** the originating finding (R5 / rollback-runbook §2).
- **Add a preventive rule** to `docs/review/CONVENTIONS.md` so the failure class can't recur (e.g. "dose
  recommendations must ship with an IOB-stacking regression test").
- **Re-run `verify.py`**; the deliverable stays consistent.
