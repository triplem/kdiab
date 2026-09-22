# Requirements — #1563 / FIND-CLIN-001: `activeIob` required in kdiab-calc

**Type:** Bug fix (clinical-safety defect) · **Scope:** single service (kdiab-calc) + its OpenAPI
contract and the kdiab-ui client · **Depth:** Minimal (clear request, narrow scope, well-understood
domain) · **Priority:** High (patient-safety).

## Intent Analysis

The dose calculator is stateless and does **not** compute insulin-on-board; it trusts `activeIob` from
the caller. Today that field silently defaults to `0.0` at every layer (OpenAPI schema, `DoseRequestDto`,
domain `DoseRequest`). A client that omits IOB — or computes it wrongly — receives a correction dose that
ignores active insulin, so repeated corrections **stack**, causing delayed hypoglycemia. IOB correctly
reduces only the correction dose (`DoseCalculationService.kt:66`), but that safety behaviour is only sound
if IOB is actually supplied.

**Goal:** eliminate the silent-zero failure mode so an omitted IOB is a hard, clear error rather than a
dangerous default, and make a genuine zero IOB visible in the result — without (yet) taking on server-side
IOB computation.

## Functional Requirements

- **FR-1 — IOB is a required input.** `activeIob` MUST be a required field of the dose-calculation
  request contract. It MUST NOT default to `0.0` at any layer (OpenAPI schema, `DoseRequestDto`, domain
  `DoseRequest`). (Origin: FIND-CLIN-001 primary recommendation.)
- **FR-2 — Missing or invalid IOB returns HTTP 400 with a clear clinical message.** A request that does
  not supply a valid `activeIob` MUST be rejected with `400 Bad Request` carrying a clinical message
  (e.g. *"activeIob is required to prevent insulin stacking — supply the patient's current
  insulin-on-board"*), not a generic body-parse error. Three cases MUST all be rejected identically:
  (a) the field **omitted**, (b) the field **explicitly `null`** (`{"activeIob": null}`), and
  (c) a **negative value** (`activeIob < 0`, message e.g. *"activeIob must be zero or positive"*). Rationale
  for (c): a negative IOB would *increase* the correction (`rawCorrection - (negative)`) — the exact
  stacking direction this fix exists to prevent; rejecting it (never silently clamping) keeps the input
  path honestly hardened. **Decision (Q1 = B):** enforce at two levels — mark `activeIob` required in the
  OpenAPI schema (contract + generated-client truth) and raise a `BusinessValidationException` in the
  inbound mapper (DTO field `Double? = null` so the omitted/null case routes through the mapper's clinical
  message rather than the generic `SerializationException` one). (Findings #1, #3 from the §12a review.)
- **FR-3 — Transparency warning for a genuine zero IOB.** When `activeIob == 0.0` **and a correction dose
  greater than 0 is actually recommended**, the result MUST include a warning in `DoseResult.warnings`
  making explicit that the correction does not account for any active insulin (e.g. *"No insulin-on-board
  supplied (IOB = 0) — this correction does not account for active insulin; confirm no recent bolus"*).
  The warning MUST be suppressed when no correction dose is given (BG at/below target or hypoglycemic), to
  keep warnings high-signal. **Decision (Q2 = B).** This new zero-IOB warning is **mutually exclusive by
  construction** with the existing `iobCoversFullCorrection` warning (DoseCalculationService.kt:79), which
  fires only when `activeIob > 0.0`; the new one fires only when `activeIob == 0.0`, so at most one
  IOB-related warning can appear per result (§12a finding #2). (Origin: FIND-CLIN-001 "surface 'IOB assumed
  0' in warnings", refined for signal quality.)
- **FR-4 — Client contract updated.** The kdiab-ui dose client MUST treat `activeIob` as required. The
  hand-written `kdiab-ui/src/api/calcApi.ts` `DoseRequestBody.activeIob` MUST become non-optional. The one
  caller (`DoseCalculator.tsx`) already always supplies a computed IOB, so no behavioural change to the UI
  flow is required — only the type is tightened.

## Non-Functional Requirements

- **NFR-1 — Backward compatibility (Q3 = A).** The change ships on `/api/v1` as a safety fix; **no** major
  version bump. The breaking nature (a newly-required field) is documented in the OpenAPI field
  description and the service changelog. Justification: the sole consumer (kdiab-ui) already sends
  `activeIob`, and patient safety outweighs strict backward-compat on this internal, single-consumer API.
- **NFR-2 — Safety / correctness.** No change to the existing dose math, the hypoglycemia guard
  (`< 70 mg/dL` → no correction), the high-dose warning (`> 20 U`), or the absolute cap (`30 U`). The fix
  only hardens the IOB input path; it MUST NOT alter any computed dose for a request that already supplies
  `activeIob`.
- **NFR-3 — Test coverage.** New/changed code MUST stay at or above the 80% line-coverage floor enforced
  by Kover (`./gradlew check`). Tests MUST cover: omitted IOB → 400 with the clinical message; explicit
  `null` IOB → 400 with the same clinical message; negative IOB → 400; explicit IOB = 0 with a correction →
  zero-IOB warning present; explicit IOB = 0 with no correction (at-target/hypo) → zero-IOB warning absent;
  positive IOB → unchanged dose math (regression) and the existing `iobCoversFullCorrection` warning still
  behaves as before.
- **NFR-4 — Observability.** The 400 rejection path reuses the existing StatusPages logging; no new PII is
  logged (IOB value is a clinical measure, already handled within request scope — do not log it).

## Constraints

- **C-1** Hexagonal architecture and existing conventions are preserved: inbound mapping in
  `adapters/inbound/web` (`CalcMapper`/`CalcRoutes`), domain model unchanged in shape apart from the
  removed default, domain exceptions (`BusinessValidationException`) → StatusPages for HTTP mapping.
- **C-2** API-first: `kdiab-calc/api/openapi.yaml` is the source of truth and MUST be edited first;
  backend stubs and the TS client are regenerated from it.
- **C-3** kotlinx.serialization semantics: a DTO property with no default is required at deserialization;
  a nullable property with `= null` default is optional. FR-2's mapper-guard approach uses the latter so
  the *clinical* message (not the generic serialization message) is returned for the omitted case.
- **C-4** Team practices: merge-commit (never squash), Conventional Commits referencing #1563, full
  quality gate (`./gradlew check` + `npm run build`/lint/test) green before PR, all CI green before merge.

## Assumptions

- **A-1** kdiab-ui is the only consumer of the dose endpoint (verified: no other backend references it;
  the nightscout compat layer does not call it).
- **A-2** `DoseCalculator.tsx` already computes and always sends `activeIob` (line 133) — verified — so the
  required-field tightening does not regress the UI at runtime.
- **A-3** Server-side IOB computation from kdiab-treatments is deliberately **not** part of this fix.

## Out of Scope

- Server-side / authoritative IOB computation (a separate, larger change flagged by FIND-CLIN-001).
- Any change to the IOB decay model in the UI (`calcIOB` in `basalUtils.ts`).
- Changes to other kdiab-calc inputs, the dose math, or other services.
- API v2 / deprecation-window machinery (rejected in Q3).

## Open Questions

- None blocking. Exact user-facing warning/error wording and i18n keys for the UI-side surfacing are a
  code-generation detail; the semantic requirements are fixed above.

## Traceability & Inputs

- **Authoritative origin:** GitHub issue **#1563** (FIND-CLIN-001), itself synthesized from
  `docs/review/clinical-safety.md` (review deliverable v1.1.0, epic #1562). Every FR/NFR above traces to
  this finding.
- **Upstream ideation artifacts** (`intent-statement`, `scope-document`) and **`team-practices`** are
  **N/A** for this run — the `bugfix` scope skips Intent Capture, Scope Definition, and Practices
  Discovery. The finding text is the requirement source; team practices are read directly from
  `aidlc/spaces/default/memory/{org,team,project}.md`.
- **Brownfield codekb consulted:** `business-overview.md` (kdiab-calc = stateless dose calculator),
  `architecture.md` (hexagonal, JWT-forwarding BFF pattern), `code-structure.md` (adapters/application/
  domain layering, convention plugins) — all under `aidlc/spaces/default/codekb/kdiab-bkp/`.
- **Live source verified:** `DoseCalculation.kt`, `DoseCalculationService.kt`, `CalcMapper.kt`,
  `CalcRoutes.kt`, `kdiab-calc/api/openapi.yaml`, `kdiab-common` `StatusPages.kt`, and kdiab-ui
  `DoseCalculator.tsx` / `calcApi.ts`.
