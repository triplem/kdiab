# Data-Model Completeness Review

> **Theme: data-model** (area code `DATA`). Findings follow [`CONVENTIONS.md`](./CONVENTIONS.md).
> Assessment per FR-1.5: each of the four schemas — `kdiab-measures`, `kdiab-treatments`,
> `kdiab-carbs`, `kdiab-profiles` — carries an explicit completeness verdict against real T1D
> pump/CGM workflows (extended/dual-wave bolus, temp basal, carb absorption, sensor calibration,
> exercise/illness context). Depth: flag gaps with schema evidence (RA-Q2=A).

## Overarching pattern

All three event/measurement stores (`measures`, `treatments`) and the carbs store share a **"typed
enum + free-form `data` JSONB"** shape (Nightscout convention). This is representationally flexible —
almost any workflow *can* be stored — but nothing inside `data` is schema-enforced. That single design
choice drives most findings below, so it is captured once as FIND-DATA-005 and cross-referenced.

## Schema verdicts (FR-1.5)

| Schema | Verdict | Finding |
|---|---|---|
| `kdiab-measures` | Complete except sensor calibration | FIND-DATA-002 |
| `kdiab-treatments` | Representable but untyped for advanced boluses/temp basal | FIND-DATA-003 |
| `kdiab-carbs` | **Incomplete** — gram-counting only | FIND-DATA-001 |
| `kdiab-profiles` | Complete for standard dosing; no context override | FIND-DATA-004 |
| cross-cutting | Untyped JSONB payloads | FIND-DATA-005 |

### Findings

#### FIND-DATA-001 — Carb entries model grams only; no absorption time, GI, or fat/protein
- Severity: Medium · Effort: M · Confidence: High · Phase: Mid · Area: data-model
- Evidence: `kdiab-carbs/.../domain/model/FoodEntry.kt#FoodEntry` (`name`, `portionGrams`, `carbsPer100g` only)
- Patient-safety impact: Medium. A `FoodEntry` captures only carbohydrate mass. Real T1D advanced bolusing needs **carb absorption time** (fast vs slow carbs) and, for high-fat/protein meals, a fat-protein estimate (FPU / Warsaw method) to drive an *extended* bolus. Without these, the platform cannot support absorption-aware dosing — a high-fat meal bolused as pure fast carbs causes a late post-prandial rise.
- Finding: no absorption-time, glycemic-index, or fat/protein fields; no meal-composition model.
- Recommendation: add optional `absorptionMinutes`, `glycemicIndex`, and `fatG`/`proteinG` fields to `FoodEntry`, feeding an extended-bolus suggestion.
- Incremental alternative: start with a single optional `absorptionMinutes` field (the highest-value one for extended bolusing) before a full FPU model.

#### FIND-DATA-002 — `measures` has no sensor-calibration record type
- Severity: Medium · Effort: M · Confidence: High · Phase: Mid · Area: data-model
- Evidence: `kdiab-measures/.../domain/model/Measure.kt#MeasureType` (CGM, BGM, BLOOD_PRESSURE, WEIGHT, PULSE, BG_CHECK, KETONE_CHECK, GLYCOSYLATED_HEMOGLOBIN)
- Patient-safety impact: Medium. There is no `CALIBRATION` measure type. xDrip+/Nightscout record calibration events (BGM entered to calibrate the CGM, with slope/intercept) as first-class `cal` records; here a calibration can only be stored as an untyped BGM/BG_CHECK, so calibration history — which materially affects CGM accuracy and therefore dose decisions — cannot be reconstructed or displayed.
- Finding: missing a calibration record type (and the slope/intercept it carries).
- Recommendation: add a `CALIBRATION` `MeasureType` with a typed `data` shape (`bgValue`, optional `slope`/`intercept`).
- Incremental alternative: n/a (small enum + mapping addition).
- Positive note: ketones (`KETONE_CHECK`) and lab A1c (`GLYCOSYLATED_HEMOGLOBIN`) *are* first-class — good DKA-risk and long-term-control coverage.

#### FIND-DATA-003 — Extended/dual-wave bolus and temp basal are representable only as untyped JSONB
- Severity: Medium · Effort: M · Confidence: Medium · Phase: Mid · Area: data-model
- Evidence: `kdiab-treatments/.../domain/model/Treatment.kt#TreatmentType` (`COMBO_BOLUS`, `TEMP_BASAL` exist); `kdiab-analyze/.../application/service/AnalyticsService.kt#computeBasalTotalIe` (reads `data["rate"]`/`data["duration"]` as an absolute rate — no percent path)
- Patient-safety impact: Medium. The enum *has* `COMBO_BOLUS` (dual-wave) and `TEMP_BASAL`, so the events can be recorded — but their parameters live in the free `data` JSONB with no typed contract. A dual-wave bolus's now/extended split and duration, and a temp basal's **absolute-vs-percent** distinction (AAPS/Nightscout use percent temp basals extensively), are convention-only. The analytics basal-total math reads only an absolute `rate`, so a percent temp basal would be mis-totalled.
- Finding: advanced bolus/basal *types* exist but their payloads are unmodelled and the consumer assumes absolute rates.
- Recommendation: define typed payloads per treatment type (e.g. `COMBO_BOLUS`: `immediateU`, `extendedU`, `durationMin`; `TEMP_BASAL`: `mode: absolute|percent`, `value`, `durationMin`) and handle percent temp basals in analytics.
- Incremental alternative: add a `mode` discriminator to temp-basal payloads and a percent branch in `computeBasalTotalIe` first (closes the analytics correctness gap) before a full typed-payload migration.

#### FIND-DATA-004 — Profiles model no temporary context override (illness / exercise sensitivity)
- Severity: Medium · Effort: M · Confidence: High · Phase: Long · Area: data-model
- Evidence: `kdiab-profiles/.../domain/model/Profile.kt#ProfileSchedule` (isf/icr/targets/basal/insulinToMealInterval); `kdiab-treatments/.../domain/model/Treatment.kt#TreatmentType` (`EXERCISE`, `ACTIVITY` are events)
- Patient-safety impact: Medium. Exercise and activity are captured as treatment *events*, but there is no way to apply a temporary sensitivity change to dosing — no profile percentage switch or temporary target (AAPS "profile switch %", pump temp targets) for illness (needs more insulin) or exercise (needs less). The event is recorded but cannot influence a subsequent dose recommendation.
- Finding: no temporary profile override / percentage switch; context events don't feed dosing.
- Recommendation: add a temporary-override concept (percentage multiplier + temp target with a time window) that `kdiab-calc` honours.
- Incremental alternative: a temporary target alone (simpler than a full profile-% switch) covers the most common exercise/illness case first.

#### FIND-DATA-005 — Shared "typed envelope + free JSONB `data`" pattern is schema-unenforced (cross-cutting)
- Severity: Medium · Effort: L · Confidence: High · Phase: Long · Area: data-model
- Evidence: `kdiab-measures/.../domain/model/Measure.kt#Measure` (`data: JsonObject`); `kdiab-treatments/.../domain/model/Treatment.kt#Treatment` (`data: JsonObject`); cross-reference FIND-CLIN-013 (no dose plausibility bound)
- Patient-safety impact: Medium. The flexibility that lets these schemas represent almost any workflow also means no `data` payload is validated: a `COMBO_BOLUS` missing its `duration`, a CGM row missing `value`, a temp basal missing `rate` are all storable and only fail (silently, via `?: return@forEach` / `?: 0.0`) at read time in analytics. For a medical-data platform, "representable" is not "reliable."
- Finding: no per-type payload schema/validation across the JSONB stores.
- Recommendation: define and validate a per-`type` payload schema at each store's boundary (e.g. a sealed payload type or JSON-schema check) so an incomplete event is rejected at write, not silently dropped at read.
- Incremental alternative: begin with validation for the dosing-relevant types (BOLUS/COMBO_BOLUS/TEMP_BASAL/CARBS and CGM) where a silent-drop has clinical impact; extend to the rest later.

## Section coverage (FR-1.5)

Each of the four schemas carries an explicit completeness verdict: measures (FIND-DATA-002),
treatments (FIND-DATA-003), carbs (FIND-DATA-001), profiles (FIND-DATA-004), plus the cross-cutting
JSONB-validation gap (FIND-DATA-005). ✓
