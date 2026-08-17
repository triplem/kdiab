# Clinical Safety Review

> **Theme: clinical-safety** (area code `CLIN`, severity `Critical` reserved for patient-safety).
> Findings follow the schema in [`CONVENTIONS.md`](./CONVENTIONS.md). Depth per FR-1.1/RA-Q2=A:
> concerns are *flagged with code evidence + reference pointers only* — no corrected formulas or tests
> are authored this run.
>
> **Sections:** dose calculation (U1, this Bolt) · guardrails & metric definitions (U2, appended next).

---

## 1. Dose calculation — `kdiab-calc` (U1 · the non-trimmable MVR floor, FR-1.4)

Reviewed `DoseCalculationService.calculateDose` against a standard bolus-calculator reference model
across the six required dimensions (FR-1.1): **bolus formula, IOB, ISF/correction, carb-ratio, unit
handling, rounding/guardrails**. Each dimension below carries an evidence-linked finding or an explicit
"no concern found."

### Model summary (what the code does)

```
bgMgDl        = mmol/L ? currentBg * 18.0 : currentBg
correctionDose= hypo ? 0 : max(0, (bgMgDl - targetMid)/ISF - activeIob)
carbDose      = (hypo || carbs==0) ? 0 : carbs / ICR
trendAdj      = hypo ? 0 : (±10/20/30 mg/dL by trend) / ISF
total         = min( max(0, correctionDose + carbDose + trendAdj), 30U )
```
Evidence: `kdiab-calc/src/main/kotlin/org/javafreedom/kdiab/calc/application/service/DoseCalculationService.kt#calculateDose`.

### Dimension verdicts

| Dimension | Verdict | Finding |
|---|---|---|
| Bolus formula | No concern found | correction + meal + trend − IOB is the standard structure |
| IOB | **Concern** | FIND-CLIN-001 |
| ISF / correction | No concern found | FIND-CLIN-007 (verdict) |
| Carb-ratio | No concern found | FIND-CLIN-008 (verdict) |
| Unit handling | Mostly sound; one input gap | FIND-CLIN-002 (+ FIND-CLIN-009 verdict) |
| Rounding / guardrails | **Concerns** | FIND-CLIN-003, -004, -005, -006 |

### Findings

#### FIND-CLIN-001 — IOB is caller-supplied and silently defaults to 0 (insulin-stacking risk)
- Severity: High · Effort: M · Confidence: High · Phase: Near
- Evidence: `kdiab-calc/.../domain/model/DoseCalculation.kt#DoseRequest` (`activeIob: Double = 0.0`); `kdiab-calc/.../application/service/DoseCalculationService.kt#calculateDose`
- Patient-safety impact: **High.** The service is stateless and does **not** compute insulin-on-board — it trusts `activeIob` from the caller, which defaults to `0.0`. A client that omits IOB (or computes it wrongly) gets a correction dose that ignores active insulin, so repeated corrections **stack** → delayed hypoglycemia. IOB reduces only the correction (line 66), which is clinically correct — but only if IOB is actually supplied.
- Finding: correctness of the single most safety-critical input is delegated entirely to the caller with an unsafe default.
- Recommendation: make `activeIob` a **required** request field (remove the `= 0.0` default) so an omitted IOB is a 400, not a silent zero; surface "IOB assumed 0" in `warnings` when it is genuinely zero.
- Incremental alternative: keep the field but default-reject at the API boundary and always populate it from the UI's IOB calc; the larger move (compute IOB server-side from `kdiab-treatments`) is a separate, larger change — not required to close the immediate stacking risk.

#### FIND-CLIN-002 — `glucoseUnit` is an unvalidated free string; a malformed unit mis-scales BG
- Severity: Medium · Effort: S · Confidence: High · Phase: Near
- Evidence: `kdiab-calc/.../application/service/DoseCalculationService.kt#calculateDose` (`request.glucoseUnit.equals("mmol/L", ignoreCase = true)`); `kdiab-calc/.../domain/model/DoseCalculation.kt#DoseRequest` (`glucoseUnit: String`)
- Patient-safety impact: Medium. Only the exact string `mmol/L` (case-insensitive, no trim) triggers the ×18 conversion; **any other value** (`"mmol"`, `"mmol/l "` with a trailing space, a typo, empty) falls through to a mg/dL interpretation. A mmol/L reading of e.g. 15 then reads as 15 mg/dL → treated as hypo → 0 dose. The dominant failure direction is *under*-dosing a high BG (safer than over-dosing), but it is still a silent correctness gap.
- Finding: no enum/allow-list validation on the unit; unknown units are silently coerced to mg/dL.
- Recommendation: validate `glucoseUnit` against `{mg/dL, mmol/L}` (trimmed) at the boundary and reject unknown values with a 400; consider an enum type instead of `String`.
- Incremental alternative: n/a (small, self-contained fix).

#### FIND-CLIN-003 — Maximum-dose cap is a fixed global 30 U, not personalized
- Severity: Medium · Effort: M · Confidence: High · Phase: Mid
- Evidence: `kdiab-calc/.../application/service/DoseCalculationService.kt#calculateDose` (`MAX_ABSOLUTE_DOSE = 30.0`, cap at line 71)
- Patient-safety impact: Medium. A single 30 U ceiling protects a large insulin-resistant adult but is far above a safe bolus for an insulin-sensitive adult or a child — for whom 30 U is itself dangerous, so the cap gives no real protection. Conversely it could wrongly clamp a legitimately large meal dose for a resistant user.
- Finding: the hard cap is not individualized to the user's profile.
- Recommendation: add a profile-driven `maxBolus` guardrail (per-user) and keep 30 U only as an absolute backstop.
- Incremental alternative: retain the 30 U constant as the outer backstop and layer a per-profile max-bolus check in front of it — no need to remove the existing cap.

#### FIND-CLIN-004 — Trend adjustment pre-emptively *adds* insulin to the bolus (aggressive)
- Severity: Medium · Effort: M · Confidence: Medium · Phase: Mid
- Evidence: `kdiab-calc/.../application/service/DoseCalculationService.kt#trendAdjustment`; applied in `#calculateDose` (line 68)
- Patient-safety impact: Medium. Rising CGM trends add up to `30 mg/dL / ISF` of insulin to the meal bolus. If the predicted rise does not materialize (trend reverses — common post-exercise or with variable absorption), that added insulin over-doses → hypo. Many reference bolus calculators either omit trend from the delivered dose or present it as advisory. Downward-trend subtraction (conservative) is fine.
- Finding: trend-based insulin is folded into the recommended dose rather than offered as an advisory adjustment.
- Recommendation: make the rising-trend addition opt-in/advisory, or cap its contribution (e.g. ≤ a small fixed fraction of the meal dose).
- Incremental alternative: the trend delta is already isolated in `DoseResult.trendAdjustment` and `breakdown`; surface it separately in the UI and let the user accept it, rather than silently including it in `totalRecommended`.

#### FIND-CLIN-005 — Rounding to 0.01 U does not reflect pump delivery increments
- Severity: Low · Effort: S · Confidence: High · Phase: Mid
- Evidence: `kdiab-calc/.../application/service/DoseCalculationService.kt#round2`
- Patient-safety impact: Low. Results are rounded to two decimals (0.01 U); most pumps deliver in 0.05 U or 0.1 U increments, so a recommendation like `2.37 U` is not directly deliverable. It is a recommendation (not a delivery), so impact is low, but the extra precision is misleading.
- Finding: rounding granularity is finer than any pump can deliver.
- Recommendation: round to a configurable pump increment (default 0.05 U), or state the increment in the disclaimer.
- Incremental alternative: n/a.

#### FIND-CLIN-006 — `currentBg` has no plausibility validation
- Severity: Low · Effort: S · Confidence: High · Phase: Mid
- Evidence: `kdiab-calc/.../application/service/DoseCalculationService.kt#calculateDose` (no range check on `request.currentBg`)
- Patient-safety impact: Low. Implausible inputs (negative, or e.g. 2000 mg/dL) are only backstopped by the hypo guard and the 30 U cap. No explicit rejection of physiologically impossible readings.
- Finding: missing input-range validation on the primary clinical input.
- Recommendation: reject `currentBg` outside a physiological band (e.g. 20–600 mg/dL equivalent) at the boundary.
- Incremental alternative: n/a.

#### FIND-CLIN-007 — ISF / correction math is correct (verdict, no concern)
- Severity: Low · Effort: S · Confidence: High · Phase: Near · Patient-safety impact: n/a (positive verdict)
- Evidence: `kdiab-calc/.../application/service/DoseCalculationService.kt#calculateDose` (line 64); `#lookupTargetSegment`
- Finding: correction = `(bgMgDl − targetMid)/ISF` with target = midpoint of the active target segment; ISF validated `> 0`; correction floored at 0; hypo (`< 70 mg/dL`) forces 0. Time-of-day segment selection picks the latest segment ≤ ref time. This matches the reference model.
- Recommendation: no change. (Design note: correcting to the target *midpoint* is a reasonable, documented choice; some clinicians prefer the upper bound — not a defect.)

#### FIND-CLIN-008 — Carb-ratio math is correct (verdict, no concern)
- Severity: Low · Effort: S · Confidence: High · Phase: Near · Patient-safety impact: n/a (positive verdict)
- Evidence: `kdiab-calc/.../application/service/DoseCalculationService.kt#calculateDose` (line 67); ICR validation `#lookupIcrSegment`
- Finding: carb dose = `carbsGrams / ICR`; ICR validated `> 0` when carbs are present; suppressed (with a "treat hypo first" warning) when hypoglycemic — a conservative, safe default. Correct.
- Recommendation: no change. (Minor: no upper bound on `carbsGrams`; relies on the 30 U cap — see FIND-CLIN-003.)

#### FIND-CLIN-009 — Internal unit consistency is sound (verdict, no concern)
- Severity: Low · Effort: S · Confidence: High · Phase: Near · Patient-safety impact: n/a (positive verdict)
- Evidence: `kdiab-calc/.../application/service/DoseCalculationService.kt#calculateDose` (BG→mg/dL at line 48); `kdiab-profiles/.../domain/model/Profile.kt` (`MIN_ISF_MGDL`/`MAX_ISF_MGDL`, analysis thresholds in mg/dL)
- Finding: a plausible Critical bug — BG converted to mg/dL while ISF/target stay in the user's unit — was investigated and **ruled out**. `kdiab-profiles` canonicalizes ISF (validated 10–200 mg/dL) and targets to mg/dL for **all** users regardless of display unit, so the correction math (`bgMgDl`, `ISF`, `target` all mg/dL) is unit-consistent. The display unit is a UI concern, not a storage unit.
- Recommendation: no change; keep this invariant documented so a future "store ISF in user units" change cannot silently break the calc.

---

## 2. Guardrails & metric definitions (U2 · US-2)

Two assessments (FR-1.2a treatment guardrails; FR-1.2b clinical-metric definitions in `kdiab-analyze`).

### 2a. `kdiab-treatments` safety guardrails

| Guardrail | Verdict | Finding |
|---|---|---|
| Implausible-dose limit | **Absent** | FIND-CLIN-013 |
| Correction-bolus stacking | **Absent (system-wide)** | FIND-CLIN-014 |

#### FIND-CLIN-013 — Treatment store applies no implausible-dose plausibility bound
- Severity: Medium · Effort: S · Confidence: High · Phase: Near
- Evidence: `kdiab-treatments/.../application/service/TreatmentService.kt#addTreatment`; `kdiab-treatments/.../adapters/inbound/web/TreatmentMapper.kt#toDomain`
- Patient-safety impact: Medium. `addTreatment` validates only the `treatedAt` timestamp (not >10 y past, not >1 d future); `toDomain` normalizes the `data` JSONB units but never range-checks the dose. A fat-finger `BOLUS amount=9999` is persisted unchallenged and then flows into analytics (insulin totals, TDD) and the timeline. As the system of record this "faithful capture" is partly defensible, but there is no guard to catch obvious entry errors.
- Finding: no upper-bound plausibility validation on dose/carb amounts at ingest.
- Recommendation: add a soft plausibility guard (e.g. reject or warn on bolus > a configurable ceiling such as 50 U, carbs > 500 g) at the treatments boundary, keeping an override path for genuine clinical corrections.
- Incremental alternative: start with a *warning* (a `data.warnings` marker on the stored event) rather than a hard reject, so no legitimate high-dose event is lost while entry errors become visible.

#### FIND-CLIN-014 — No correction-bolus stacking detection anywhere in the platform
- Severity: High · Effort: L · Confidence: High · Phase: Mid
- Evidence: `kdiab-treatments/.../application/service/TreatmentService.kt` (no IOB/stacking logic); cross-reference FIND-CLIN-001 (`kdiab-calc` trusts caller-supplied `activeIob`)
- Patient-safety impact: High. Neither the treatment store nor the dose calculator detects that a new correction bolus stacks on insulin still active from a recent one. `kdiab-treatments` is a pure event store (by design), and `kdiab-calc` delegates IOB to the caller (FIND-CLIN-001) — so the stacking guardrail FR-1.2a asks about does not exist at any layer.
- Finding: correction-bolus stacking is unguarded system-wide.
- Recommendation: compute IOB from recent boluses (server-side, in `kdiab-calc` or a shared service) and warn when a new correction stacks within the insulin duration-of-action window.
- Incremental alternative: as a first step, have `kdiab-calc` query recent `kdiab-treatments` boluses to *warn* on likely stacking, before building full server-side IOB — closes the visibility gap without a new subsystem. Depends on FIND-CLIN-001.

### 2b. `kdiab-analyze` clinical-metric definitions

| Metric | Verdict | Finding |
|---|---|---|
| HbA1c / GMI | **Concern** | FIND-CLIN-010 |
| TIR | No concern found | FIND-CLIN-011 |
| AGP | No concern found (doc drift) | FIND-CLIN-012 |

#### FIND-CLIN-010 — "HbA1c" estimate uses the ADAG eAG inversion, not the consensus GMI
- Severity: Medium · Effort: S · Confidence: High · Phase: Near
- Evidence: `kdiab-analyze/.../application/service/AnalyticsService.kt#getHba1c` (`(mean + DCCT_ADDEND) / DCCT_DIVISOR`, constants `DCCT_ADDEND = 46.7`, `DCCT_DIVISOR = 28.7`)
- Patient-safety impact: Medium. The estimate is `(mean_mgdl + 46.7)/28.7` — the Nathan 2008 ADAG eAG↔A1c regression (the in-code comment labels it "DCCT", a common misnomer). Current CGM consensus (Bergenstal 2018; Battelino 2019) recommends **GMI = 3.31 + 0.02392 × mean_mgdl** for CGM-derived estimates. The two diverge materially away from ~150 mg/dL: at mean 100 the ADAG value is 5.1% vs GMI 5.7% (≈0.6% low); at mean 250 it is 10.3% vs GMI 9.3% (≈1.0% high). Reporting this as "HbA1c" (rather than an estimate) can also lead a patient/clinician to conflate it with a lab A1c.
- Finding: outdated estimator formula + over-strong "HbA1c" labeling for a CGM-derived value.
- Recommendation: adopt GMI (`3.31 + 0.02392 × mean_mgdl`) and rename the field/label to "GMI (estimated A1c)"; keep the existing "<14 days unreliable" warnings.
- Incremental alternative: if backward-compatibility of the `hba1c` field matters, add GMI as a new field and mark the old one deprecated rather than changing it in place.

#### FIND-CLIN-011 — TIR zone definitions are correct (verdict, no concern)
- Severity: Low · Effort: S · Confidence: High · Phase: Near · Patient-safety impact: n/a (positive verdict)
- Evidence: `kdiab-analyze/.../application/service/AnalyticsService.kt#computeTir`; constants `TIR_VERY_LOW=54`, `TIR_LOW=70`, `TIR_HIGH=180`, `TIR_VERY_HIGH=250`
- Finding: the five bands (VL <54, L 54–<70, in-range 70–180 inclusive, high >180–250, VH >250) and their boundary handling match the International Consensus on TIR (Battelino et al. 2019). Custom profile `analysisLow/High` correctly personalizes only the in-range boundary while the clinical severity bands (<54, >250) stay fixed. Correct.
- Recommendation: no change.

#### FIND-CLIN-012 — AGP percentile computation is correct (verdict, no concern) + doc drift
- Severity: Low · Effort: S · Confidence: High · Phase: Mid · Patient-safety impact: n/a (positive verdict)
- Evidence: `kdiab-analyze/.../application/service/AnalyticsService.kt#getAgp`, `#percentile`
- Finding: p10/p25/p50/p75/p90 use standard linear interpolation between closest ranks; readings are bucketed into 288 five-minute buckets by **patient-local** time-of-day (correct for AGP). This is *more* correct than `kdiab-analyze/CLAUDE.md` documents (it claims "group by UTC hour, 24 buckets") — the doc has drifted from the implementation.
- Recommendation: no code change; update `kdiab-analyze/CLAUDE.md` to describe the 288 local-time buckets (tracked as a docs fix — see also tech-debt review).

### Cross-cutting positive note

`kdiab-analyze` also computes GRI (Klonoff 2023), PGS (Rodbard 2011), CGP (Vigersky 2018) and GVI with cited sources and per-metric data-quality warnings — evidence of genuine clinical-metric rigor beyond the FR-1.2b minimum.

---

## Section coverage (FR-1.1 / FR-1.2a / FR-1.2b)

- **FR-1.1** (dose, six dimensions): FIND-CLIN-001..009. ✓
- **FR-1.2a** (treatment guardrails): implausible-dose → FIND-CLIN-013; stacking → FIND-CLIN-014. ✓
- **FR-1.2b** (metric definitions): TIR → FIND-CLIN-011; AGP → FIND-CLIN-012; HbA1c/GMI → FIND-CLIN-010. ✓
