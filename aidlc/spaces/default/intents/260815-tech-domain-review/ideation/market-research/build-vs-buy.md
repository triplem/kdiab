# Build-vs-Buy — Dose Calculator (`kdiab-calc`)

**Intent:** review technology and domain and suggest improvements
**Focus (Q3=B):** should kdiab keep building its own dose calculator, or adopt/align with established
algorithms (AndroidAPS / Loop / OpenAPS)?
**Priority context:** this sits squarely in your #1 priority — **clinical correctness & safety** (Q2=C).

> **Confidence labelling.** `[well-established]` = widely documented T1D practice/OSS convention;
> `[assumption]` = my inference about kdiab-calc's current behavior, to be **verified against the code**
> in the Reverse-Engineering / Functional-Design stages; `[safety]` = a safety-critical flag.

## The three "buy" candidates are not the same kind of thing

1. **The bolus-calculator formula itself** `[well-established]` — carb bolus = carbs ÷ ICR; correction =
   (BG − target) ÷ ISF; total = carb + correction − IOB (insulin-on-board). This is **standard, public,
   clinically-validated practice** (the "bolus wizard" convention shared by pumps and apps). It is not a
   library to import — it is a *specification to conform to*.

2. **AAPS / Loop / OpenAPS** `[well-established]` — these are **closed-loop automated-insulin-delivery
   (AID)** engines (OpenAPS `oref0/oref1`, the Loop algorithm). They do far **more** than a bolus
   calculator: automated basal/temp-basal and micro-bolus decisions in a control loop. They are AGPL,
   safety-critical, and designed to *drive a pump*, not to give advisory recommendations.

3. **A hosted/commercial calculator API** — effectively **none** exists as a self-hostable drop-in for a
   personal tool; commercial calculators are locked inside vendor apps. So "buy" in the SaaS sense is a
   non-option consistent with Q2=A (personal self-hosted).

## Recommendation: **BUILD (keep `kdiab-calc`) + ALIGN to references — do NOT embed an AID engine**

| Option | Verdict | Why |
|---|---|---|
| Keep `kdiab-calc`, **validate** its formula/IOB model against the standard bolus-wizard spec | ✅ **Recommended** | `kdiab-calc` is a genuine differentiator (neither Nightscout nor Nocturne has one). The formula is public; the work is *correctness assurance*, not reinvention. |
| **Embed** AAPS/Loop/OpenAPS algorithm code as a dependency | ❌ Avoid | Scope explosion (AID ≠ advisory calc); `[safety]` turns kdiab into an insulin-delivery decision system with the regulatory/liability weight that implies; AGPL coupling; contradicts "personal, low-overhead" (Q2=A). |
| Use AAPS/Loop/OpenAPS **as reference documentation** for the IOB/DIA model and edge cases | ✅ Recommended (adjunct) | Their published IOB decay curves, DIA handling, and guardrails are the community's trusted reference — mine them for *test cases and validation*, not for code. |

## Concrete follow-ups this seeds for later stages

These become candidate backlog items (Q8=A) — recorded here, produced properly in Requirements/Functional
Design:

- **[safety] Verify `kdiab-calc` implements IOB** `[assumption]` — confirm insulin-on-board is subtracted
  from correction dosing; a calculator that ignores IOB can recommend insulin stacking. **Highest-severity
  correctness check.**
- **[safety] Verify dosing guardrails** — max-bolus clamp, negative-correction handling (BG below target),
  and a sane cap on CGM-trend adjustment. (Ties to Q6=B safety guardrails.)
- **Validate the formula** against the standard bolus-wizard spec and a table of reference cases derived
  from AAPS/Loop/OpenAPS documentation (`[well-established]`).
- **Confirm unit handling** — mg/dL vs mmol/L in ISF/target math (kdiab supports both; a unit bug here is
  `[safety]`).
- **Scope guard:** explicitly document that `kdiab-calc` is **advisory only**, not closed-loop — so the
  review does not accidentally pull kdiab toward AID.

## Bottom line

Building the calculator was the right call and remains so. The valuable move is not "buy" — it is to
**treat `kdiab-calc` as a safety-critical component and prove its correctness** against the established
algorithms as *references*. That is the single highest-value thread this whole review can pull, and it
maps directly to your top priority.
