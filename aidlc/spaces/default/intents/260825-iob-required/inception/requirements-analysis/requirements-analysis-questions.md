# Requirements Analysis — Clarifying Questions

Intent: **#1563 / FIND-CLIN-001** — make `activeIob` a required input in kdiab-calc to prevent
silent insulin stacking. Source: GitHub issue #1563, `docs/review/clinical-safety.md` (deliverable v1.1.0).

Grounding facts established during analysis (not questions — context for your answers):

- `DoseRequest.activeIob: Double = 0.0` (DoseCalculation.kt:10) and the DTO field (CalcMapper.kt) both
  default to `0.0`; the OpenAPI schema (`kdiab-calc/api/openapi.yaml`) has `activeIob` with `default: 0.0`
  and it is **not** in the `required` list.
- IOB reduces only the correction dose (DoseCalculationService.kt:66,
  `maxOf(0.0, rawCorrection - activeIob)`) — clinically correct, but only if IOB is actually supplied.
- StatusPages already maps `SerializationException` → 400 ("Invalid request body") and
  `BusinessValidationException` → 400 (with the exception's own message).
- The **only** consumer is `kdiab-ui`; its `DoseCalculator.tsx` **already always sends** `activeIob`
  (line 133, computed from recent treatments). The hand-written client `calcApi.ts` types it as optional
  (`activeIob?: number`). No backend service calls the dose endpoint.
- Server-side IOB computation from kdiab-treatments is **out of scope** per the finding (a larger,
  separate change).

---

## Q1 — How should "IOB is required → omitted returns 400" be enforced?

A. Schema-required only. Add `activeIob` to the OpenAPI `required` list, remove `default: 0.0`, make the
   DTO field non-nullable with no default. An omitted field then throws kotlinx `MissingFieldException`
   → existing `SerializationException` handler → 400 "Invalid request body" (generic message).
B. Contract-required + clear clinical message. Mark `activeIob` required in the OpenAPI schema (contract
   + generated client truth) AND make the DTO field nullable-without-default so the mapper throws
   `BusinessValidationException("activeIob is required to prevent insulin stacking — supply the patient's
   current insulin-on-board")` → 400 with a clinically clear message.
C. Service-layer guard only. Leave the schema optional; add a guard in the service/mapper that rejects a
   null/absent IOB. Clear message, but the contract still advertises the field as optional.
X. Other (please specify)

[Answer]: B

## Q2 — When `activeIob` is explicitly `0.0`, should the result carry a transparency warning?

A. Always warn when `activeIob == 0.0` — add a warning to `DoseResult.warnings` unconditionally
   (matches the finding's literal wording "surface 'IOB assumed 0'").
B. Warn only when a correction dose > 0 is actually recommended — i.e. only when unaccounted IOB could
   have caused stacking; suppress the warning when BG is at/below target or hypo (no correction given),
   to keep warnings high-signal.
C. No warning — a required, explicit `0.0` is a deliberate declaration of "no insulin on board".
X. Other (please specify)

[Answer]: B

## Q3 — This adds a required request field, which is a breaking API change (per api-design.md). How to handle compatibility?

A. Apply on `/api/v1` as a safety fix; document the change in the OpenAPI field description + a changelog
   note; no version bump. Justified: the sole consumer (kdiab-ui) already sends `activeIob`, and patient
   safety outweighs strict backward-compat on an internal single-consumer API.
B. Bump the dose endpoint to `/api/v2`, keep `/api/v1` with the old lenient behavior (deprecated).
C. Apply on `/api/v1` but only enforce the 400 after a deprecation grace window.
X. Other (please specify)

[Answer]: A
