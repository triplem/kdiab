# Phased Roadmap

> **Unit U9.** The complete [backlog](./BACKLOG.md) sequenced into **Near / Mid / Long** value-density
> bands (FR-D.3, ADR-RVW-006). The band is the single source of truth for each finding's `roadmap-phase`
> — the backlog stamps the same value, so tag and band cannot drift. Every item is independently
> shippable in one maintainer burst (NFR-2); phases are for *ordering*, not coordinated releases.
> Band rule: **Near** = quick-wins + Must clinical-safety · **Mid** = Should security + tech-debt ·
> **Long** = Could modernization + structural data-model.

## Near — safety + highest value-per-effort (target: ~1–2 weeks of bursts)

Start here. These are the highest-value items, weighted to clinical safety, mostly small effort.

| ID | Area | Sev | Eff | One-liner |
|---|---|---|---|---|
| FIND-SEC-001 | security | High | S | Guard the test-JWT mode out of production (one-line, platform-wide risk) |
| FIND-CLIN-002 | clinical-safety | Med | S | Validate `glucoseUnit`; reject unknown units |
| FIND-CLIN-013 | clinical-safety | Med | S | Soft implausible-dose guard at the treatments boundary |
| FIND-CLIN-010 | clinical-safety | Med | S | Switch estimate to GMI + relabel |
| FIND-CLIN-001 | clinical-safety | High | M | Make `activeIob` required (close the stacking default) |
| FIND-DEBT-005 | tech-debt | High | M | Wire calc + users specs into `api:generate` first (drift-critical) |

**Rough effort:** ~4×S + 2×M ≈ 8–10 maintainer-days. **Rationale:** clears four of the five quick-wins
plus the two highest-impact medium-effort items (IOB default, spec drift on the dose/identity services).

## Mid — the bulk of correctness, domain, security & debt (target: ~1–2 months of bursts)

Do after Near. Grouped by value-density within the band.

| ID | Area | Sev | Eff | One-liner |
|---|---|---|---|---|
| FIND-CLIN-014 | clinical-safety | High | L | Server-side IOB + stacking warning (depends on FIND-CLIN-001) |
| FIND-SEC-004 | security | High | L | Determine MDR/SaMD classification of the dose calculator (flag → decision) |
| FIND-CLIN-003 | clinical-safety | Med | M | Profile-driven max-bolus guardrail |
| FIND-CLIN-004 | clinical-safety | Med | M | Make trend adjustment advisory/capped |
| FIND-DATA-001 | data-model | Med | M | Carb absorption time (extended-bolus enabler) |
| FIND-DATA-003 | data-model | Med | M | Typed extended-bolus / temp-basal payloads (+ percent temp basal) |
| FIND-DATA-002 | data-model | Med | M | Sensor-calibration measure type |
| FIND-SEC-002 | security | Med | M | Keep access-token TTL short (≤15 min) + document doctor-access revocation-latency window |
| FIND-SEC-005 | security | Med | M | Reconcile GDPR erasure vs MDR retention (documented lawful basis) |
| FIND-SEC-006 | security | Med | M | Verify Art-9 safeguards (encryption-at-rest, DPA, IP-log retention) |
| FIND-DEBT-008 | tech-debt | Med | S | Remove analyze `suppressWarnings`, triage warnings |
| FIND-DEBT-001 | tech-debt | Med | M | Nightscout e2e/contract tests |
| FIND-DEBT-007 | tech-debt | Med | M | v3 HISTORY: new tracking issue + decide implement vs 404 |
| FIND-DEBT-004 | tech-debt | Med | M | Burn down the nightscout Detekt baseline |
| FIND-DEBT-003 | tech-debt | Med | M | Narrow coverage exclusions (users/nightscout first) |
| FIND-DEBT-009 | tech-debt | Med | M | Add a performance/load-testing tier (start: calc dose, analyze BFF) |
| FIND-CLIN-006 | clinical-safety | Low | S | Reject implausible `currentBg` |
| FIND-CLIN-005 | clinical-safety | Low | S | Round to pump increment |
| FIND-SEC-007 | security | Low | S | CSP hardening |
| FIND-MOD-003 | modernization | Low | S | Unified platform version |

**Rationale:** completes the clinical-safety hardening (stacking detection depends on the Near IOB work),
resolves the two regulatory flags into decisions, fills the domain data-model gaps that unlock
absorption-aware bolusing, and burns down the residual tech-debt.

## Long — structural / strategic (target: quarter-scale, only if the value holds)

Larger, reversible-but-heavy changes. Revisit whether each still earns its keep before committing.

| ID | Area | Sev | Eff | One-liner |
|---|---|---|---|---|
| FIND-DATA-004 | data-model | Med | M | Temporary profile override for illness/exercise |
| FIND-DATA-005 | data-model | Med | L | Per-type JSONB payload validation across stores |
| FIND-MOD-004 | modernization | Med | M | Metrics + alerting + log-aggregation (opt-in compose profile) |
| FIND-MOD-002 | modernization | Med | L | Consolidate the nine services — **incremental first**: collapse the stateless trio, unify deployment |
| FIND-DEBT-006 | tech-debt | Low | M | De-duplicate boilerplate; unify glucose-conversion in `kdiab-common` |

**Rationale:** these change the platform's *shape* (validation model, observability, service topology).
Per C-1, the one true rewrite (service consolidation) leads with its incremental alternative; per C-2,
all are bounded by solo-maintainer capacity, so they are deliberately last.

## Practice-conformance note (NFR-5)

Every roadmap item is expressible as a single change under `team-practices.md`: feature-branch-per-issue,
merge-commit (not squash), ≥80% coverage on new/changed code, green CI before merge. No item implies a
practice violation, and none requires a coordinated multi-item release — the phases order value, they do
not gate each other except where noted (FIND-CLIN-014 depends on FIND-CLIN-001).
