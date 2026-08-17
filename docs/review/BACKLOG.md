# Prioritized Improvement Backlog

> **Unit U7.** The single master list — every finding from every theme, ordered by value-density with
> **clinical safety strictly first** (NFR-3, FR-1.4). Ordering key: `(safetyRank, valueDensity desc,
> effort asc)` where `safetyRank` forces clinical-safety Critical/High ahead of all else. Findings and
> their full evidence live in the theme docs ([clinical-safety](./clinical-safety.md),
> [data-model](./data-model.md), [security](./security.md), [tech-debt](./tech-debt.md),
> [modernization](./modernization.md)); this list references them, it does not duplicate them.
>
> Schema, severity/effort/phase scales, and the evidence rules are in [CONVENTIONS.md](./CONVENTIONS.md).
> **No Critical findings were raised** — no confirmed patient-harm-certain dose bug was found (an
> intentionally reassuring result; the review earns trust by ruling concerns out with evidence as well
> as raising them).

## Ordered backlog (31 actionable findings)

| # | ID | Area | Sev | Eff | Phase | Summary |
|---|---|---|---|---|---|---|
| 1 | FIND-CLIN-001 | clinical-safety | High | M | Near | IOB caller-supplied, defaults to 0 → insulin-stacking risk |
| 2 | FIND-CLIN-014 | clinical-safety | High | L | Mid | No correction-bolus stacking detection anywhere (system-wide) |
| 3 | FIND-CLIN-002 | clinical-safety | Med | S | Near | `glucoseUnit` unvalidated → malformed unit mis-scales BG |
| 4 | FIND-CLIN-013 | clinical-safety | Med | S | Near | Treatment store has no implausible-dose plausibility bound |
| 5 | FIND-CLIN-010 | clinical-safety | Med | S | Near | "HbA1c" uses ADAG inversion, not consensus GMI |
| 6 | FIND-CLIN-003 | clinical-safety | Med | M | Mid | Max-dose cap is a fixed global 30 U, not personalized |
| 7 | FIND-CLIN-004 | clinical-safety | Med | M | Mid | Trend adjustment pre-emptively adds insulin (aggressive) |
| 8 | FIND-CLIN-006 | clinical-safety | Low | S | Mid | `currentBg` has no plausibility validation |
| 9 | FIND-CLIN-005 | clinical-safety | Low | S | Mid | Dose rounded to 0.01 U (finer than pump increments) |
| 10 | FIND-SEC-001 | security | High | S | Near | Test-mode HMAC JWT toggle has no production guard |
| 11 | FIND-DEBT-005 | tech-debt | High | M | Near | UI generates typed clients for only 4 of 8 backends (calc, users hand-written) |
| 12 | FIND-SEC-004 | security | High | L | Mid | Dose calculator likely SaMD under EU MDR (flag classification) |
| 13 | FIND-DATA-001 | data-model | Med | M | Mid | Carb entries model grams only — no absorption/GI/fat-protein |
| 14 | FIND-DATA-003 | data-model | Med | M | Mid | Extended/dual-wave bolus + temp basal are untyped JSONB |
| 15 | FIND-DATA-002 | data-model | Med | M | Mid | No sensor-calibration measure type |
| 16 | FIND-SEC-002 | security | Med | M | Mid | Doctor→patient access is JWT-embedded; revocation lags by token lifetime |
| 17 | FIND-SEC-005 | security | Med | M | Mid | GDPR erasure (Art 17) vs MDR 7-yr no-purge retention unreconciled |
| 18 | FIND-SEC-006 | security | Med | M | Mid | GDPR Art-9 safeguards to verify (encryption-at-rest, lawful basis, IP-log retention) |
| 19 | FIND-DEBT-008 | tech-debt | Med | S | Mid | `kdiab-analyze` suppresses all compiler warnings |
| 20 | FIND-DEBT-007 | tech-debt | Med | M | Mid | v3 HISTORY stubbed + stale `#894-#898` reference (untracked) |
| 21 | FIND-DEBT-001 | tech-debt | Med | M | Mid | `kdiab-nightscout` ships 0 e2e tests (interop-critical) |
| 22 | FIND-DEBT-004 | tech-debt | Med | M | Mid | nightscout Detekt baseline: 19 UnreachableCode false positives |
| 23 | FIND-DEBT-003 | tech-debt | Med | M | Mid | Coverage-exclusion breadth overstates real coverage (users/nightscout) |
| 24 | FIND-DEBT-009 | tech-debt | Med | M | Mid | No performance/load-testing tier across the services (calc dose, analyze BFF) |
| 25 | FIND-MOD-004 | modernization | Med | M | Long | Observability is trace-centric — no metrics/alerting/log-aggregation |
| 26 | FIND-DATA-004 | data-model | Med | M | Long | Profiles model no temporary illness/exercise override |
| 27 | FIND-DATA-005 | data-model | Med | L | Long | JSONB payloads schema-unenforced across stores |
| 28 | FIND-MOD-002 | modernization | Med | L | Long | Nine services over-decomposed for solo/self-hosted (rewrite + incremental alt) |
| 29 | FIND-SEC-007 | security | Low | S | Mid | CSP hardening (add frame-ancestors/base-uri/form-action) |
| 30 | FIND-MOD-003 | modernization | Low | S | Mid | No unified platform version; module versions drift |
| 31 | FIND-DEBT-006 | tech-debt | Low | M | Long | Cross-service duplication incl. glucose-conversion constant in 3 services |

## Positive verdicts (no action — recorded for trust)

These were investigated and found sound; they are **not** debt:

- **FIND-CLIN-007/008/009** — ISF/correction, carb-ratio, and internal unit-consistency math are correct
  (a plausible Critical unit-mismatch was investigated and disproven).
- **FIND-CLIN-011/012** — TIR bands (Battelino 2019) and AGP percentile math are correct.
- **FIND-SEC-003** — the ABAC `canAccess` / JWKS / audience auth core is clean and correct.
- **FIND-DEBT-002** — the UI coverage "gap" is RESOLVED (#1082 closed; `lines:72` is the intentional
  ADR-015 floor) and the 80% Kover floor is genuinely enforced.
- **FIND-MOD-001/005** — stack currency and CI/CD maturity are excellent.

## Ordering rationale

1. **Clinical-safety first (rows 1–9)** — NFR-3 / FR-1.4. Within the band, High before Medium before Low,
   then effort ascending. FIND-CLIN-001 (IOB) leads: highest safety value at small-to-medium effort.
2. **Then cross-theme High severity (rows 10–12)** by value-density: SEC-001 (small-effort auth fix) >
   DEBT-005 (spec drift on calc/users) > SEC-004 (large-effort regulatory flag).
3. **Then Medium (rows 13–28)** grouped by value-density: domain data-model gaps and the security/GDPR
   flags (incl. FIND-SEC-002 doctor-access revocation latency) ahead of internal tech-debt (incl.
   FIND-DEBT-009 missing performance tier) and architecture, effort ascending within ties.
4. **Then Low (rows 29–31).**

Effort is shown per item but never overrides value-density — a cheap Low item never jumps a costly
safety item.

## Materialization

- **Docs** (this file + theme docs) are the value-bearing deliverable and ship now (FR-D.1).
- **GitHub issues** are a deferred projection — one epic + one native sub-issue per row, labelled
  `area:*`+`severity:*`, reuse-first, already-tracked items cross-referenced not re-filed (ADR-RVW-005).
  Execution is parked at end of Inception (RA-Q3=A) and `gh`-gated (A-2). The ready-to-open queued issue
  set is appended by unit U10 (see the "Queued GitHub issues (deferred)" section below once generated).
- The near-term subset is in [QUICK-WINS.md](./QUICK-WINS.md); the phased sequence is in
  [ROADMAP.md](./ROADMAP.md).

---

## Queued GitHub issues (deferred — unit U10)

> **Not executed this run.** Per ADR-RVW-005 and RA-Q3=A the workflow parks at end of Inception and issue
> creation is `gh`-gated (A-2, OQ-1). This section is the **ready-to-open queued issue set** (the
> `fallbackQueue` projection) — the exact issues to file when the maintainer un-parks. Nothing was written
> to GitHub. Follows the repo's `github-issue-management.md`: one epic + native sub-issues (`addSubIssue`),
> **no assignee at creation**, reuse-first labels, dedup against already-tracked items.

### Epic

- **Title:** `Tech & Domain Review — prioritized improvement backlog`
- **Body:** links to this backlog + the five theme docs; lists the 31 sub-issues grouped by area.
- **Labels:** `review`, `epic`.

### Labels to reconcile (reuse-first, create-missing)

`area:clinical-safety`, `area:data-model`, `area:security`, `area:tech-debt`, `area:modernization`;
`severity:high`, `severity:medium`, `severity:low`; `quick-win`; `review`. Reuse any that already exist;
create the rest. (In practice the `area:*` labels will not pre-exist and will be created.)

### Sub-issues (one per actionable backlog row — 31)

Each sub-issue: title = the finding summary; body = the theme-doc finding block (evidence link,
recommendation, incremental alternative); labels = `area:<area>` + `severity:<sev>` (+ `quick-win` for the
five top quick-wins); linked to the epic via `addSubIssue`. Mapping:

| Backlog # | Sub-issue title (from finding) | Labels |
|---|---|---|
| 1 | FIND-CLIN-001 IOB caller-supplied default 0 (stacking) | `area:clinical-safety` `severity:high` |
| 2 | FIND-CLIN-014 no correction-bolus stacking detection | `area:clinical-safety` `severity:high` |
| 3 | FIND-CLIN-002 glucoseUnit unvalidated | `area:clinical-safety` `severity:medium` `quick-win` |
| 4 | FIND-CLIN-013 no implausible-dose bound | `area:clinical-safety` `severity:medium` `quick-win` |
| 5 | FIND-CLIN-010 HbA1c→GMI | `area:clinical-safety` `severity:medium` `quick-win` |
| 6 | FIND-CLIN-003 global 30U cap not personalized | `area:clinical-safety` `severity:medium` |
| 7 | FIND-CLIN-004 trend adds insulin (aggressive) | `area:clinical-safety` `severity:medium` |
| 8 | FIND-CLIN-006 currentBg no validation | `area:clinical-safety` `severity:low` |
| 9 | FIND-CLIN-005 rounding 0.01U | `area:clinical-safety` `severity:low` |
| 10 | FIND-SEC-001 test-JWT no prod guard | `area:security` `severity:high` `quick-win` |
| 11 | FIND-DEBT-005 api:generate 4 of 8 | `area:tech-debt` `severity:high` |
| 12 | FIND-SEC-004 MDR/SaMD flag | `area:security` `severity:high` |
| 13 | FIND-DATA-001 carbs no absorption | `area:data-model` `severity:medium` |
| 14 | FIND-DATA-003 extended/temp basal untyped | `area:data-model` `severity:medium` |
| 15 | FIND-DATA-002 no calibration type | `area:data-model` `severity:medium` |
| 16 | FIND-SEC-002 doctor-access revocation lag (short TTL) | `area:security` `severity:medium` |
| 17 | FIND-SEC-005 erasure vs retention | `area:security` `severity:medium` |
| 18 | FIND-SEC-006 GDPR Art-9 safeguards | `area:security` `severity:medium` |
| 19 | FIND-DEBT-008 analyze suppressWarnings | `area:tech-debt` `severity:medium` `quick-win` |
| 20 | FIND-DEBT-007 v3 HISTORY stub + stale ref | `area:tech-debt` `severity:medium` |
| 21 | FIND-DEBT-001 nightscout 0 e2e | `area:tech-debt` `severity:medium` |
| 22 | FIND-DEBT-004 nightscout Detekt baseline | `area:tech-debt` `severity:medium` |
| 23 | FIND-DEBT-003 coverage-exclusion breadth | `area:tech-debt` `severity:medium` |
| 24 | FIND-DEBT-009 no performance/load-testing tier | `area:tech-debt` `severity:medium` |
| 25 | FIND-MOD-004 observability trace-centric | `area:modernization` `severity:medium` |
| 26 | FIND-DATA-004 no context override | `area:data-model` `severity:medium` |
| 27 | FIND-DATA-005 JSONB unvalidated | `area:data-model` `severity:medium` |
| 28 | FIND-MOD-002 nine-service consolidation | `area:modernization` `severity:medium` |
| 29 | FIND-SEC-007 CSP hardening | `area:security` `severity:low` |
| 30 | FIND-MOD-003 unified platform version | `area:modernization` `severity:low` |
| 31 | FIND-DEBT-006 duplication / glucose constant | `area:tech-debt` `severity:low` |

### Dedup / cross-reference (FR-D.5 — do NOT re-file)

- **AR-001** (libxml2 in the UI nginx image) — already tracked in `docs/security/accepted-risks.md` as an
  accepted risk. **Cross-reference, do not file a new issue.**
- **#1082** (UI coverage) — **CLOSED** (resolved; `lines:72` is the ADR-015 floor). No issue — it is a
  positive verdict (FIND-DEBT-002), not backlog debt.
- **#894–#898** (v3 collections) — **CLOSED**. The v3 HISTORY debt (FIND-DEBT-007) gets a **new** issue;
  it must **not** be attached to these closed CRUD issues.

**Gating:** file only after the end-of-Inception continue decision (OQ-1) and only if `gh` is
available/authorized. Assign no one at creation; add assignees + the `In Progress` label when work starts.
