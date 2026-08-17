# Requirements — Technology & Domain Review

**Intent:** review technology and domain and suggest improvements
**Type:** Assessment → prioritized recommendations (implementation deferred; park at end of Inception)
**Scope:** enterprise (comprehensive depth) · **Project:** kdiab (brownfield, 9-service Kotlin/Ktor + React 19 monorepo)
**Upstream inputs:** `intent-statement.md`, `scope-document.md`, `team-practices.md`, and the RE codekb (`business-overview.md`, `architecture.md`, `code-structure.md`, `code-quality-assessment.md`).

---

## Intent Analysis

Per `intent-statement.md`, the goal is to give the **solo maintainer** an evidence-based,
prioritized "where do I invest next" view across two dimensions — **technology health** and
**T1D domain correctness** — delivered as an actionable improvement plan. This is advisory work,
not a shipped feature: the value-bearing outputs are a prioritized backlog, a quick-wins list, and
a phased roadmap, every finding tied to concrete evidence. `scope-document.md` ranks the work by
value-density under occasional-burst capacity, with clinical safety non-negotiable.

The deliverables must serve one person's investment decision — not a committee — and must be
sequenced so a single maintainer can chip away incrementally. Because output mode is
recommendations-only, the confirmed enterprise scope's **Construction and Operation stages carry no
build mandate** for this intent; Inception is the natural finish/park point (confirmed: Q3 = A).

## Functional Requirements

Requirements are organized by the four review themes (in priority order) plus the deliverable set.
Each is verifiable against a concrete pass/fail criterion.

### Theme 1 — Clinical Correctness, Safety & Data-Model Completeness (Priority 1, non-negotiable)

Covers the three confirmed domain dimensions from the intent: clinical correctness (intent Q6=A),
safety guardrails (intent Q6=B), and data-model completeness (intent Q6=C).

- **FR-1.1** The review MUST assess `kdiab-calc` dose-calculation correctness: bolus formula vs. a
  reference model, insulin-on-board (IOB), correction/ISF and carb-ratio handling, unit correctness
  (mg/dL vs mmol/L), and rounding/guardrail behaviour. *Depth: flag concerns with code evidence +
  reference pointers only (RA-Q2 = A) — no corrected formulas/tests authored in this run.*
  *Pass/fail: each of {bolus formula, IOB, ISF/correction, carb-ratio, unit handling, rounding} has an
  evidence-linked finding or an explicit "no concern found" with a code reference.* (intent Q6=A)
- **FR-1.2a** The review MUST assess safety guardrails in `kdiab-treatments` (e.g. implausible-dose
  limits, correction-bolus stacking). *Pass/fail: each of {implausible-dose limit, correction-bolus
  stacking} has an evidence-linked finding or an explicit "no concern found" with a citation.* (intent Q6=B)
- **FR-1.2b** The review MUST assess the definitional correctness of TIR / AGP / HbA1c (GMI) in
  `kdiab-analyze` against standard clinical definitions. *Pass/fail: each of {TIR, AGP, HbA1c/GMI}
  definitions carries an evidence-linked verdict with a code reference.* (intent Q6=A)
- **FR-1.5** The review MUST assess **data-model completeness** of `kdiab-measures`,
  `kdiab-treatments`, `kdiab-carbs`, and `kdiab-profiles` against real T1D workflows — whether the
  schemas capture what real pump/CGM use needs (e.g. extended/dual-wave boluses, temp basals, carb
  absorption time, sensor calibration, exercise/illness context). *Pass/fail: each assessed data model
  carries an explicit completeness verdict citing the schema/entity.* (intent Q6=C)
- **FR-1.3** Every clinical/domain finding MUST be evidence-linked to a specific file/function/schema
  and note the patient-safety impact. *Pass/fail: no finding without a code/schema reference.*
- **FR-1.4** If capacity forces a cut, a verified/safe `kdiab-calc` is the single non-trimmable
  deliverable (per `scope-document.md` "Minimum Viable Review").

### Theme 2 — Security & Compliance (Priority 2)

- **FR-2.1** The review MUST assess GDPR special-category (health data / PII) handling, auth
  hardening (Keycloak OIDC / passkeys, JWT posture, ABAC `canAccess`), and MDR/SaMD posture.
- **FR-2.2** The review MUST *flag* regulatory obligations (MDR, GDPR) without executing certification.

### Theme 3 — Tech Debt / Code Health (Priority 3)

- **FR-3.1** The review MUST assess the test pyramid (unit/integration/e2e balance and the real Kover
  coverage picture vs. the 80% floor, including the UI coverage gap and nightscout's 0 e2e tests).
- **FR-3.2** The review MUST quantify Detekt baseline debt per module (notably the nightscout
  `UnreachableCode` cluster) and identify cross-service code duplication.

### Theme 4 — Modernization / Architecture (Priority 4)

- **FR-4.1** The review MUST assess stack currency and deprecations, the nine-service boundary
  tension, CI/CD & release health, and observability/operability, using the RE `architecture.md`,
  `code-structure.md`, and `code-quality-assessment.md` as evidence.

### Deliverables (cross-cutting)

- **FR-D.1 — Prioritized backlog:** every review area MUST be represented; each item MUST be
  actionable, labelled by **area + severity**, and **evidence-linked**. Materialized as **BOTH**
  markdown documents committed to the repo under **`docs/review/`** (path pinned, resolving OQ-3)
  **AND** actual GitHub issues opened in this run (RA-Q1 = C). *Pass/fail: a `docs/review/` backlog doc
  exists AND a corresponding GitHub issue exists per backlog item.* If the `gh` CLI is unavailable or
  unauthorized (see A-2), the markdown deliverables still ship and the issues are queued as an explicit
  follow-up list — a tooling gap MUST NOT block the value-bearing docs.
- **FR-D.5 — No duplicate issues:** findings the codekb already tracks (e.g. UI coverage → issue
  #1082 / ADR-015, Nightscout v3 HISTORY → TODO #894–#898) MUST be **cross-referenced** to the
  existing issue/ADR, never re-filed as new issues (project rule: reuse issues, don't duplicate).
- **FR-D.2 — Quick-wins list:** an explicit, short list the maintainer can act on immediately.
- **FR-D.3 — Phased roadmap:** near / mid / long-term phases, each with a rationale and rough effort,
  sequenced so each item is independently shippable in a burst.
- **FR-D.4 — Evidence discipline:** every finding across all deliverables MUST cite concrete evidence
  (a Detekt finding, a coverage number, a duplication cluster, a specific code path) — never opinion.

## Non-Functional Requirements

- **NFR-1 (Evidence):** 100% of findings are evidence-linked; a finding without a citation is a defect.
- **NFR-2 (Actionability):** each backlog item is independently shippable within a single maintainer
  burst; no item requires a coordinated multi-item release.
- **NFR-3 (Prioritization):** the backlog is ordered by value-density (clinical/safety + risk
  reduction per hour), with clinical safety strictly first.
- **NFR-4 (Audience fit):** deliverables are readable by a single non-committee maintainer; no formal
  sign-off ceremony assumed.
- **NFR-5 (Practice conformance):** recommendations, when later implemented, MUST be expressible under
  `team-practices.md` — feature branch per issue, merge-commit (not squash), ≥80% coverage, green CI
  before merge. The roadmap notes this so no recommendation implies a practice violation.

## Constraints

- **C-1:** No hard technology constraints on *proposals* (intent Q7 = E, "none") — rewrites are
  permitted where justified, always paired with an incremental alternative.
- **C-2:** Pragmatism is bounded by solo-maintainer capacity and the self-hosted preference; these
  inform recommendations but are not fences on what may be proposed.
- **C-3:** Enterprise scope is confirmed, but Construction/Operation have no build mandate this run;
  the workflow parks at end of Inception (RA-Q3 = A park; intent Q9 = A recommendations-only).

## Assumptions

- **A-1:** The RE codekb (commit `d6c8866b`) is current and authoritative evidence; no re-scan needed.
- **A-2:** The GitHub `gh` CLI is available and authorized for FR-D.1 issue creation. If it is not,
  FR-D.1's fallback applies (docs ship; issues queued as a follow-up list) — the assumption failing
  degrades the deliverable, it does not block it.
- **A-3:** "Flag + references" clinical depth (RA-Q2 = A) is sufficient; corrected formulas, worked
  examples, and test authoring are deferred to a later implementation decision.
- **A-4:** Existing accepted-risk documentation (`docs/security/accepted-risks.md`, ADRs) is treated as
  prior art the review builds on rather than re-derives.

## Out of Scope

- Implementation / building of any recommendation (deferred, Q9 = A).
- Performance & scalability tuning — **Q2 = E (rank 5), explicitly deprioritized** (per
  `intent-statement.md` "Goal priority", lines 66 & 96).
- Interoperability breadth — Dexcom/Glooko/LibreLinkUp connectors, deep Nightscout/AAPS parity (Q6).
- Terminology/standards alignment (HL7/FHIR) (Q6, not selected).
- Multi-tenant / cloud-native / Kubernetes posture (contradicts self-hosted).
- Formal certification execution (MDR submission, GDPR DPA) — obligations are *flagged*, not executed.

## Open Questions

- **OQ-1:** At end of Inception, re-confirm park vs. continue into Construction (current decision:
  park, Q3 = A) once recommendations are visible.
- **OQ-2:** Confirm performance/scalability and interoperability/standards can remain out of scope as
  findings surface (revisit if a Theme-1/2 finding makes one of them safety-relevant).
- **OQ-3:** *Resolved* — the durable repo path for the backlog docs is pinned to `docs/review/` (see
  FR-D.1), making FR-D.1 immediately testable.
